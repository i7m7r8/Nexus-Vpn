// ============================================================================
// NEXUS VPN - SNI → Tor Chain Implementation (MASTERPLAN CORE FEATURE)
// ============================================================================
// Connection Flow:
// 1. Device → SNI Handler (TLS Client Hello spoofing)
// 2. SNI → Tor Network (Arti client circuit)
// 3. Tor → Internet (Multi-hop anonymous routing)
// ============================================================================

package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexusvpn.android.MainActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress

class NexusVpnService : VpnService() {

    companion object {
        private const val TAG = "NexusVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "nexus_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Rust Core JNI Functions
        init {
            System.loadLibrary("nexus_vpn_core")
        }
        
        @JvmStatic
        private external fun nexus_vpn_create_engine(): Long
        @JvmStatic        private external fun nexus_vpn_destroy_engine(enginePtr: Long)
        @JvmStatic
        private external fun nexus_vpn_set_sni_config(
            enginePtr: Long,
            sniHostname: String,
            randomize: Boolean,
            torEnabled: Boolean
        ): Int
        @JvmStatic
        private external fun nexus_vpn_connect_sni_tor(
            enginePtr: Long,
            serverId: String
        ): Int
        @JvmStatic
        private external fun nexus_vpn_get_stats(enginePtr: Long): String?
        @JvmStatic
        private external fun nexus_vpn_kill_switch_enable(enginePtr: Long): Int
        @JvmStatic
        private external fun nexus_vpn_kill_switch_disable(enginePtr: Long): Int
    }

    // VPN State
    private var enginePtr: Long = 0
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isConnected = false
    private var sniHostname = ""
    private var torEnabled = false
    private var killSwitchEnabled = true
    
    // Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Network monitoring
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize Rust VPN engine
        enginePtr = nexus_vpn_create_engine()
        Log.d(TAG, "Rust engine initialized: $enginePtr")
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Start command received: ${intent?.action}")
        
        when (intent?.action) {            ACTION_CONNECT -> connectVpn(intent)
            ACTION_DISCONNECT -> disconnectVpn()
            ACTION_UPDATE_CONFIG -> updateConfig(intent)
        }
        
        return START_STICKY
    }

    // ============================================================================
    // ======================== SNI → TOR CHAIN CONNECTION ========================
    // ============================================================================
    
    private fun connectVpn(intent: Intent) {
        serviceScope.launch {
            try {
                // STEP 1: Show connecting notification
                updateNotification("Connecting...", "Establishing SNI → Tor chain")
                
                // STEP 2: Extract configuration from intent
                sniHostname = intent.getStringExtra("sni_hostname") ?: ""
                torEnabled = intent.getBooleanExtra("tor_enabled", true)
                val serverId = intent.getStringExtra("server_id") ?: "default"
                
                Log.d(TAG, "Config: SNI=$sniHostname, Tor=$torEnabled, Server=$serverId")
                
                // STEP 3: Configure Rust core with SNI settings
                val randomize = sniHostname.isEmpty()
                val configResult = nexus_vpn_set_sni_config(
                    enginePtr,
                    sniHostname,
                    randomize,
                    torEnabled
                )
                Log.d(TAG, "SNI config result: $configResult")
                
                // STEP 4: Setup VPN interface (Android VpnService builder)
                setupVpnInterface()
                
                // STEP 5: Start foreground service
                startForeground(NOTIFICATION_ID, createNotification("Connecting...", "Setting up VPN tunnel"))
                
                // STEP 6: Connect with SNI → Tor chain (THE MASTERPLAN FEATURE!)
                Log.d(TAG, "Initiating SNI → Tor chain connection...")
                val connectResult = nexus_vpn_connect_sni_tor(enginePtr, serverId)
                Log.d(TAG, "Connection result: $connectResult")
                
                if (connectResult == 0) {
                    // STEP 7: Enable kill switch for leak protection
                    if (killSwitchEnabled) {
                        nexus_vpn_kill_switch_enable(enginePtr)                    }
                    
                    // STEP 8: Connection successful!
                    isConnected = true
                    updateNotification("Connected", "SNI → Tor chain active")
                    Log.d(TAG, "✅ SNI → Tor chain established successfully!")
                } else {
                    // Connection failed
                    disconnectVpn()
                    updateNotification("Connection Failed", "Unable to establish SNI → Tor chain")
                    Log.e(TAG, "❌ Connection failed with code: $connectResult")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                disconnectVpn()
                updateNotification("Error", e.message ?: "Unknown error")
            }
        }
    }

    // ============================================================================
    // ======================== VPN INTERFACE SETUP ===============================
    // ============================================================================
    
    private fun setupVpnInterface() {
        Log.d(TAG, "Setting up VPN interface...")
        
        val builder = Builder()
            .setSession("Nexus VPN")
            .addAddress("10.0.0.1", 24)  // VPN tunnel IP
            .addRoute("0.0.0.0", 0)       // Route ALL traffic through VPN
            .addDnsServer("1.1.1.1")      // Cloudflare DNS
            .addDnsServer("9.9.9.9")      // Quad9 DNS
            .setMtu(1500)
            .setBlocking(false)
            .setMetered(false)
        
        // Add allowed/disallowed apps based on split tunneling
        // For now, route everything through VPN
        
        // IPv6 blocking (leak prevention)
        builder.addRoute("::", 0)
        
        // Bypass VPN for local network (optional)
        // builder.addRoute("192.168.0.0", 16)
        // builder.addRoute("10.0.0.0", 8)
        
        // Establish VPN interface
        vpnInterface = builder.establish()        Log.d(TAG, "VPN interface established: ${vpnInterface != null}")
        
        if (vpnInterface == null) {
            throw IllegalStateException("Failed to establish VPN interface")
        }
        
        // Monitor network changes
        setupNetworkMonitoring()
    }

    // ============================================================================
    // ======================== DISCONNECT ========================================
    // ============================================================================
    
    private fun disconnectVpn() {
        Log.d(TAG, "Disconnecting VPN...")
        
        serviceScope.launch {
            try {
                // Destroy Rust engine (cleans up Tor circuits, SNI connections)
                if (enginePtr != 0L) {
                    nexus_vpn_destroy_engine(enginePtr)
                    enginePtr = 0
                }
                
                // Close VPN interface
                vpnInterface?.close()
                vpnInterface = null
                
                // Remove network callback
                networkCallback?.let {
                    val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    connectivityManager.unregisterNetworkCallback(it)
                }
                
                isConnected = false
                
                updateNotification("Disconnected", "VPN tunnel closed")
                Log.d(TAG, "✅ VPN disconnected successfully")
                
                // Stop foreground service after delay
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error", e)
            }
        }    }

    // ============================================================================
    // ======================== CONFIG UPDATE =====================================
    // ============================================================================
    
    private fun updateConfig(intent: Intent) {
        sniHostname = intent.getStringExtra("sni_hostname") ?: ""
        torEnabled = intent.getBooleanExtra("tor_enabled", true)
        killSwitchEnabled = intent.getBooleanExtra("kill_switch", true)
        
        Log.d(TAG, "Config updated: SNI=$sniHostname, Tor=$torEnabled")
        
        // Apply config to Rust core
        if (isConnected) {
            val randomize = sniHostname.isEmpty()
            nexus_vpn_set_sni_config(enginePtr, sniHostname, randomize, torEnabled)
        }
    }

    // ============================================================================
    // ======================== NETWORK MONITORING ================================
    // ============================================================================
    
    private fun setupNetworkMonitoring() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost - reconnecting...")
                // Auto-reconnect logic could go here
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val bandwidth = networkCapabilities.linkDownstreamBandwidthKbps
                Log.d(TAG, "Network bandwidth: $bandwidth kbps")
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    // ============================================================================
    // ======================== NOTIFICATIONS =====================================
    // ============================================================================
    
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
    
    private fun createNotification(title: String, message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
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

    // ============================================================================
    // ======================== LIFECYCLE =========================================
    // ============================================================================
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroying...")        disconnectVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onTrimMemory(level: Int) {
        Log.d(TAG, "Trim memory: $level")
        super.onTrimMemory(level)
    }

    // ============================================================================
    // ======================== CONSTANTS =========================================
    // ============================================================================
    
    companion object {
        const val ACTION_CONNECT = "com.nexusvpn.android.CONNECT"
        const val ACTION_DISCONNECT = "com.nexusvpn.android.DISCONNECT"
        const val ACTION_UPDATE_CONFIG = "com.nexusvpn.android.UPDATE_CONFIG"
    }
}
