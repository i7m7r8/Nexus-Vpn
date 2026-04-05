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
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "action=${intent?.action}")
        when (intent?.action) {
            "CONNECT" -> connect()
            "DISCONNECT" -> disconnect()
        }
        return START_STICKY
    }

    private fun connect() {
        Log.i(TAG, "connect() isRunning=$isRunning")
        if (isRunning) return
        isRunning = true

        try {
            val prefs = NexusVpnApplication.prefs
            val sni = prefs.sniHostname ?: "www.cloudflare.com"
            addLog("Starting Nexus VPN...")
            addLog("SNI: $sni")

            startForeground(NOTIF_ID, notif("Starting Tor..."))

            val torDir = File(applicationContext.filesDir, "tor").apply { mkdirs() }
            val torBinary = prepareTorBinary(torDir)
                ?: return fail("Tor binary missing")
            addLog("Tor: ${torBinary.length() / 1024 / 1024}MB")

            copyAssets(torDir)

            val torrc = File(torDir, "torrc")
            val dataDir = File(torDir, "data").apply { mkdirs() }
            writeTorrc(torrc, dataDir, sni, prefs)
            addLog("torrc generated")

            launchTorProcess(torBinary, torrc)

            val builder = Builder()
                .setSession("Nexus VPN")
                .addAddress("10.8.0.2", 24)
                .addDnsServer("127.0.0.1")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && prefs.killSwitch)
                builder.setBlocking(true)

            tunFd = builder.establish() ?: return fail("establish() returned null")
            addLog("TUN established: 10.8.0.2")

            val bc = """{"socks_port":9050,"dns_port":5400,"use_bridges":${prefs.useBridges},"bridge_type":"${prefs.bridgeType}"}"""
            try {
                if (!initVpnNative(tunFd!!.fd, sni, bc))
                    return fail("initVpnNative failed")
                addLog("Native routing initialized")
            } catch (e: Exception) {
                return fail("Native lib: ${e.message}")
            }

            val msg = if (prefs.useBridges) "Connected: Bridge + Tor" else "Connected: Tor"
            startForeground(NOTIF_ID, notif(msg))
            NexusVpnApplication.prefs.isVpnConnected = true
            broadcastStatus(msg)
            addLog("✅ Connected")

        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            addLog("ERROR: ${e.message}")
            fail(e.message ?: "Unknown")
        }
    }

    /** Extract tor binary from native libs (Guardian Project AAR) */
    private fun prepareTorBinary(torDir: File): File? {
        val dest = File(torDir, "tor")
        if (dest.exists() && dest.length() > 1_000_000) return dest

        // Try from native library dir (AAR packages libtor.so)
        val nativeDir = applicationInfo.nativeLibraryDir
        val srcNames = listOf("libtor.so", "tor")

        for (name in srcNames) {
            val src = File(nativeDir, name)
            if (src.exists()) {
                src.inputStream().use { it ->
                    dest.outputStream().use { out -> it.copyTo(out) }
                }
                dest.setExecutable(true, false)
                Log.i(TAG, "Copied $name -> tor (${dest.length()} bytes)")
                return dest
            }
        }

        // Fallback: try assets
        try {
            assets.open("tor").use { it ->
                dest.outputStream().use { out -> it.copyTo(out) }
            }
            dest.setExecutable(true, false)
            Log.i(TAG, "Extracted from assets")
            return dest
        } catch (_: Exception) {}

        Log.e(TAG, "No tor binary found. Native dir files: ${File(nativeDir).listFiles()?.map { it.name }?.joinToString()}")
        return null
    }

    private fun copyAssets(torDir: File) {
        listOf("geoip", "geoip6").forEach { name ->
            try {
                assets.open(name).use { it ->
                    File(torDir, name).outputStream().use { out -> it.copyTo(out) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun writeTorrc(torrc: File, dataDir: File, sni: String, prefs: com.nexusvpn.android.data.Prefs) {
        val lines = mutableListOf(
            "RunAsDaemon 0", "AvoidDiskWrites 1",
            "DataDirectory ${dataDir.absolutePath}",
            "SOCKSPort 127.0.0.1:9050", "DNSPort 127.0.0.1:5400",
            "TransPort 127.0.0.1:9040", "HTTPTunnelPort 127.0.0.1:8118",
            "SNI $sni", "Log notice stdout"
        )
        if (prefs.useBridges && !prefs.customBridgeLine.isNullOrEmpty()) {
            lines.add("UseBridges 1")
            lines.add("Bridge ${prefs.customBridgeLine}")
        }
        listOf("geoip" to "GeoIPFile", "geoip6" to "GeoIPv6File").forEach { file, key ->
            val f = File(torrc.parent, file)
            if (f.exists()) lines.add("$key ${f.absolutePath}")
        }
        FileWriter(torrc).use { it.write(lines.joinToString("\n")) }
    }

    private fun launchTorProcess(torBinary: File, torrc: File) {
        val cmd = arrayOf(torBinary.absolutePath, "-f", torrc.absolutePath)
        Log.i(TAG, "Starting Tor: ${cmd.joinToString(" ")}")
        addLog("Starting Tor...")

        torProcess = ProcessBuilder(*cmd).apply { redirectErrorStream(true) }.start()

        Thread {
            val reader = BufferedReader(InputStreamReader(torProcess?.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    Log.i("Tor", it)
                    addLog(it)
                    when {
                        it.contains("Bootstrapped 100%") -> broadcastStatus("Connected — Tor ready")
                        it.contains("Bootstrapped") -> {
                            Regex("Bootstrapped (\\d+)%").find(it)?.groupValues?.get(1)
                                ?.let { p -> broadcastStatus("Bootstrapping $p%") }
                        }
                    }
                }
            }
        }.start()

        Thread.sleep(1500)
        Log.i(TAG, "Tor process started")
    }

    private fun fail(msg: String) {
        addLog("❌ $msg")
        broadcastStatus("Error: $msg")
        isRunning = false
        disconnect()
    }

    private fun disconnect() {
        isRunning = false
        NexusVpnApplication.prefs.isVpnConnected = false
        broadcastStatus("Disconnected")
        try { stopVpnNative() } catch (_: Exception) {}
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
        try { torProcess?.destroy(); torProcess = null } catch (_: Exception) {}
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        addLog("Disconnected")
    }

    private fun createChannel() {
        val c = NotificationChannel(CHAN_ID, "Nexus VPN", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "VPN connection" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(c)
    }

    private fun notif(s: String): Notification = Notification.Builder(this, CHAN_ID)
        .setContentTitle("Nexus VPN").setContentText(s).setSmallIcon(R.drawable.ic_vpn)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true).build()

    private fun broadcastStatus(s: String) =
        sendBroadcast(Intent(ACTION_STATUS).apply { putExtra(EXTRA_STATUS, s); setPackage(packageName) })

    override fun onBind(i: Intent?): IBinder? = null
    override fun onRevoke() = disconnect()
    override fun onDestroy() { disconnect(); super.onDestroy() }
}
