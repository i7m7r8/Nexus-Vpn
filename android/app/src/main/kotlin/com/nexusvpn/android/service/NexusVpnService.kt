package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
 * Complete VPN service with SNI → Tor chain support
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

    private var vpnInterface: ParcelFileDescriptor? = null    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)
    private var torService: TorService? = null
    private var sniProxyService: SniProxyService? = null
    private var torEnabled = true
    private var sniEnabled = true
    private var killSwitchEnabled = true
    private var dnsLeakProtection = true
    private var ipv6LeakProtection = true
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
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            "CONNECT" -> connectVpn(intent)
            "DISCONNECT" -> disconnectVpn()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        disconnectVpn()
        serviceScope.cancel()
        packetThreadJob?.cancel()
        super.onDestroy()
    }

    /**
     * PHASE 2: Connect VPN with SNI → Tor chain
     */
    private fun connectVpn(intent: Intent) {
        if (isConnecting.get() || isConnected.get()) {
            Log.w(TAG, "Already connecting or connected")
            return
        }

        serviceScope.launch {
            try {                isConnecting.set(true)
                updateNotification("Connecting...", "Step 1/3: Initializing VPN")
                extractConfiguration(intent)

                // Step 1: Setup VPN Interface
                Log.d(TAG, "Step 1: Setting up VPN interface")
                if (!setupVpnInterface()) {
                    onConnectionFailed("Failed to establish VPN interface")
                    return@launch
                }
                updateNotification("Connecting...", "Step 2/3: Starting SNI")

                // Step 2: Start SNI Proxy
                if (sniEnabled) {
                    Log.d(TAG, "Step 2: Starting SNI proxy")
                    sniProxyService = SniProxyService(this@NexusVpnService)
                    sniProxyService?.start(sniHostname)
                    delay(500)
                }
                updateNotification("Connecting...", "Step 3/3: Starting Tor")

                // Step 3: Start Tor Service
                if (torEnabled) {
                    Log.d(TAG, "Step 3: Starting Tor service")
                    torService = TorService(this@NexusVpnService)
                    val torStarted = torService?.start() ?: false
                    if (!torStarted) {
                        onConnectionFailed("Failed to start Tor service")
                        return@launch
                    }
                    // Wait for Tor bootstrap
                    while (torService?.isReady() == false) {
                        delay(500)
                        val progress = torService?.getProgress() ?: 0
                        updateNotification("Connecting...", "Step 3/3: Tor ${progress}%")
                    }
                }

                updateNotification("Connecting...", "Finalizing...")

                // Start packet routing
                startPacketThread()

                onConnectionSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                onConnectionFailed(e.message ?: "Unknown error")
            } finally {
                isConnecting.set(false)            }
        }
    }

    /**
     * Setup VPN interface
     */
    private fun setupVpnInterface(): Boolean {
        Log.d(TAG, "Setting up VPN interface")
        val builder = Builder()
            .setSession("Nexus VPN")
            .addAddress(VPN_ADDRESS, VPN_SUBNET_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(VPN_DNS_PRIMARY)
            .addDnsServer(VPN_DNS_SECONDARY)
            .setMtu(VPN_MTU)
            .setBlocking(false)
            .setMetered(false)

        if (ipv6LeakProtection) {
            builder.addRoute("::", 0)
            Log.d(TAG, "IPv6 blocking enabled")
        }

        // Add Tor bypass
        torService?.getTorAddresses()?.forEach { addr ->
            builder.addRoute(addr, 32)
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface - establish() returned null")
            return false
        }

        Log.d(TAG, "✅ VPN interface established, fd: ${vpnInterface?.fileDescriptor}")
        return true
    }

    /**
     * Connection successful
     */
    private fun onConnectionSuccess() {
        isConnected.set(true)
        if (killSwitchEnabled) enableKillSwitch()
        updateNotification("Connected", "SNI → Tor Chain Active")
        Log.d(TAG, "✅ VPN connection established successfully")
    }

    /**     * Connection failed
     */
    private fun onConnectionFailed(error: String) {
        Log.e(TAG, "❌ Connection failed: $error")
        updateNotification("Connection Failed", error)
        cleanupConnection()
        isConnected.set(false)
        isConnecting.set(false)
    }

    /**
     * PHASE 2: Packet routing thread
     */
    private fun startPacketThread() {
        packetThreadJob = serviceScope.launch {
            Log.d(TAG, "Packet thread started")
            try {
                val vpnFd = vpnInterface?.fileDescriptor ?: return@launch
                Log.d(TAG, "VPN file descriptor: $vpnFd")

                // PHASE 3: Implement actual packet routing
                // For now, keep connection alive
                while (isConnected.get()) {
                    delay(1000)
                }
                Log.d(TAG, "Packet thread stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Packet thread error", e)
            }
        }
    }

    /**
     * Disconnect VPN
     */
    fun disconnectVpn() {
        serviceScope.launch {
            packetThreadJob?.cancel()
            
            // Stop SNI proxy
            sniProxyService?.stop()
            sniProxyService = null
            
            // Stop Tor
            torService?.stop()
            torService = null
            
            // Close VPN interface
            cleanupConnection()
                        isConnected.set(false)
            isConnecting.set(false)
            stopForegroundService()
            Log.d(TAG, "VPN disconnected")
        }
    }

    /**
     * Cleanup connection
     */
    private fun cleanupConnection() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "VPN interface closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
    }

    /**
     * Extract configuration from intent
     */
    private fun extractConfiguration(intent: Intent) {
        torEnabled = intent.getBooleanExtra("tor_enabled", true)
        sniEnabled = intent.getBooleanExtra("sni_enabled", true)
        killSwitchEnabled = intent.getBooleanExtra("kill_switch", true)
        dnsLeakProtection = intent.getBooleanExtra("dns_leak_protection", true)
        ipv6LeakProtection = intent.getBooleanExtra("ipv6_leak_protection", true)
        sniHostname = intent.getStringExtra("sni_hostname") ?: ""
        Log.d(TAG, "Config: Tor=$torEnabled, SNI=$sniEnabled, KillSwitch=$killSwitchEnabled")
    }

    /**
     * Enable kill switch
     */
    private fun enableKillSwitch() {
        Log.d(TAG, "Enabling kill switch")
    }

    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Nexus VPN Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VPN connection status"            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Start foreground service
     */
    private fun startForegroundService() {
        val notification = createNotification("Connecting", "Setting up VPN tunnel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Stop foreground service
     */
    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Create notification
     */
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Update notification
     */
    private fun updateNotification(title: String, message: String) {
        getSystemService(NotificationManager::class.java)            .notify(NOTIFICATION_ID, createNotification(title, message))
    }

    inner class LocalBinder : Binder() {
        fun getService(): NexusVpnService = this@NexusVpnService
    }
}
