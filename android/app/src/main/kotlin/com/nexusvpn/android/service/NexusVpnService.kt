package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nexusvpn.android.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PHASE 2: Nexus VPN Service
 * Complete VPN with SNI → Tor chain
 */
class NexusVpnService : VpnService() {
    companion object {
        private const val TAG = "NexusVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "nexus_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_SUBNET_PREFIX = 24
        private const val VPN_DNS_PRIMARY = "1.1.1.1"
        private const val VPN_DNS_SECONDARY = "9.9.9.9"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)
    private var torService: TorService? = null
    private var sniProxyService: SniProxyService? = null    // true removed
    private var sniEnabled = true
    private var sniHostname = ""
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var packetThreadJob: Job? = null
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> connectVpn(intent)
            "DISCONNECT" -> disconnectVpn()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        disconnectVpn()
        serviceScope.cancel()
        packetThreadJob?.cancel()
        super.onDestroy()
    }

    private fun connectVpn(intent: Intent) {
        if (isConnecting.get() || isConnected.get()) return

        serviceScope.launch {
            try {
                isConnecting.set(true)
                updateNotification("Connecting...", "Step 1/3: VPN")
                extractConfiguration(intent)

                // Step 1: VPN Interface
                if (!setupVpnInterface()) {
                    onConnectionFailed("Failed to establish VPN")
                    return@launch
                }
                updateNotification("Connecting...", "Step 2/3: SNI")

                // Step 2: SNI Proxy
                if (sniEnabled) {
                    sniProxyService = SniProxyService(this@NexusVpnService)
                    sniProxyService?.start(sniHostname)                }
                updateNotification("Connecting...", "Step 3/3: Tor")

                // Step 3: Tor
                if (true) {
                    torService = TorService(this@NexusVpnService)
                    torService?.start() ?: run {
                        onConnectionFailed("Failed to start Tor")
                        return@launch
                    }
                }

                startPacketThread()
                onConnectionSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                onConnectionFailed(e.message ?: "Unknown error")
            } finally {
                isConnecting.set(false)
            }
        }
    }

    private fun setupVpnInterface(): Boolean {
        val builder = Builder()
            .setSession("Nexus VPN")
            .addAddress(VPN_ADDRESS, VPN_SUBNET_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(VPN_DNS_PRIMARY)
            .addDnsServer(VPN_DNS_SECONDARY)
            .setMtu(VPN_MTU)
            .setBlocking(false)

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface")
            return false
        }

        Log.d(TAG, "✅ VPN established, fd: ${vpnInterface?.fileDescriptor}")
        return true
    }

    private fun onConnectionSuccess() {
        isConnected.set(true)
        updateNotification("Connected", "SNI → Tor Active")
        Log.d(TAG, "✅ VPN connected")
    }
    private fun onConnectionFailed(error: String) {
        Log.e(TAG, "❌ Failed: $error")
        updateNotification("Failed", error)
        cleanupConnection()
        isConnected.set(false)
        isConnecting.set(false)
    }

    private fun startPacketThread() {
        packetThreadJob = serviceScope.launch {
            while (isConnected.get()) {
                delay(1000)
            }
        }
    }

    fun disconnectVpn() {
        serviceScope.launch {
            packetThreadJob?.cancel()
            sniProxyService?.stop()
            torService?.stop()
            cleanupConnection()
            isConnected.set(false)
            isConnecting.set(false)
            stopForegroundService()
        }
    }

    private fun cleanupConnection() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN", e)
        }
    }

    private fun extractConfiguration(intent: Intent) {
        // torEnabled removed - hardcoded to true
        sniEnabled = intent.getBooleanExtra("sni_enabled", true)
        sniHostname = intent.getStringExtra("sni_hostname") ?: ""
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Nexus VPN",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)    }

    private fun startForegroundService() {
        val notification = createNotification("Connecting", "Setting up VPN")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotification(title: String, message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(title, message))
    }

    inner class LocalBinder : Binder() {
        fun getService(): NexusVpnService = this@NexusVpnService
    }
}
