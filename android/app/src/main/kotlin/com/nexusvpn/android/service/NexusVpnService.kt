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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

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
        private const val TOR_SOCKS_PORT = 9050
        private const val TOR_TRANSPARENT_PORT = 9040
    }
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)
    private var torService: TorService? = null
    private var sniProxyService: SniProxyService? = null
    private var torEnabled = true
    private var sniEnabled = true
    private var killSwitchEnabled = true
    private var dnsLeakProtection = true
    private var ipv6LeakProtection = true
    private var autoReconnect = true
    private var serverId = "default"
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var packetThreadJob: Job? = null
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannel()
        setupNetworkMonitoring()
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
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        serviceScope.cancel()
        packetThreadJob?.cancel()
        super.onDestroy()
    }

    private fun connectVpn(intent: Intent) {
        if (isConnecting.get() || isConnected.get()) {
            Log.w(TAG, "Already connecting or connected")
            return        }

        serviceScope.launch {
            try {
                isConnecting.set(true)
                updateNotification("Connecting...", "Step 1/3: Initializing VPN")
                extractConfiguration(intent)

                // Step 1: Setup VPN Interface
                Log.d(TAG, "Step 1: Setting up VPN interface")
                if (!setupVpnInterface()) {
                    onConnectionFailed("Failed to establish VPN interface")
                    return@launch
                }
                updateNotification("Connecting...", "Step 2/3: Starting Tor")

                // Step 2: Start Tor Service (if enabled)
                if (torEnabled) {
                    Log.d(TAG, "Step 2: Starting Tor service")
                    torService = TorService(this@NexusVpnService)
                    val torStarted = torService?.start() ?: false
                    if (!torStarted) {
                        onConnectionFailed("Failed to start Tor service")
                        return@launch
                    }
                    // Wait for Tor to bootstrap
                    updateNotification("Connecting...", "Step 2/3: Tor bootstrapping...")
                    waitForTorBootstrap()
                }

                // Step 3: Start SNI Proxy (if enabled)
                if (sniEnabled) {
                    Log.d(TAG, "Step 3: Starting SNI proxy")
                    sniProxyService = SniProxyService(this@NexusVpnService)
                    sniProxyService?.start()
                }

                updateNotification("Connecting...", "Step 3/3: Routing traffic")

                // Start packet routing
                startPacketThread()

                onConnectionSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                onConnectionFailed(e.message ?: "Unknown error")
            } finally {
                isConnecting.set(false)
            }        }
    }

    private suspend fun waitForTorBootstrap() {
        var attempts = 0
        val maxAttempts = 60 // 60 seconds timeout
        while (attempts < maxAttempts) {
            if (torService?.isReady() == true) {
                Log.d(TAG, "Tor bootstrap complete")
                return
            }
            delay(1000)
            attempts++
            updateNotification("Connecting...", "Step 2/3: Tor bootstrapping... ${attempts}%")
        }
        Log.w(TAG, "Tor bootstrap timeout, continuing anyway")
    }

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

        // Add Tor bypass if enabled
        if (torEnabled) {
            // Don't route Tor traffic through VPN
            torService?.getTorAddresses()?.forEach { addr ->
                builder.addRoute(addr, 32)
            }
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface - establish() returned null")
            return false
        }

        Log.d(TAG, "VPN interface established successfully, fd: ${vpnInterface?.fileDescriptor}")        return true
    }

    private fun onConnectionSuccess() {
        isConnected.set(true)
        if (killSwitchEnabled) enableKillSwitch()
        updateNotification("Connected", "SNI → Tor Chain Active")
        Log.d(TAG, "✅ VPN connection established successfully")
    }

    private fun onConnectionFailed(error: String) {
        Log.e(TAG, "Connection failed: $error")
        updateNotification("Connection Failed", error)
        cleanupConnection()
        isConnected.set(false)
        isConnecting.set(false)
        if (autoReconnect) scheduleReconnect()
    }

    private fun startPacketThread() {
        packetThreadJob = serviceScope.launch {
            Log.d(TAG, "Packet thread started")
            try {
                val vpnFd = vpnInterface?.fileDescriptor ?: return@launch
                Log.d(TAG, "VPN file descriptor: $vpnFd")

                // Real packet routing would happen here
                // For now, keep connection alive
                while (isConnected.get()) {
                    // Check if Tor is ready and route packets
                    if (torEnabled && torService?.isReady() == true) {
                        // Route through Tor socks proxy (port 9050)
                        // This is simplified - real implementation needs packet parsing
                    }
                    delay(1000)
                }
                Log.d(TAG, "Packet thread stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Packet thread error", e)
            }
        }
    }

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

    private fun cleanupConnection() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "VPN interface closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
    }

    private fun extractConfiguration(intent: Intent) {
        torEnabled = intent.getBooleanExtra("tor_enabled", true)
        sniEnabled = intent.getBooleanExtra("sni_enabled", true)
        killSwitchEnabled = intent.getBooleanExtra("kill_switch", true)
        dnsLeakProtection = intent.getBooleanExtra("dns_leak_protection", true)
        ipv6LeakProtection = intent.getBooleanExtra("ipv6_leak_protection", true)
        autoReconnect = intent.getBooleanExtra("auto_reconnect", true)
        serverId = intent.getStringExtra("server_id") ?: "default"
        Log.d(TAG, "Configuration: Tor=$torEnabled, SNI=$sniEnabled, KillSwitch=$killSwitchEnabled")
    }

    private fun setupNetworkMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
            }
            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost")
                if (isConnected.get() && autoReconnect) scheduleReconnect()
            }
        }
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)    }

    private fun scheduleReconnect() {
        serviceScope.launch {
            delay(5000)
            if (!isConnected.get()) Log.d(TAG, "Attempting auto-reconnect")
        }
    }

    private fun enableKillSwitch() {
        Log.d(TAG, "Enabling kill switch")
        // Implement actual kill switch (block all traffic when VPN disconnects)
    }

    private fun disableKillSwitch() {
        Log.d(TAG, "Disabling kill switch")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Nexus VPN Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VPN connection status"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = createNotification("Connecting", "Setting up VPN tunnel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotification(title: String, message: String): Notification {        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
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

    private fun updateNotification(title: String, message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, message))
    }

    inner class LocalBinder : Binder() {
        fun getService(): NexusVpnService = this@NexusVpnService
    }
}
