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
import java.io.File
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
    }

    private external fun initVpnNative(tunFd: Int, sniHostname: String, bridgeConfig: String): Boolean
    private external fun stopVpnNative()

    private var tunFd: ParcelFileDescriptor? = null
    private var isRunning = false
    private var torReceiver: BroadcastReceiver? = null

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
            addLog("Starting Nexus VPN...")
            addLog("SNI: $sni")

            startForeground(NOTIF_ID, notif("Starting Tor..."))

            // Setup Tor log receiver
            setupTorLogReceiver()

            // Start Guardian Project TorService
            val torIntent = Intent(this, org.torproject.jni.TorService::class.java)
            ContextCompat.startForegroundService(this, torIntent)
            addLog("TorService starting...")

            // Wait for Tor to bootstrap (~10s)
            Thread.sleep(10000)

            // Build VPN interface
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

            // Init native routing to SOCKS proxy on 127.0.0.1:9050
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

    private fun setupTorLogReceiver() {
        // Guardian Project TorService broadcasts logs
        try {
            val filter = IntentFilter("org.torproject.android.intent.ACTION_STATUS")
            torReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val msg = intent?.getStringExtra("status")
                    if (msg != null) {
                        addLog(msg)
                        if (msg.contains("Bootstrapped 100%")) {
                            broadcastStatus("Connected — Tor ready")
                        } else if (msg.contains("Bootstrapped")) {
                            val pct = Regex("(\\d+)%").find(msg)?.groupValues?.get(1)
                            pct?.let { broadcastStatus("Bootstrapping ${it}%") }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(torReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                registerReceiver(torReceiver, filter)
            }
            addLog("Tor log receiver registered")
        } catch (e: Exception) {
            Log.w(TAG, "Tor log receiver failed", e)
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
        try { stopService(Intent(this, org.torproject.jni.TorService::class.java)) } catch (_: Exception) {}
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
