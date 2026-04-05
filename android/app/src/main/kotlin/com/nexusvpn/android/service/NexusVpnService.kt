package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexusvpn.android.MainActivity
import com.nexusvpn.android.R
import com.nexusvpn.android.NexusVpnApplication
import org.torproject.jni.TorService

/**
 * Nexus VPN: SNI + Tor
 *
 * Architecture (InviZible Pro style):
 * 1. Guardian Project TorService starts Tor → SOCKS:9050
 * 2. VPN TUN interface captures all traffic
 * 3. Rust native code intercepts TCP, rewrites SNI in TLS ClientHello
 * 4. Modified traffic forwarded to Tor SOCKS proxy
 * 5. Tor exits to real destination, TLS shows decoy SNI
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
    private var isRunning = false
    private var torReceiver: BroadcastReceiver? = null
    private var torReady = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

            startForeground(NOTIF_ID, notif("Starting Tor..."))

            // Register Tor status receiver
            registerTorReceiver()

            // Start Guardian Project TorService
            val torIntent = Intent(this, TorService::class.java)
            ContextCompat.startForegroundService(this, torIntent)
            addLog("TorService started")

            // Wait for Tor to bootstrap (poll torReady flag)
            addLog("Waiting for Tor bootstrap...")
            for (i in 1..60) {
                Thread.sleep(1000)
                if (torReady) {
                    addLog("✅ Tor ready — SOCKS:9050 active")
                    break
                }
                if (i % 10 == 0) addLog("Tor bootstrap: ${i}s elapsed")
            }

            if (!torReady) {
                return fail("Tor bootstrap timeout")
            }

            // Establish VPN TUN interface
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
            addLog("TUN interface up: 10.8.0.2/24")

            // Initialize native code: TUN → SNI rewrite → Tor SOCKS
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
            addLog("Flow: App → SNI($sni) → Tor → Internet")

        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            addLog("❌ ERROR: ${e.message}")
            fail(e.message ?: "Unknown")
        }
    }

    private fun registerTorReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(TorService.ACTION_STATUS)
                addAction(TorService.ACTION_ERROR)
            }
            torReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val status = intent?.getStringExtra(TorService.EXTRA_STATUS)
                        ?: return
                    addLog(status)

                    when {
                        status.contains("100%", ignoreCase = true) -> {
                            torReady = true
                            addLog("✅ Tor bootstrapped")
                        }
                        status.contains("Bootstrapped", ignoreCase = true) -> {
                            val pct = Regex("(\\d+)%").find(status)?.groupValues?.get(1)
                            pct?.let { addLog("Tor: ${it}%") }
                        }
                        status.contains("error", ignoreCase = true) ->
                            addLog("⚠️ Tor: $status")
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(torReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                registerReceiver(torReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tor receiver failed", e)
        }
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

        try { stopService(Intent(this, TorService::class.java)) } catch (_: Exception) {}
        try { torReceiver?.let { unregisterReceiver(it) }; torReceiver = null } catch (_: Exception) {}
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
