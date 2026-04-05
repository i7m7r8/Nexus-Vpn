package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.net.VpnService
import android.util.Log
import com.nexusvpn.android.MainActivity
import com.nexusvpn.android.R
import com.nexusvpn.android.NexusVpnApplication
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.FileWriter

class NexusVpnService : VpnService() {
    companion object {
        const val TAG = "NexusVpnService"
        const val ACTION_STATUS = "com.nexusvpn.android.STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_SNI = "sni"
        const val EXTRA_BRIDGES = "bridges"
        const val NOTIF_ID = 1
        const val CHAN_ID = "nexus_vpn_channel"

        // Simple static log buffer — no instance needed
        private val logBuffer = mutableListOf<String>()
        private const val MAX_LOG_LINES = 500

        fun addLog(msg: String) {
            synchronized(logBuffer) {
                logBuffer.add(msg)
                if (logBuffer.size > MAX_LOG_LINES) logBuffer.removeAt(0)
            }
        }

        fun getLogBufferNative(): String {
            synchronized(logBuffer) {
                return logBuffer.joinToString("\n")
            }
        }
    }

    init {
        System.loadLibrary("nexus_vpn")
        Log.i(TAG, "✅ Native library loaded")
    }

    // Native methods at CLASS level — matches Rust JNI names
    private external fun initVpnNative(tunFd: Int, sniHostname: String, bridgeConfig: String): Boolean
    private external fun stopVpnNative()
    private external fun setSniHostnameNative(hostname: String): Boolean

    private var tunFd: ParcelFileDescriptor? = null
    private var torProcess: Process? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🔵 Service onCreate")
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "🔵 Service onStartCommand — action: ${intent?.action}")
        when (intent?.action) {
            "CONNECT" -> {
                Log.i(TAG, "🟢 CONNECT action received")
                connect()
            }
            "DISCONNECT" -> {
                Log.i(TAG, "🔴 DISCONNECT action received")
                disconnect()
            }
            "UPDATE_SNI" -> intent.getStringExtra("sni_host")?.let {
                NexusVpnApplication.prefs.sniHostname = it
                try { setSniHostnameNative(it) } catch (_: Exception) {}
            }
            else -> Log.w(TAG, "⚠️ Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    private fun connect() {
        Log.i(TAG, "🟢 connect() called, isRunning=$isRunning")
        if (isRunning) {
            Log.w(TAG, "⚠️ Already running, ignoring")
            return
        }
        isRunning = true

        try {
            val prefs = NexusVpnApplication.prefs
            val sni = prefs.sniHostname ?: "www.cloudflare.com"
            Log.i(TAG, "📝 SNI: $sni, Bridges: ${prefs.useBridges}")

            // MUST call startForeground within 5 seconds
            Log.i(TAG, "📢 Calling startForeground...")
            startForeground(NOTIF_ID, notif("Starting Tor..."))
            Log.i(TAG, "✅ startForeground called")

            // Step 1: Extract Tor binary from assets
            val torDir = File(applicationContext.filesDir, "tor")
            torDir.mkdirs()
            val torBinary = extractTorBinary(torDir)
            if (torBinary == null) {
                Log.e(TAG, "❌ Failed to find Tor binary in assets")
                broadcastStatus("Error: Tor binary missing")
                isRunning = false
                return
            }
            Log.i(TAG, "✅ Tor binary ready: ${torBinary.absolutePath} (${torBinary.length()} bytes)")

            // Step 2: Copy geoip files from assets to torDir
            copyGeoipFiles(torDir)

            // Step 3: Generate torrc with SNI config (InviZible Pro style)
            val torrcFile = File(torDir, "torrc")
            val torDataDir = File(torDir, "data")
            torDataDir.mkdirs()
            generateTorrc(torrcFile, torDataDir, sni, prefs)
            Log.i(TAG, "✅ torrc generated: ${torrcFile.absolutePath}")
            Log.i(TAG, "🎭 SNI Host: $sni (TLS handshake will show this hostname)")

            // Step 4: Start Tor as subprocess
            Log.i(TAG, "🚀 Starting Tor process...")
            startTorProcess(torBinary, torrcFile, torDataDir)

            // Step 4: Update notification
            val statusMsg = when {
                prefs.useBridges && prefs.killSwitch -> "Starting Bridge + Tor + Kill Switch"
                prefs.useBridges -> "Starting Bridge + Tor"
                prefs.killSwitch -> "Starting Tor + Kill Switch"
                else -> "Starting Tor"
            }
            startForeground(NOTIF_ID, notif(statusMsg))
            broadcastStatus(statusMsg, sni, prefs.useBridges)

            // Step 5: Build VPN interface
            val builder = Builder()
                .setSession("Nexus VPN")
                .addAddress("10.8.0.2", 24)
                .addDnsServer("127.0.0.1")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(false)

            // Kill switch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && prefs.killSwitch) {
                builder.setBlocking(true)
                Log.i(TAG, "🔒 Kill switch enabled")
            }

            tunFd = builder.establish()
                ?: return run { Log.e(TAG, "❌ establish() returned null"); disconnect() }
            Log.i(TAG, "✅ TUN interface established")

            // Step 6: Start native VPN (routes TUN to local SOCKS)
            val bridgeConfig = buildBridgeConfigJson(prefs)
            try {
                Log.i(TAG, "🔌 Calling initVpnNative...")
                val initResult = initVpnNative(tunFd!!.fd, sni, bridgeConfig)
                if (!initResult) {
                    Log.e(TAG, "❌ initVpnNative returned false")
                    tunFd?.close()
                    tunFd = null
                    disconnect()
                    return
                }
                Log.i(TAG, "✅ initVpnNative returned true")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "❌ Native library not found", e)
                tunFd?.close()
                tunFd = null
                disconnect()
                return
            } catch (e: Exception) {
                Log.e(TAG, "❌ initVpnNative crashed", e)
                tunFd?.close()
                tunFd = null
                disconnect()
                return
            }

            val finalMsg = when {
                prefs.useBridges && prefs.killSwitch -> "Bridge + Tor + Kill Switch"
                prefs.useBridges -> "Connected via Bridge + Tor"
                prefs.killSwitch -> "Connected via Tor + Kill Switch"
                else -> "Connected via Tor"
            }
            startForeground(NOTIF_ID, notif(finalMsg))
            NexusVpnApplication.prefs.isVpnConnected = true
            broadcastStatus(finalMsg, sni, prefs.useBridges)
            Log.i(TAG, "✅ VPN started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start VPN", e)
            addLog("ERROR: ${e.message}")
            disconnect()
        }
    }

    private fun extractTorBinary(torDir: File): File? {
        val abi = when {
            Build.SUPPORTED_ABIS.any { it.contains("arm64") || it.contains("aarch64") } -> "arm64"
            Build.SUPPORTED_ABIS.any { it.contains("x86_64") } -> "x86_64"
            else -> {
                Log.e(TAG, "❌ Unsupported ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                return null
            }
        }

        val assetName = "tor-$abi"
        val destFile = File(torDir, "tor")

        // Check if already extracted
        if (destFile.exists() && destFile.canExecute() && destFile.length() > 1_000_000) {
            Log.i(TAG, "✅ Tor binary already extracted: ${destFile.absolutePath}")
            return destFile
        }

        // Extract from assets
        return try {
            Log.i(TAG, "📦 Extracting Tor binary from assets: $assetName")
            assets.open(assetName).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.setExecutable(true, false)
            destFile.setReadable(true, false)

            if (!destFile.canExecute()) {
                Log.e(TAG, "❌ Failed to make Tor binary executable")
                return null
            }

            Log.i(TAG, "✅ Tor binary extracted: ${destFile.absolutePath} (${destFile.length()} bytes)")
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to extract Tor binary from assets", e)
            null
        }
    }

    private fun copyGeoipFiles(torDir: File) {
        try {
            assets.list("tor")?.filter { it.startsWith("geoip") }?.forEach { assetName ->
                val dest = File(torDir, assetName)
                if (!dest.exists()) {
                    assets.open("tor/$assetName").use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "📄 Copied $assetName")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not copy geoip files", e)
        }
    }

    private fun generateTorrc(torrc: File, dataDir: File, sni: String, prefs: com.nexusvpn.android.data.Prefs) {
        val lines = mutableListOf<String>()

        lines.add("RunAsDaemon 0")
        lines.add("AvoidDiskWrites 1")
        lines.add("DataDirectory ${dataDir.absolutePath}")

        // Local listeners
        lines.add("SOCKSPort 127.0.0.1:9050")
        lines.add("DNSPort 127.0.0.1:5400")
        lines.add("TransPort 127.0.0.1:9040")
        lines.add("HTTPTunnelPort 127.0.0.1:8118")

        // SNI config
        lines.add("SNI $sni")

        // Bridge config
        if (prefs.useBridges) {
            lines.add("UseBridges 1")
            when (prefs.bridgeType) {
                "obfs4" -> {
                    // obfs4 via lyrebird (built into Tor 0.4.9+)
                    if (!prefs.customBridgeLine.isNullOrEmpty()) {
                        lines.add("Bridge ${prefs.customBridgeLine}")
                    }
                }
                "snowflake" -> {
                    // snowflake (built into Tor 0.4.9+)
                    if (!prefs.customBridgeLine.isNullOrEmpty()) {
                        lines.add("Bridge ${prefs.customBridgeLine}")
                    }
                }
                "vanilla" -> {
                    lines.add("UseBridges 0")
                }
            }
        }

        // GeoIP (from torDir where we copied them)
        val geoip = File(torrc.parentFile, "geoip")
        val geoip6 = File(torrc.parentFile, "geoip6")
        if (geoip.exists()) lines.add("GeoIPFile ${geoip.absolutePath}")
        if (geoip6.exists()) lines.add("GeoIPv6File ${geoip6.absolutePath}")

        // Logging
        lines.add("Log notice stdout")

        // Write torrc
        FileWriter(torrc).use { it.write(lines.joinToString("\n")) }
        Log.i(TAG, "📝 Generated torrc with SNI=$sni, Bridges=${prefs.useBridges}")
    }

    private fun startTorProcess(torBinary: File, torrc: File, dataDir: File) {
        val processBuilder = ProcessBuilder(
            torBinary.absolutePath,
            "-f", torrc.absolutePath,
            "--DataDirectory", dataDir.absolutePath
        )
        processBuilder.redirectErrorStream(true)

        torProcess = processBuilder.start()

        // Read Tor output in background
        Thread {
            val reader = BufferedReader(InputStreamReader(torProcess?.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                Log.i("Tor", line!!)
                addLog(line!!)
                // Check for bootstrap completion
                if (line!!.contains("Bootstrapped 100%")) {
                    broadcastStatus("Connected via Tor", NexusVpnApplication.prefs.sniHostname ?: "", NexusVpnApplication.prefs.useBridges)
                }
            }
        }.start()

        Log.i(TAG, "✅ Tor process started")
    }

    private fun buildBridgeConfigJson(prefs: com.nexusvpn.android.data.Prefs): String {
        return """{"socks_port":9050,"dns_port":5400,"use_bridges":${prefs.useBridges},"bridge_type":"${prefs.bridgeType}"}"""
    }

    private fun disconnect() {
        Log.i(TAG, "🔴 disconnect() called")
        isRunning = false
        NexusVpnApplication.prefs.isVpnConnected = false
        broadcastStatus("Disconnected")

        // Stop native first
        try { stopVpnNative() } catch (_: Exception) {}

        // Close TUN fd
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null

        // Stop Tor process
        try {
            torProcess?.destroy()
            torProcess?.waitFor()
            torProcess = null
        } catch (_: Exception) {}

        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        Log.i(TAG, "✅ VPN disconnected")
    }

    private fun createChannel() {
        val chan = NotificationChannel(CHAN_ID, "Nexus VPN", NotificationManager.IMPORTANCE_LOW).apply {
            description = "VPN connection status"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
    }

    private fun notif(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, CHAN_ID)
            .setContentTitle("Nexus VPN")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun broadcastStatus(status: String, sni: String = "", bridges: Boolean = false) {
        val intent = Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_SNI, sni)
            putExtra(EXTRA_BRIDGES, bridges)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onRevoke() {
        Log.i(TAG, "🔴 VPN revoked")
        disconnect()
    }

    override fun onDestroy() {
        Log.i(TAG, "🔴 Service onDestroy")
        disconnect()
        super.onDestroy()
    }
}
