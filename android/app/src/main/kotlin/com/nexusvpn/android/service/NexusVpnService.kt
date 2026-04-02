package com.nexusvpn.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexusvpn.android.MainActivity
import com.nexusvpn.android.NexusVpnApplication
import com.nexusvpn.android.R
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean


class NexusVpnService : VpnService() {
    companion object {
        private const val TAG = "NexusVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "nexus_vpn_channel"
        private const val NOTIFICATION_ID = 1
        private const val TUN_ADDRESS = "10.8.0.2"
        private const val TUN_PREFIX = 32
        private const val TUN_MTU = 1500

        init { System.loadLibrary("nexus_vpn") }

        external fun initVpnNative(tunFd: Int, sniHostname: String): Boolean
        external fun runPacketLoopNative(): Boolean
        external fun stopVpnNative()
        external fun setSniHostnameNative(hostname: String): Boolean
        external fun getTrafficStatsSentNative(): Long
        external fun getTrafficStatsRecvNative(): Long
    }

    private var tunFd: ParcelFileDescriptor? = null
    private var packetLoopThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> startVpn()
            "DISCONNECT" -> stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (isRunning.get()) return
        Log.i(TAG, "Starting VPN...")

        try {
            val sniHost = NexusVpnApplication.prefs.sniHostname ?: "www.cloudflare.com"

            tunFd = Builder()
                .addAddress(TUN_ADDRESS, TUN_PREFIX)
                .addDnsServer("1.1.1.1")
                .addDnsServer("1.0.0.1")
                .addRoute("0.0.0.0", 0)
                .setSession("Nexus VPN")
                .setMtu(TUN_MTU)
                .setBlocking(true)
                .establish()
                ?: throw IOException("VpnService.Builder.establish() returned null")

            if (!initVpnNative(tunFd?.fd ?: return, sniHost)) {
                stopVpn()
                updateNotification("Failed to initialize VPN")
                return
            }

            isRunning.set(true)
            updateNotification("Connected via Tor")
            packetLoopThread = Thread({ runPacketLoopNative() }, "packet-loop").apply { start() }

            Log.i(TAG, "VPN started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
            updateNotification("VPN connection failed")
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN...")
        isRunning.set(false)
        stopVpnNative()
        packetLoopThread?.interrupt()
        packetLoopThread = null
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Nexus VPN",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "VPN connection status" }
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Nexus VPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_vpn)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
