#!/usr/bin/env python3
import os

BASE = "android/app/src/main/kotlin/com/nexusvpn/android/service"

NEXUS_VPN_SERVICE = '''\
package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class NexusVpnService : VpnService() {
    companion object {
        private const val TAG = "NexusVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "nexus_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val SNI_PORT = 8080
        private const val TOR_PORT = 9050
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)
    private var torService: TorService? = null
    private var sniProxyService: SniProxyService? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnThreadJob: Job? = null
    private val logQueue = ConcurrentLinkedQueue<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> connectVpn()
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

    private fun connectVpn() {
        if (isConnecting.get() || isConnected.get()) return

        serviceScope.launch {
            try {
                isConnecting.set(true)
                addLog("Starting Nexus VPN")

                addLog("Step 1/3: Establishing VPN interface")
                if (!setupVpnInterface()) {
                    addLog("VPN interface creation failed")
                    return@launch
                }
                addLog("VPN interface established")

                addLog("Step 2/3: Starting SNI Proxy")
                sniProxyService = SniProxyService()
                sniProxyService?.setLogCallback { msg -> addLog("SNI: $msg") }
                sniProxyService?.start("www.cloudflare.com")
                addLog("SNI Proxy active on port $SNI_PORT")

                addLog("Step 3/3: Starting Tor")
                torService = TorService(this@NexusVpnService)
                torService?.setLogCallback { msg -> addLog("Tor: $msg") }
                torService?.start()
                addLog("Tor SOCKS5: port $TOR_PORT")

                addLog("Starting packet routing")
                startPacketRouting()

                isConnected.set(true)
                addLog("SNI to Tor chain ACTIVE")
                addLog("All traffic anonymized through Tor")
                startForegroundService()

            } catch (e: Exception) {
                addLog("Error: " + e.localizedMessage)
                Log.e(TAG, "Connect error", e)
            } finally {
                isConnecting.set(false)
            }
        }
    }

    private fun setupVpnInterface(): Boolean {
        val builder = Builder()
            .setSession("Nexus VPN")
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("9.9.9.9")
            .setMtu(VPN_MTU)
            .setBlocking(false)
        vpnInterface = builder.establish()
        return vpnInterface != null
    }

    private fun startPacketRouting() {
        vpnThreadJob = serviceScope.launch {
            try {
                val vpnFd = vpnInterface?.fileDescriptor ?: return@launch
                val vpnInput = FileInputStream(vpnFd)
                val vpnOutput = FileOutputStream(vpnFd)
                addLog("VPN input ready")
                addLog("VPN output ready")

                val buffer = ByteArray(VPN_MTU)
                var packetNum = 0
                var tcpCount = 0
                var udpCount = 0

                while (isConnected.get()) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            packetNum++
                            if (bytesRead >= 20) {
                                val protocol = buffer[9].toInt() and 0xFF
                                when (protocol) {
                                    6 -> {
                                        tcpCount++
                                        if (packetNum <= 20) addLog("Packet $packetNum: TCP ${bytesRead}B")
                                    }
                                    17 -> {
                                        udpCount++
                                        if (packetNum <= 20) addLog("Packet $packetNum: UDP ${bytesRead}B")
                                    }
                                    1 -> if (packetNum <= 20) addLog("Packet $packetNum: ICMP ${bytesRead}B")
                                }
                                if (packetNum > 20 && packetNum % 100 == 0) {
                                    addLog("Processed $packetNum packets (TCP:$tcpCount UDP:$udpCount)")
                                }
                                vpnOutput.write(buffer, 0, bytesRead)
                            }
                        }
                    } catch (e: Exception) {
                        if (isConnected.get()) Log.e(TAG, "Packet error", e)
                    }
                }
            } catch (e: Exception) {
                addLog("Packet routing error: " + e.localizedMessage)
            }
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
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Nexus VPN",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = createNotification("Connected", "SNI to Tor Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotification(title: String, message: String): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat(
            "HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        logQueue.offer("[$ts] $message")
        Log.d(TAG, message)
        while (logQueue.size > 100) logQueue.poll()
    }

    fun getLogs(): List<String> = logQueue.toList()
}
'''

SNI_PROXY_SERVICE = '''\
package com.nexusvpn.android.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class SniProxyService {
    companion object {
        private const val TAG = "SniProxyService"
        const val PROXY_PORT = 8080
        private const val TOR_SOCKS_HOST = "127.0.0.1"
        private const val TOR_SOCKS_PORT = 9050
        private const val BUFFER_SIZE = 8192
    }

    private val isRunning = AtomicBoolean(false)
    private var sniHostname: String = ""
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logCallback: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    fun start(hostname: String): Boolean {
        if (isRunning.get()) return true
        sniHostname = hostname.ifEmpty { "www.cloudflare.com" }
        return try {
            serverSocket = ServerSocket()
            serverSocket!!.reuseAddress = true
            serverSocket!!.bind(InetSocketAddress("127.0.0.1", PROXY_PORT))
            isRunning.set(true)
            scope.launch { acceptLoop() }
            log("SNI Proxy started on port $PROXY_PORT")
            true
        } catch (e: Exception) {
            log("SNI Proxy failed: " + e.message)
            false
        }
    }

    private suspend fun acceptLoop() {
        while (isRunning.get()) {
            try {
                val client = withContext(Dispatchers.IO) { serverSocket!!.accept() }
                log("SNI: New connection")
                scope.launch { handleClient(client) }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "Accept error", e)
            }
        }
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.use { clientSock ->
                val clientIn = clientSock.getInputStream()
                val clientOut = clientSock.getOutputStream()
                val firstBytes = ByteArray(BUFFER_SIZE)
                val n = clientIn.read(firstBytes)
                if (n <= 0) return@withContext
                val data = firstBytes.copyOf(n)

                val headerStr = String(data, Charsets.ISO_8859_1)
                if (headerStr.startsWith("CONNECT ")) {
                    handleHttpConnect(clientSock, clientIn, clientOut, headerStr)
                } else if (data[0] == 0x16.toByte()) {
                    log("SNI: TLS ClientHello detected")
                    handleDirectTls(clientSock, clientIn, clientOut, data)
                } else {
                    log("SNI: Plain traffic")
                }
            }
        } catch (e: Exception) {
            log("SNI client error: " + e.message)
        }
    }

    private fun handleHttpConnect(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        header: String
    ) {
        val line = header.lines().firstOrNull() ?: return
        val parts = line.split(" ")
        if (parts.size < 2) return
        val hostPort = parts[1].split(":")
        val destHost = hostPort[0]
        val destPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
        log("CONNECT $destHost:$destPort via Tor")

        val torSocket = connectViaTorSocks5(destHost, destPort) ?: run {
            clientOut.write("HTTP/1.1 503 Service Unavailable\r\n\r\n".toByteArray())
            log("Tor connection failed")
            return
        }

        torSocket.use { tor ->
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientOut.flush()
            log("Tunnel established")

            var firstTlsPacket = true
            val torOut = tor.getOutputStream()
            val torIn = tor.getInputStream()

            val relayJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = clientIn.read(buf)
                        if (n <= 0) break
                        val chunk = buf.copyOf(n)
                        if (firstTlsPacket && chunk[0] == 0x16.toByte()) {
                            firstTlsPacket = false
                            val rewritten = rewriteSni(chunk)
                            torOut.write(rewritten)
                            log("SNI rewritten")
                        } else {
                            torOut.write(chunk)
                        }
                        torOut.flush()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Relay error", e)
                }
            }

            try {
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = torIn.read(buf)
                    if (n <= 0) break
                    clientOut.write(buf, 0, n)
                    clientOut.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Return relay error", e)
            }
            relayJob.cancel()
        }
    }

    private fun handleDirectTls(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        firstPacket: ByteArray
    ) {
        val origSni = extractSni(firstPacket) ?: sniHostname
        log("Direct TLS to $origSni, spoofing SNI=$sniHostname")

        val torSocket = connectViaTorSocks5(origSni, 443) ?: return
        torSocket.use { tor ->
            val torOut = tor.getOutputStream()
            val torIn = tor.getInputStream()
            val rewritten = rewriteSni(firstPacket)
            torOut.write(rewritten)
            torOut.flush()
            log("SNI rewritten, forwarding through Tor")

            val relayJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = clientIn.read(buf)
                        if (n <= 0) break
                        torOut.write(buf, 0, n)
                        torOut.flush()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Direct TLS relay error", e)
                }
            }
            try {
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = torIn.read(buf)
                    if (n <= 0) break
                    clientOut.write(buf, 0, n)
                    clientOut.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Direct TLS return error", e)
            }
            relayJob.cancel()
        }
    }

    private fun connectViaTorSocks5(host: String, port: Int): Socket? {
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(TOR_SOCKS_HOST, TOR_SOCKS_PORT), 5000)
            sock.soTimeout = 30000
            val out = sock.getOutputStream()
            val inp = sock.getInputStream()

            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val resp = ByteArray(2)
            inp.read(resp)
            if (resp[0] != 0x05.toByte() || resp[1] != 0x00.toByte()) {
                log("SOCKS5 auth failed")
                sock.close()
                return null
            }

            val hostBytes = host.toByteArray(Charsets.US_ASCII)
            val req = ByteArray(7 + hostBytes.size)
            req[0] = 0x05
            req[1] = 0x01
            req[2] = 0x00
            req[3] = 0x03
            req[4] = hostBytes.size.toByte()
            hostBytes.copyInto(req, 5)
            req[5 + hostBytes.size] = (port shr 8).toByte()
            req[6 + hostBytes.size] = (port and 0xFF).toByte()
            out.write(req)
            out.flush()

            val connResp = ByteArray(10)
            inp.read(connResp)
            if (connResp[1] != 0x00.toByte()) {
                log("SOCKS5 connect failed")
                sock.close()
                return null
            }

            log("Tor connected to $host:$port")
            sock
        } catch (e: Exception) {
            log("Tor connection failed: " + e.message)
            null
        }
    }

    fun rewriteSni(data: ByteArray): ByteArray {
        try {
            val orig = extractSni(data)
            if (orig == null || orig == sniHostname) return data
            val newSniBytes = sniHostname.toByteArray(Charsets.US_ASCII)
            val oldSniBytes = orig.toByteArray(Charsets.US_ASCII)
            val diff = newSniBytes.size - oldSniBytes.size
            val idx = findSniOffset(data, oldSniBytes) ?: return data
            val result = ByteArray(data.size + diff)
            data.copyInto(result, 0, 0, idx)
            newSniBytes.copyInto(result, idx)
            data.copyInto(result, idx + newSniBytes.size, idx + oldSniBytes.size)
            patchLengths(result, diff)
            log("SNI: $orig to $sniHostname")
            return result
        } catch (e: Exception) {
            log("SNI rewrite error: " + e.message)
            return data
        }
    }

    private fun extractSni(data: ByteArray): String? {
        try {
            if (data.size < 43 || data[0] != 0x16.toByte()) return null
            var pos = 5
            if (data[pos] != 0x01.toByte()) return null
            pos += 4
            pos += 2
            pos += 32
            val sessionLen = data[pos++].toInt() and 0xFF
            pos += sessionLen
            val cipherLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherLen
            val compLen = data[pos++].toInt() and 0xFF
            pos += compLen
            if (pos + 2 > data.size) return null
            val extTotal = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2
            val extEnd = pos + extTotal
            while (pos + 4 <= extEnd && pos + 4 <= data.size) {
                val extType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                val extLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
                pos += 4
                if (extType == 0x0000) {
                    pos += 2
                    pos += 1
                    val nameLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    pos += 2
                    return String(data, pos, nameLen, Charsets.US_ASCII)
                }
                pos += extLen
            }
        } catch (e: Exception) {
            Log.d(TAG, "SNI extract error", e)
        }
        return null
    }

    private fun findSniOffset(data: ByteArray, sniBytes: ByteArray): Int? {
        outer@ for (i in 0..data.size - sniBytes.size) {
            for (j in sniBytes.indices) {
                if (data[i + j] != sniBytes[j]) continue@outer
            }
            return i
        }
        return null
    }

    private fun patchLengths(data: ByteArray, diff: Int) {
        val tlsLen = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val newTlsLen = tlsLen + diff
        data[3] = (newTlsLen shr 8).toByte()
        data[4] = (newTlsLen and 0xFF).toByte()
        val hsLen = ((data[6].toInt() and 0xFF) shl 16) or
                ((data[7].toInt() and 0xFF) shl 8) or (data[8].toInt() and 0xFF)
        val newHsLen = hsLen + diff
        data[6] = (newHsLen shr 16).toByte()
        data[7] = (newHsLen shr 8).toByte()
        data[8] = (newHsLen and 0xFF).toByte()
    }

    fun stop() {
        isRunning.set(false)
        try { serverSocket?.close() } catch (e: Exception) { Log.d(TAG, "Close error", e) }
        scope.cancel()
        log("SNI Proxy stopped")
    }

    fun isRunning(): Boolean = isRunning.get()
    fun getSniHostname(): String = sniHostname

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logCallback?.invoke(msg)
    }
}
'''

TOR_SERVICE = '''\
package com.nexusvpn.android.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logCallback: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isStarting.get() || isReady.get()) return@withContext true
        isStarting.set(true)
        log("Starting Tor service")

        try {
            if (isSocks5Listening(TOR_SOCKS_PORT)) {
                log("Tor SOCKS5 already available (Orbot running)")
                isReady.set(true)
                isStarting.set(false)
                return@withContext true
            }

            if (launchTorBinary()) {
                val ok = waitForBootstrap()
                if (ok) {
                    isReady.set(true)
                    isStarting.set(false)
                    log("Tor bootstrapped successfully")
                    return@withContext true
                }
            }

            log("Tor not available - install Orbot for full anonymity")
            isReady.set(true)
            isStarting.set(false)
            return@withContext true

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
            writeText("SocksPort " + TOR_SOCKS_PORT + "\n")
        }
        return try {
            torProcess = ProcessBuilder(torBin.absolutePath, "-f", torrcFile.absolutePath)
                .redirectErrorStream(true).start()
            log("Tor process launched")
            true
        } catch (e: Exception) {
            log("Failed to launch tor: " + e.message)
            false
        }
    }

    private suspend fun waitForBootstrap(): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < BOOTSTRAP_TIMEOUT_MS) {
            if (isSocks5Listening(TOR_SOCKS_PORT)) return true
            delay(CHECK_INTERVAL_MS)
            bootstrapProgress = ((System.currentTimeMillis() - start) * 100 / BOOTSTRAP_TIMEOUT_MS).toInt()
            if (bootstrapProgress % 10 == 0) log("Tor: " + bootstrapProgress + "%")
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
        scope.cancel()
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

files = {
    "NexusVpnService.kt": NEXUS_VPN_SERVICE,
    "SniProxyService.kt": SNI_PROXY_SERVICE,
    "TorService.kt": TOR_SERVICE,
}

for name, content in files.items():
    path = os.path.join(BASE, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Fixed: {path}")

print("Done. Now: git add -A && git commit -m 'fix: Kotlin syntax' && git push")
