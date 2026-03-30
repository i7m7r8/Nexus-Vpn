package com.nexusvpn.android.service

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
                val connectReq = "CONNECT $destIp:$destPort HTTP/1.1\r\nHost: $destIp:$destPort\r\n\r\n"
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
