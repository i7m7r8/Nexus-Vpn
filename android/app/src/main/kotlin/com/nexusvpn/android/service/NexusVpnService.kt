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

/**
 * Nexus VPN: InviZible Pro style SNI → Tor
 *
 * Based on InviZible Pro 7.4.0 source analysis:
 * 1. Extract libtor.so from APK native libs to filesDir
 * 2. Run libtor.so -f torrc as subprocess (Runtime.exec)
 * 3. Tor reads torrc with SNI config, logs to stdout
 * 4. Parse stdout for "Bootstrapped 100%"
 * 5. Establish TUN, route through Tor SOCKS:9050
 * 6. Rust native code rewrites SNI in TLS ClientHello
 */
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
        Log.i(TAG, "Native library loaded")
    }

    private external fun initVpnNative(tunFd: Int, sniHostname: String, socksPort: Int): Boolean
    private external fun stopVpnNative()

    private var tunFd: ParcelFileDescriptor? = null
    private var torProcess: Process? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action}")
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
            addLog("═══ Nexus VPN Starting ═══")
            addLog("SNI: $sni")

            startForeground(NOTIF_ID, notif("Configuring Tor..."))

            // === STEP 1: Extract libtor.so from native libs ===
            val torDir = File(filesDir, "tor").apply { mkdirs() }
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val torSrc = File(nativeLibDir, "libtor.so")
            val torDest = File(torDir, "libtor.so")

            if (!torSrc.exists()) {
                return fail("libtor.so not found (checked $nativeLibDir)")
            }

            if (!torDest.exists() || torDest.length() != torSrc.length()) {
                torSrc.inputStream().use { it ->
                    torDest.outputStream().use { out -> it.copyTo(out) }
                }
                torDest.setExecutable(true, false)
                addLog("libtor.so extracted (${torDest.length() / 1024 / 1024}MB)")
            }

            // Copy geoip
            listOf("geoip", "geoip6").forEach { name ->
                try {
                    assets.open(name).use { it ->
                        File(torDir, name).outputStream().use { out -> it.copyTo(out) }
                    }
                } catch (_: Exception) {}
            }

            // === STEP 2: Write torrc with SNI ===
            val dataDir = File(torDir, "data").apply { mkdirs() }
            val torrc = File(torDir, "torrc")
            writeTorrc(torrc, dataDir, sni, prefs)
            addLog("torrc written: SNI=$sni")

            // === STEP 3: Start Tor subprocess ===
            addLog("Starting Tor (SNI: $sni)...")
            startForeground(NOTIF_ID, notif("Starting Tor (SNI: $sni)..."))

            val env = arrayOf("LD_LIBRARY_PATH=$nativeLibDir")
            val cmd = arrayOf(torDest.absolutePath, "-f", torrc.absolutePath)
            Log.i(TAG, "Tor cmd: ${cmd.joinToString(" ")}")
            addLog("Executing: libtor.so -f torrc")

            torProcess = Runtime.getRuntime().exec(cmd, env)

            // Capture stdout
            val bootLatch = java.util.concurrent.CountDownLatch(1)
            Thread {
                val reader = BufferedReader(InputStreamReader(torProcess?.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        Log.i("Tor", it)
                        addLog(it)
                        if (it.contains("Bootstrapped 100%") || it.contains("Done")) {
                            bootLatch.countDown()
                            addLog("✅ Tor bootstrapped")
                        } else if (it.contains("Bootstrapped")) {
                            val pct = Regex("(\\d+)%").find(it)?.groupValues?.get(1)
                            pct?.let { p -> addLog("Tor: ${p}%") }
                        }
                    }
                }
            }.start()

            // Wait for bootstrap (60s max)
            addLog("Waiting for Tor bootstrap...")
            val ready = bootLatch.await(60, java.util.concurrent.TimeUnit.SECONDS)
            if (!ready) return fail("Tor bootstrap timeout (60s)")

            addLog("✅ Tor ready — SOCKS:9050 active")

            // === STEP 4: Establish VPN TUN ===
            addLog("Establishing VPN tunnel...")
            startForeground(NOTIF_ID, notif("Establishing VPN tunnel..."))

            val builder = Builder()
                .setSession("Nexus VPN")
                .addAddress("10.8.0.2", 24)
                .addDnsServer("127.0.0.1")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && prefs.killSwitch)
                builder.setBlocking(true)

            tunFd = builder.establish() ?: return fail("VPN establish failed")
            addLog("TUN up: 10.8.0.2/24")

            // === STEP 5: Native SNI + Tor routing ===
            addLog("Initializing SNI + Tor routing...")
            try {
                if (!initVpnNative(tunFd!!.fd, sni, 9050))
                    return fail("Native routing init failed")
                addLog("✅ SNI obfuscation active")
            } catch (e: Exception) {
                return fail("Native lib error: ${e.message}")
            }

            val msg = "✅ Connected: SNI($sni) → Tor"
            startForeground(NOTIF_ID, notif(msg))
            NexusVpnApplication.prefs.isVpnConnected = true
            broadcastStatus(msg)
            addLog("═══ VPN Connected ═══")
            addLog("Route: App → SNI($sni) → Tor → Internet")

        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            addLog("❌ ERROR: ${e.message}")
            fail(e.message ?: "Unknown")
        }
    }

    private fun writeTorrc(torrc: File, dataDir: File, sni: String, prefs: com.nexusvpn.android.data.Prefs) {
        val lines = mutableListOf(
            "RunAsDaemon 0",
            "AvoidDiskWrites 1",
            "DataDirectory ${dataDir.absolutePath}",
            "SOCKSPort 127.0.0.1:9050",
            "DNSPort 127.0.0.1:5400",
            "TransPort 127.0.0.1:9040",
            "SNI $sni",
            "Log notice stdout"
        )
        if (prefs.useBridges && !prefs.customBridgeLine.isNullOrEmpty()) {
            lines.add("UseBridges 1")
            lines.add("Bridge ${prefs.customBridgeLine}")
        }
        listOf("geoip" to "GeoIPFile", "geoip6" to "GeoIPv6File").forEach { (file, key) ->
            val f = File(torrc.parent, file)
            if (f.exists()) lines.add("$key ${f.absolutePath}")
        }
        FileWriter(torrc).use { it.write(lines.joinToString("\n")) }
    }

    private fun fail(msg: String) {
        addLog("❌ $msg")
        broadcastStatus("Error: $msg")
        isRunning = false
        disconnect()
    }

    private fun disconnect() {
        Log.i(TAG, "disconnect()")
        isRunning = false
        NexusVpnApplication.prefs.isVpnConnected = false
        broadcastStatus("Disconnected")
        try { stopVpnNative() } catch (_: Exception) {}
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
        try { torProcess?.destroy(); torProcess?.waitFor(); torProcess = null } catch (_: Exception) {}
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

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onRevoke() = disconnect()
    override fun onDestroy() { disconnect(); super.onDestroy() }
}
