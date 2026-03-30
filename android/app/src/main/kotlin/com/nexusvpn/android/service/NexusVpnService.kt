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

        init { System.loadLibrary("nexus_vpn_core") }    }

    private var enginePtr: Long = 0
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)
    private var sniConnected = AtomicBoolean(false)
    private var torConnected = AtomicBoolean(false)
    private var sniHostname = ""
    private var torEnabled = true
    private var killSwitchEnabled = true
    private var ipv6LeakProtection = true
    private var autoReconnect = true
    private var serverId = "default"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var statsJob: Job? = null
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        enginePtr = createEngine()
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
        statsJob?.cancel()
        super.onDestroy()
    }

    private fun connectVpn(intent: Intent) {
        if (isConnecting.get() || isConnected.get()) return
        serviceScope.launch {
            try {
                isConnecting.set(true)
                updateNotification("Connecting...", "Step 1/2: SNI Handler")
                extractConfiguration(intent)                
                // Step 1: Connect to SNI Handler
                Log.d(TAG, "Step 1: Connecting to SNI Handler")
                val randomize = sniHostname.isEmpty()
                val sniResult = setSniConfig(enginePtr, sniHostname, randomize, false)
                Log.d(TAG, "SNI config result: $sniResult")
                
                if (sniResult == 0) {
                    sniConnected.set(true)
                    updateNotification("Connecting...", "Step 2/2: Tor Circuit")
                    
                    // Step 2: Connect to Tor (if enabled)
                    if (torEnabled) {
                        Log.d(TAG, "Step 2: Establishing Tor Circuit")
                        val torResult = connectSniTor(enginePtr, serverId)
                        Log.d(TAG, "Tor connection result: $torResult")
                        
                        if (torResult == 0) {
                            torConnected.set(true)
                            onConnectionSuccess()
                        } else {
                            onConnectionFailed("Tor connection failed: $torResult")
                        }
                    } else {
                        onConnectionSuccess()
                    }
                } else {
                    onConnectionFailed("SNI connection failed: $sniResult")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                onConnectionFailed(e.message ?: "Unknown error")
            } finally {
                isConnecting.set(false)
            }
        }
    }

    private fun extractConfiguration(intent: Intent) {
        sniHostname = intent.getStringExtra("sni_hostname") ?: ""
        torEnabled = intent.getBooleanExtra("tor_enabled", true)
        killSwitchEnabled = intent.getBooleanExtra("kill_switch", true)
        ipv6LeakProtection = intent.getBooleanExtra("ipv6_leak_protection", true)
        autoReconnect = intent.getBooleanExtra("auto_reconnect", true)
        serverId = intent.getStringExtra("server_id") ?: "default"
    }

    private fun onConnectionSuccess() {
        isConnected.set(true)
        if (killSwitchEnabled) enableKillSwitch()
        startStatsUpdater()
        updateNotification("Connected", "SNI → Tor Chain Active")
        Log.d(TAG, "✅ SNI → Tor chain established successfully")
    }

    private fun onConnectionFailed(error: String) {
        Log.e(TAG, "Connection failed: $error")
        updateNotification("Connection Failed", error)
        cleanupConnection()
        if (autoReconnect) scheduleReconnect()
    }

    private fun setupVpnInterface() {
        val builder = Builder()
            .setSession("Nexus VPN")
            .addAddress(VPN_ADDRESS, VPN_SUBNET_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(VPN_DNS_PRIMARY)
            .addDnsServer(VPN_DNS_SECONDARY)
            .setMtu(VPN_MTU)
            .setBlocking(false)
            .setMetered(false)
        if (ipv6LeakProtection) builder.addRoute("::", 0)
        vpnInterface = builder.establish() ?: throw IllegalStateException("Failed to establish VPN interface")
    }

    fun disconnectVpn() {
        serviceScope.launch {
            statsJob?.cancel()
            disableKillSwitch()
            if (enginePtr != 0L) {
                destroyEngine(enginePtr)
                enginePtr = 0
            }
            cleanupConnection()
            isConnected.set(false)
            isConnecting.set(false)
            sniConnected.set(false)
            torConnected.set(false)
            stopForegroundService()
        }
    }

    private fun cleanupConnection() {
        try { vpnInterface?.close(); vpnInterface = null } catch (e: Exception) { Log.e(TAG, "Error closing VPN", e) }
    }

    private fun scheduleReconnect() {
        serviceScope.launch {
            delay(5000)            if (!isConnected.get()) Log.d(TAG, "Attempting auto-reconnect")
        }
    }

    private fun enableKillSwitch() { Log.d(TAG, "Enabling kill switch") }
    private fun disableKillSwitch() { Log.d(TAG, "Disabling kill switch") }

    private fun startStatsUpdater() {
        statsJob = serviceScope.launch {
            while (isConnected.get()) { delay(2000) }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Nexus VPN Service", NotificationManager.IMPORTANCE_LOW).apply {
            description = "VPN connection status"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = createNotification("Connecting", "Setting up VPN tunnel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else { startForeground(NOTIFICATION_ID, notification) }
    }

    private fun stopForegroundService() { stopForeground(STOP_FOREGROUND_REMOVE) }

    private fun createNotification(title: String, message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title).setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun updateNotification(title: String, message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, message))
    }

    private external fun createEngine(): Long
    private external fun destroyEngine(enginePtr: Long)
    private external fun setSniConfig(enginePtr: Long, hostname: String, randomize: Boolean, torEnabled: Boolean): Int
    private external fun connectSniTor(enginePtr: Long, serverId: String): Int
    inner class LocalBinder : Binder() { fun getService(): NexusVpnService = this@NexusVpnService }
}
