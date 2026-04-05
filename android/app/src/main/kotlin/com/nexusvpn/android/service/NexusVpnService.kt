package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
        const val NOTIF_ID = 1
        const val CHAN_ID = "nexus_vpn_channel"

        private val logBuffer = mutableListOf<String>()
        private const val MAX_LOG_LINES = 2000

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

    private external fun initVpnNative(tunFd: Int, sniHostname: String, bridgeConfig: String): Boolean
    private external fun stopVpnNative()

    private var tunFd: ParcelFileDescriptor? = null
    private var torProcess: Process? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🔵 Service onCreate")
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "🔵 onStartCommand action=${intent?.action}")
        when (intent?.action) {
            "CONNECT" -> connect()
            "DISCONNECT" -> disconnect()
            else -> Log.w(TAG, "⚠️ Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    private fun connect() {
        Log.i(TAG, "🟢 connect() isRunning=$isRunning")
        if (isRunning) return
        isRunning = true

        try {
            val prefs = NexusVpnApplication.prefs
            val sni = prefs.sniHostname ?: "www.cloudflare.com"
            addLog("Starting Nexus VPN...")
            addLog("SNI: $sni")

            startForeground(NOTIF_ID, notif("Starting Tor..."))

            // Extract tor binary from assets
            val torDir = File(applicationContext.filesDir, "tor")
            torDir.mkdirs()
            val torBinary = extractTorBinary(torDir)
                ?: return fail("Tor binary missing")
            addLog("Tor binary: ${torBinary.name} (${torBinary.length() / 1024 / 1024}MB)")

            // Copy geoip
            copyGeoipFiles(torDir)

            // Generate torrc
            val torrc = File(torDir, "torrc")
            val dataDir = File(torDir, "data").apply { mkdirs() }
            generateTorrc(torrc, dataDir, sni, prefs)
            addLog("torrc generated")

            // Start Tor process
            startTorProcess(torBinary, torrc, dataDir)

            // Build VPN interface
            val builder = Builder()
                .setSession("Nexus VPN")
                .addAddress("10.8.0.2", 24)
                .addDnsServer("127.0.0.1")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && prefs.killSwitch) {
                builder.setBlocking(true)
            }

            tunFd = builder.establish()
                ?: return fail("VPN establish failed")
            addLog("TUN interface established (10.8.0.2)")

            // Start native VPN routing
            val bridgeConfig = """{"socks_port":9050,"dns_port":5400,"use_bridges":${prefs.useBridges},"bridge_type":"${prefs.bridgeType}"}"""
            try {
                val result = initVpnNative(tunFd!!.fd, sni, bridgeConfig)
                if (!result) return fail("initVpnNative returned false")
                addLog("Native VPN routing initialized")
            } catch (e: Exception) {
                return fail("Native lib error: ${e.message}")
            }

            val msg = if (prefs.useBridges) "Connected via Bridge + Tor" else "Connected via Tor"
            startForeground(NOTIF_ID, notif(msg))
            NexusVpnApplication.prefs.isVpnConnected = true
            broadcastStatus(msg)
            addLog("✅ VPN connected successfully")
            Log.i(TAG, "✅ VPN started")

        } catch (e: Exception) {
            Log.e(TAG, "❌ connect failed", e)
            addLog("ERROR: ${e.message}")
            fail(e.message ?: "Unknown error")
        }
    }

    private fun extractTorBinary(torDir: File): File? {
        val dest = File(torDir, "tor")
        if (dest.exists() && dest.length() > 1_000_000) return dest
        return try {
            assets.open("tor").use { it ->
                dest.outputStream().use { out -> it.copyTo(out) }
            }
            dest.setExecutable(true, false)
            Log.i(TAG, "Tor binary extracted to ${dest.absolutePath}")
            dest
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract tor binary", e)
            null
        }
    }

    private fun copyGeoipFiles(torDir: File) {
        try {
            listOf("geoip", "geoip6").forEach { name ->
                try {
                    assets.open(name).use { it ->
                        val dest = File(torDir, name)
                        dest.outputStream().use { out -> it.copyTo(out) }
                    }
                    addLog("Copied $name")
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "geoip copy failed", e)
        }
    }

    private fun generateTorrc(torrc: File, dataDir: File, sni: String, prefs: com.nexusvpn.android.data.Prefs) {
        val lines = mutableListOf(
            "RunAsDaemon 0",
            "AvoidDiskWrites 1",
            "DataDirectory ${dataDir.absolutePath}",
            "SOCKSPort 127.0.0.1:9050",
            "DNSPort 127.0.0.1:5400",
            "TransPort 127.0.0.1:9040",
            "HTTPTunnelPort 127.0.0.1:8118",
            "SNI $sni",
            "Log notice stdout"
        )

        if (prefs.useBridges && !prefs.customBridgeLine.isNullOrEmpty()) {
            lines.add("UseBridges 1")
            lines.add("Bridge ${prefs.customBridgeLine}")
        }

        val geoip = File(torrc.parent, "geoip")
        val geoip6 = File(torrc.parent, "geoip6")
        if (geoip.exists()) lines.add("GeoIPFile ${geoip.absolutePath}")
        if (geoip6.exists()) lines.add("GeoIPv6File ${geoip6.absolutePath}")

        FileWriter(torrc).use { it.write(lines.joinToString("\n")) }
    }

    private fun startTorProcess(torBinary: File, torrc: File, dataDir: File) {
        val cmd = arrayOf(
            torBinary.absolutePath,
            "-f", torrc.absolutePath
        )
        Log.i(TAG, "Starting Tor: ${cmd.joinToString(" ")}")
        addLog("Starting Tor...")

        val pb = ProcessBuilder(*cmd).apply {
            redirectErrorStream(true)
        }
        torProcess = pb.start()

        // Read Tor output
        Thread {
            val reader = BufferedReader(InputStreamReader(torProcess?.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    Log.i("Tor", it)
                    addLog(it)
                    // Broadcast bootstrap progress
                    when {
                        it.contains("Bootstrapped 100%") -> broadcastStatus("Connected — Tor ready")
                        it.contains("Bootstrapped") -> {
                            val pct = Regex("Bootstrapped (\\d+)%").find(it)?.groupValues?.get(1)
                            pct?.let { p -> broadcastStatus("Bootstrapping $p%") }
                        }
                    }
                }
            }
        }.start()

        Thread.sleep(1500) // let Tor start
        Log.i(TAG, "Tor process started")
    }

    private fun fail(msg: String) {
        addLog("❌ $msg")
        broadcastStatus("Error: $msg")
        isRunning = false
        disconnect()
    }

    private fun disconnect() {
        Log.i(TAG, "🔴 disconnect()")
        isRunning = false
        NexusVpnApplication.prefs.isVpnConnected = false
        broadcastStatus("Disconnected")

        try { stopVpnNative() } catch (_: Exception) {}
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null

        try {
            torProcess?.destroy()
            torProcess?.waitFor()
            torProcess = null
        } catch (_: Exception) {}

        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        addLog("Disconnected")
        Log.i(TAG, "✅ Disconnected")
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

    private fun broadcastStatus(status: String) {
        Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_STATUS, status)
            setPackage(packageName)
        }.also { sendBroadcast(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onRevoke() { disconnect() }
    override fun onDestroy() { disconnect(); super.onDestroy() }
}
