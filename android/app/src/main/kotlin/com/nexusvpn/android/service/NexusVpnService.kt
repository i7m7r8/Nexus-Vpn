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

        init {
            System.loadLibrary("nexus_vpn")
        }

        external fun initVpnNative(tunFd: Int, sniHostname: String, bridgeConfig: String): Boolean
        external fun stopVpnNative()
        external fun setSniHostnameNative(hostname: String): Boolean
        external fun getLogBufferNative(): String
    }

    private var tunFd: ParcelFileDescriptor? = null
    private var torProcess: Process? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> connect()
            "DISCONNECT" -> disconnect()
            "UPDATE_SNI" -> intent.getStringExtra("sni_host")?.let {
                NexusVpnApplication.prefs.sniHostname = it
                try { setSniHostnameNative(it) } catch (_: Exception) {}
            }
        }
        return START_STICKY
    }

    private fun connect() {
        if (isRunning) return
        isRunning = true

        try {
            val prefs = NexusVpnApplication.prefs
            val sni = prefs.sniHostname ?: "www.cloudflare.com"

            // Step 1: Extract Tor binary from assets
            val torDir = File(applicationContext.filesDir, "tor")
            torDir.mkdirs()
            val torBinary = extractTorBinary(torDir)
            if (torBinary == null) {
                Log.e(TAG, "Failed to extract Tor binary")
                broadcastStatus("Error: Tor binary missing")
                isRunning = false
                return
            }

            // Step 2: Generate torrc with SNI config
            val torrcFile = File(torDir, "torrc")
            val torDataDir = File(torDir, "data")
            torDataDir.mkdirs()
            generateTorrc(torrcFile, torDataDir, sni, prefs)

            // Step 3: Start Tor as subprocess
            startTorProcess(torBinary, torrcFile, torDataDir)

            // Step 4: Wait for Tor to be ready (simple delay for bootstrap)
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
                ?: return run { Log.e(TAG, "establish() returned null"); disconnect() }

            // Step 6: Start native VPN (routes TUN to local SOCKS)
            val bridgeConfig = buildBridgeConfigJson(prefs)
            try {
                val initResult = initVpnNative(tunFd!!.fd, sni, bridgeConfig)
                if (!initResult) {
                    Log.e(TAG, "initVpnNative returned false")
                    tunFd?.close()
                    tunFd = null
                    disconnect()
                    return
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library not found", e)
                tunFd?.close()
                tunFd = null
                disconnect()
                return
            } catch (e: Exception) {
                Log.e(TAG, "initVpnNative crashed", e)
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
            Log.i(TAG, "VPN started (SNI: $sni, Bridges: ${prefs.useBridges}, KillSwitch: ${prefs.killSwitch})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            disconnect()
        }
    }

    private fun extractTorBinary(torDir: File): File? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it.contains("arm64") || it.contains("aarch64") }
            ?: Build.SUPPORTED_ABIS.firstOrNull { it.contains("x86_64") }
            ?: return null

        val archDir = if (abi.contains("arm64") || abi.contains("aarch64")) "arm64-v8a" else "x86_64"
        val assetPath = "tor/$archDir"

        torDir.mkdirs()

        // Recursively copy entire asset tree to torDir (preserves nested dirs)
        try {
            copyAssets(assets, assetPath, torDir)
            Log.i(TAG, "✅ Extracted Tor assets: $assetPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract Tor assets", e)
            return null
        }

        // Find the tor binary (may be in subdirectory)
        val torBinary = findFile(torDir, "tor")
        if (torBinary == null) {
            Log.e(TAG, "Tor binary not found in $torDir — contents: ${torDir.listFiles()?.map { it.name }?.joinToString(", ")}")
            return null
        }
        torBinary.setExecutable(true)
        Log.i(TAG, "✅ Found Tor binary: ${torBinary.absolutePath}")
        return torBinary
    }

    /** Recursively copy assets directory to destination */
    private fun copyAssets(assetMgr: android.content.res.AssetManager, assetPath: String, destDir: File) {
        val files = assetMgr.list(assetPath) ?: return
        if (files.isEmpty()) {
            // It's a file — copy it
            val destFile = File(destDir, assetPath.substringAfterLast("/"))
            // If we're at a leaf file, copy from the full path
            return
        }

        // If it has subitems, it's a directory
        val destSubDir = File(destDir, assetPath.substringAfterLast("/"))
        if (assetPath.contains("/")) {
            destSubDir.mkdirs()
        } else {
            destDir.mkdirs()
        }

        for (file in files) {
            val subPath = if (assetPath == "tor/arm64-v8a" || assetPath == "tor/x86_64") "$assetPath/$file" else file
            val fullAssetPath = if (assetPath == "tor/arm64-v8a" || assetPath == "tor/x86_64") subPath else "$assetPath/$file"
            val subFiles = assetMgr.list(fullAssetPath)
            if (subFiles.isNullOrEmpty()) {
                // It's a file
                val destFile = File(destSubDir, file)
                assetMgr.open(fullAssetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // It's a directory
                copyAssets(assetMgr, fullAssetPath, destSubDir)
            }
        }
    }

    /** Find a file by name recursively in a directory */
    private fun findFile(dir: File, name: String): File? {
        val files = dir.listFiles() ?: return null
        for (f in files) {
            if (f.name == name) return f
            if (f.isDirectory) {
                findFile(f, name)?.let { return it }
            }
        }
        return null
    }

    private fun generateTorrc(torrc: File, dataDir: File, sni: String, prefs: com.nexusvpn.android.data.Prefs) {
        val lines = mutableListOf<String>()

        // Basic config
        lines.add("RunAsDaemon 0")
        lines.add("AvoidDiskWrites 1")
        lines.add("DataDirectory ${dataDir.absolutePath}")

        // Local listeners
        lines.add("SOCKSPort 127.0.0.1:9050")
        lines.add("DNSPort 127.0.0.1:5400")
        lines.add("TransPort 127.0.0.1:9040")
        lines.add("HTTPTunnelPort 127.0.0.1:8118")

        // SNI config (for domain fronting)
        lines.add("SNI $sni")

        // Bridge config
        if (prefs.useBridges) {
            lines.add("UseBridges 1")

            when (prefs.bridgeType) {
                "obfs4" -> {
                    lines.add("ClientTransportPlugin obfs4 exec ${File(applicationContext.filesDir, "tor/lyrebird").absolutePath}")
                    if (!prefs.customBridgeLine.isNullOrEmpty()) {
                        lines.add("Bridge ${prefs.customBridgeLine}")
                    }
                }
                "snowflake" -> {
                    lines.add("ClientTransportPlugin snowflake exec ${File(applicationContext.filesDir, "tor/snowflake-client").absolutePath}")
                    if (!prefs.customBridgeLine.isNullOrEmpty()) {
                        lines.add("Bridge ${prefs.customBridgeLine}")
                    }
                }
                "vanilla" -> {
                    // No bridges, just use default Tor
                    lines.add("UseBridges 0")
                }
            }
        }

        // GeoIP
        val geoip = File(applicationContext.filesDir, "tor/geoip")
        val geoip6 = File(applicationContext.filesDir, "tor/geoip6")
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
        isRunning = false
        NexusVpnApplication.prefs.isVpnConnected = false
        broadcastStatus("Disconnected")

        // Stop native first (before closing TUN fd)
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
        Log.i(TAG, "VPN disconnected")
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
        disconnect()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
