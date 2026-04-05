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

class NexusVpnService : VpnService() {
    companion object {
        const val TAG = "NexusVpnService"
        const val ACTION_STATUS = "com.nexusvpn.android.STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_SNI = "sni"
        const val EXTRA_BRIDGES = "bridges"
        const val NOTIF_ID = 1
        const val CHAN_ID = "nexus_vpn_channel"

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

    private external fun initVpnNative(tunFd: Int, sniHostname: String, bridgeConfig: String): Boolean
    private external fun stopVpnNative()
    private external fun setSniHostnameNative(hostname: String): Boolean

    private var tunFd: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🔵 Service onCreate")
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "🔵 Service onStartCommand — action: ${intent?.action}")
        when (intent?.action) {
            "CONNECT" -> connect()
            "DISCONNECT" -> disconnect()
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
            Log.i(TAG, "📝 SNI: $sni")

            startForeground(NOTIF_ID, notif("Starting Tor..."))

            // Start Guardian Project TorService
            val torIntent = Intent(this, org.torproject.android.service.TorService::class.java)
            startService(torIntent)
            Log.i(TAG, "✅ Guardian Project TorService started")

            // Give Tor a moment to start
            Thread.sleep(2000)

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
                ?: return run { Log.e(TAG, "❌ establish() returned null"); disconnect() }
            Log.i(TAG, "✅ TUN interface established")

            val bridgeConfig = buildBridgeConfigJson(prefs)
            try {
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
