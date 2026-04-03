package com.nexusvpn.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexusvpn.android.MainActivity
import com.nexusvpn.android.NexusVpnApplication
import com.nexusvpn.android.R

class NexusVpnService : VpnService() {
    companion object {
        private const val TAG = "NexusVpnService"
        private const val CHAN_ID = "nexus_vpn"
        private const val NOTIF_ID = 1
        private const val TUN_ADDR = "10.8.0.2"
        private const val TUN_PREFIX = 32
        private const val TUN_MTU = 1500

        init { System.loadLibrary("nexus_vpn") }

        @JvmStatic external fun initVpnNative(tunFd: Int, sniHostname: String, bridgeConfig: String): Boolean
        @JvmStatic external fun stopVpnNative()
        @JvmStatic external fun setSniHostnameNative(hostname: String): Boolean

        fun setSniHostnameNative(hostname: String) {
            try { setSniHostnameNative(hostname) } catch (_: UnsatisfiedLinkError) {}
        }
    }

    private var tunFd: android.os.ParcelFileDescriptor? = null

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
                try { companion.setSniHostnameNative(it) } catch (_: Exception) {}
            }
        }
        return START_STICKY
    }

    private fun connect() {
        try {
            val prefs = NexusVpnApplication.prefs
            val sni = prefs.sniHostname ?: "cdn.cloudflare.net"

            // Build bridge config JSON
            val bridgeConfig = if (prefs.useBridges) {
                val bridgeLine = prefs.customBridgeLine ?: ""
                """{"use_bridges":true,"bridge_type":"${prefs.bridgeType}","custom_bridge_line":"$bridgeLine"}"""
            } else {
                "{}"
            }

            val builder = Builder()
                .addAddress(TUN_ADDR, TUN_PREFIX)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .setSession("Nexus VPN")
                .setMtu(TUN_MTU)

            // Kill switch: block all traffic when VPN tunnel is not ready
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && prefs.killSwitch) {
                builder.setBlocking(true)
                Log.i(TAG, "🔒 Kill switch enabled (setBlocking)")
            }

            tunFd = builder.establish()
                ?: return run { Log.e(TAG, "establish() returned null"); disconnect() }

            if (!initVpnNative(tunFd!!.fd, sni, bridgeConfig)) {
                Log.e(TAG, "initVpnNative returned false")
                disconnect()
                return
            }

            val statusMsg = when {
                prefs.useBridges && prefs.killSwitch -> "Bridge + Tor + Kill Switch"
                prefs.useBridges -> "Connected via Bridge + Tor"
                prefs.killSwitch -> "Connected via Tor + Kill Switch"
                else -> "Connected via Tor"
            }
            startForeground(NOTIF_ID, notif(statusMsg))
            NexusVpnApplication.prefs.isVpnConnected = true
            Log.i(TAG, "VPN started (SNI: $sni, Bridges: ${prefs.useBridges}, KillSwitch: ${prefs.killSwitch})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            disconnect()
        }
    }

    private fun disconnect() {
        NexusVpnApplication.prefs.isVpnConnected = false
        try { stopVpnNative() } catch (_: Exception) {}
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
        val stopType = if (Build.VERSION.SDK_INT >= 29) {
            STOP_FOREGROUND_PASS
        } else {
            @Suppress("DEPRECATION")
            false
        }
        try { stopForeground(stopType) } catch (_: Exception) {}
        stopSelf()
        Log.i(TAG, "VPN disconnected")
    }

    private fun createChannel() {
        val chan = NotificationChannel(CHAN_ID, "Nexus VPN", NotificationManager.IMPORTANCE_LOW).apply {
            description = "VPN connection status"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
    }

    private fun notif(text: String) = NotificationCompat.Builder(this, CHAN_ID)
        .setContentTitle("Nexus VPN")
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_vpn)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { disconnect(); super.onDestroy() }
}
