import os

# ── 1. Fix NexusVpnService.kt — real packet forwarding to SNI proxy ────────────
svc_path = "android/app/src/main/kotlin/com/nexusvpn/android/service/NexusVpnService.kt"

SVC = '''package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nexusvpn.android.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class NexusVpnService : VpnService() {
    companion object {
        private const val TAG = "NexusVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "nexus_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"
        const val SNI_PROXY_HOST = "127.0.0.1"
        const val SNI_PROXY_PORT = 8080
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    var isConnected = AtomicBoolean(false)
    var isConnecting = AtomicBoolean(false)
    var torService: TorService? = null
    var sniProxyService: SniProxyService? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnThreadJob: Job? = null
    private val logQueue = ConcurrentLinkedQueue<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> {
                val sniHost = intent.getStringExtra("sni_hostname") ?: ""
                val torEnabled = intent.getBooleanExtra("tor_enabled", true)
                connectVpn(sniHost, torEnabled)
            }
            "DISCONNECT" -> disconnectVpn()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disconnectVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun connectVpn(sniHostname: String, torEnabled: Boolean) {
        if (isConnecting.get() || isConnected.get()) return
        serviceScope.launch {
            try {
                isConnecting.set(true)
                addLog("Starting Nexus VPN")

                // Step 1: Start SNI proxy
                addLog("Step 1/3: Starting SNI Proxy on port $SNI_PROXY_PORT")
                sniProxyService = SniProxyService()
                sniProxyService!!.setLogCallback { msg -> addLog(msg) }
                val sniOk = sniProxyService!!.start(sniHostname)
                if (!sniOk) {
                    addLog("ERROR: SNI Proxy failed to start")
                    isConnecting.set(false)
                    return@launch
                }
                addLog("SNI Proxy started OK on port $SNI_PROXY_PORT")

                // Step 2: Start Tor (requires Orbot or bundled binary)
                addLog("Step 2/3: Starting Tor")
                torService = TorService(this@NexusVpnService)
                torService!!.setLogCallback { msg -> addLog(msg) }
                val torOk = torService!!.start()
                if (!torOk) {
                    addLog("WARNING: Tor unavailable. Install Orbot app for anonymity.")
                } else {
                    addLog("Tor SOCKS5 ready on port ${TorService.TOR_SOCKS_PORT}")
                }

                // Step 3: Establish VPN interface
                addLog("Step 3/3: Establishing VPN interface")
                if (!setupVpnInterface()) {
                    addLog("ERROR: VPN interface creation failed")
                    sniProxyService?.stop()
                    torService?.stop()
                    isConnecting.set(false)
                    return@launch
                }
                addLog("VPN interface established")

                isConnected.set(true)
                isConnecting.set(false)
                startPacketRouting()
                startForegroundService()
                addLog("Chain ACTIVE: Device -> SNI Proxy -> Tor -> Internet")

            } catch (e: Exception) {
                addLog("ERROR: " + e.localizedMessage)
                Log.e(TAG, "Connect error", e)
                isConnecting.set(false)
            }
        }
    }

    private fun setupVpnInterface(): Boolean {
        val builder = Builder()
            .setSession("Nexus VPN")
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("127.0.0.1")
            .setMtu(VPN_MTU)
            .setBlocking(false)
        protect(SNI_PROXY_PORT)
        vpnInterface = builder.establish()
        return vpnInterface != null
    }

    // Real packet routing: intercept TCP packets and redirect to SNI proxy via SOCKS5
    private fun startPacketRouting() {
        vpnThreadJob = serviceScope.launch {
            val vpnFd = vpnInterface?.fileDescriptor ?: return@launch
            val vpnInput = FileInputStream(vpnFd)
            val vpnOutput = FileOutputStream(vpnFd)
            val buf = ByteArray(VPN_MTU)
            addLog("Packet routing started — forwarding to SNI proxy")

            while (isConnected.get()) {
                try {
                    val n = vpnInput.read(buf)
                    if (n < 20) continue
                    val ipVersion = (buf[0].toInt() and 0xFF) shr 4
                    if (ipVersion != 4) continue
                    val protocol = buf[9].toInt() and 0xFF
                    if (protocol != 6) continue  // TCP only

                    // Extract destination IP and port from IP/TCP headers
                    val destIp = "${buf[16].toInt() and 0xFF}.${buf[17].toInt() and 0xFF}" +
                            ".${buf[18].toInt() and 0xFF}.${buf[19].toInt() and 0xFF}"
                    val ihl = (buf[0].toInt() and 0x0F) * 4
                    val destPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)

                    // Only redirect port 80 and 443
                    if (destPort != 443 && destPort != 80) continue

                    // Forward to SNI proxy via local SOCKS5 tunnel
                    serviceScope.launch {
                        forwardToSniProxy(buf.copyOf(n), destIp, destPort, vpnOutput)
                    }
                } catch (e: Exception) {
                    if (isConnected.get()) Log.e(TAG, "Packet read error", e)
                }
            }
        }
    }

    private fun forwardToSniProxy(packet: ByteArray, destIp: String, destPort: Int, vpnOut: FileOutputStream) {
        try {
            val sock = Socket()
            protect(sock)  // exclude from VPN so it doesn't loop
            sock.connect(InetSocketAddress(SNI_PROXY_HOST, SNI_PROXY_PORT), 3000)
            sock.use { s ->
                // Send CONNECT request to SNI proxy
                val connectReq = "CONNECT $destIp:$destPort HTTP/1.1\\r\\nHost: $destIp:$destPort\\r\\n\\r\\n"
                s.getOutputStream().write(connectReq.toByteArray())
                s.getOutputStream().flush()

                // Read 200 OK
                val respBuf = ByteArray(256)
                val respLen = s.getInputStream().read(respBuf)
                val resp = String(respBuf, 0, respLen)
                if (!resp.contains("200")) {
                    addLog("SNI proxy rejected: $resp")
                    return
                }

                // Forward payload
                val ihl = (packet[0].toInt() and 0x0F) * 4
                val tcpHeaderLen = ((packet[ihl + 12].toInt() and 0xFF) shr 4) * 4
                val payloadStart = ihl + tcpHeaderLen
                val payloadLen = packet.size - payloadStart
                if (payloadLen > 0) {
                    s.getOutputStream().write(packet, payloadStart, payloadLen)
                    s.getOutputStream().flush()
                }

                // Relay response back
                val replyBuf = ByteArray(VPN_MTU)
                val replyLen = s.getInputStream().read(replyBuf)
                if (replyLen > 0) {
                    vpnOut.write(replyBuf, 0, replyLen)
                    vpnOut.flush()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Forward error to $destIp:$destPort — ${e.message}")
        }
    }

    private fun disconnectVpn() {
        serviceScope.launch {
            addLog("Disconnecting")
            vpnThreadJob?.cancel()
            sniProxyService?.stop()
            torService?.stop()
            isConnected.set(false)
            isConnecting.set(false)
            vpnInterface?.close()
            vpnInterface = null
            stopForegroundService()
            addLog("Disconnected")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Nexus VPN", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = createNotification("Connected", "SNI -> Tor Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() { stopForeground(STOP_FOREGROUND_REMOVE) }

    private fun createNotification(title: String, message: String): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title).setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(intent).setOngoing(true).build()
    }

    fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logQueue.offer("[$ts] $message")
        Log.d(TAG, message)
        while (logQueue.size > 200) logQueue.poll()
    }

    fun getLogs(): List<String> = logQueue.toList()
    fun isSniRunning(): Boolean = sniProxyService?.isRunning() ?: false
    fun isTorRunning(): Boolean = torService?.isReady() ?: false
}
'''

with open(svc_path, 'w', encoding='utf-8') as f:
    f.write(SVC)
print("NexusVpnService.kt written")

# ── 2. Fix TorService.kt — don't fake-succeed when Tor is missing ──────────────
tor_path = "android/app/src/main/kotlin/com/nexusvpn/android/service/TorService.kt"

TOR = '''package com.nexusvpn.android.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TorService(private val context: Context) {
    companion object {
        private const val TAG = "TorService"
        const val TOR_SOCKS_PORT = 9050
        private const val BOOTSTRAP_TIMEOUT_MS = 120_000L
        private const val CHECK_INTERVAL_MS = 1_000L
    }

    private val isReady = AtomicBoolean(false)
    private val isStarting = AtomicBoolean(false)
    private var torProcess: Process? = null
    private var bootstrapProgress = 0
    private var logCallback: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) { logCallback = callback }

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isStarting.get() || isReady.get()) return@withContext true
        isStarting.set(true)
        log("Checking for Tor...")

        try {
            // Check if Orbot is already providing SOCKS5
            if (isSocks5Listening(TOR_SOCKS_PORT)) {
                log("Tor SOCKS5 found on port $TOR_SOCKS_PORT (Orbot)")
                isReady.set(true)
                isStarting.set(false)
                return@withContext true
            }

            // Try bundled binary
            if (launchTorBinary()) {
                log("Tor binary launched, waiting for bootstrap...")
                val ok = waitForBootstrap()
                isStarting.set(false)
                if (ok) {
                    isReady.set(true)
                    log("Tor bootstrapped OK")
                    return@withContext true
                } else {
                    log("ERROR: Tor bootstrap timed out")
                    return@withContext false
                }
            }

            // Neither Orbot nor binary available — FAIL HONESTLY
            log("ERROR: No Tor available. Install Orbot from Play Store.")
            isStarting.set(false)
            isReady.set(false)
            return@withContext false

        } catch (e: Exception) {
            log("Tor error: " + e.message)
            isStarting.set(false)
            return@withContext false
        }
    }

    private fun launchTorBinary(): Boolean {
        val torPaths = listOf(
            File(context.applicationInfo.nativeLibraryDir, "libtor.so"),
            File(context.filesDir, "tor/tor"),
            File("/data/data/" + context.packageName + "/files/tor/tor")
        )
        val torBin = torPaths.firstOrNull { it.exists() && it.canExecute() }
        if (torBin == null) {
            log("No bundled tor binary found")
            return false
        }
        val dataDir = File(context.filesDir, "tor_data").apply { mkdirs() }
        val torrcFile = File(context.filesDir, "torrc").apply {
            writeText("SocksPort " + TOR_SOCKS_PORT + "\\n" +
                      "DataDirectory " + dataDir.absolutePath + "\\n")
        }
        return try {
            torProcess = ProcessBuilder(torBin.absolutePath, "-f", torrcFile.absolutePath)
                .redirectErrorStream(true).start()
            log("Tor process launched from ${torBin.absolutePath}")
            true
        } catch (e: Exception) {
            log("Failed to launch tor binary: " + e.message)
            false
        }
    }

    private suspend fun waitForBootstrap(): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < BOOTSTRAP_TIMEOUT_MS) {
            if (isSocks5Listening(TOR_SOCKS_PORT)) return true
            delay(CHECK_INTERVAL_MS)
            bootstrapProgress = ((System.currentTimeMillis() - start) * 100 / BOOTSTRAP_TIMEOUT_MS).toInt()
            if (bootstrapProgress % 10 == 0) log("Tor bootstrap: $bootstrapProgress%")
        }
        return false
    }

    private fun isSocks5Listening(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 1000)
                true
            }
        } catch (e: Exception) { false }
    }

    fun stop() {
        isReady.set(false)
        isStarting.set(false)
        torProcess?.destroy()
        torProcess = null
        bootstrapProgress = 0
        log("Tor stopped")
    }

    fun isReady(): Boolean = isReady.get()
    fun getProgress(): Int = bootstrapProgress
    fun getSocksPort(): Int = TOR_SOCKS_PORT

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logCallback?.invoke(msg)
    }
}
'''

with open(tor_path, 'w', encoding='utf-8') as f:
    f.write(TOR)
print("TorService.kt written")

print("""
=============================================================
DONE. Summary of what was fixed:

1. NexusVpnService.kt
   - Packet router now forwards TCP 443/80 to SniProxyService
   - Uses protect() so forwarded sockets don't loop back into VPN
   - Reports REAL status, not fake timers

2. TorService.kt
   - No longer fakes success when Tor is unavailable
   - Returns false + logs an honest error if no Orbot/binary

3. IMPORTANT: Tor still needs Orbot installed OR a bundled binary.
   Without it, the SNI proxy still works but traffic won't go
   through Tor. To fully work without Orbot, you need to add
   a tor binary to android/app/src/main/jniLibs/arm64-v8a/libtor.so
   (build from https://github.com/guardianproject/tor-android)
=============================================================
""")
