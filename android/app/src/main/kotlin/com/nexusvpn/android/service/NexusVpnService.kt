package com.nexusvpn.android.service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexusvpn.android.MainActivity

class NexusVpnService : VpnService() {

    companion object {
        private const val TAG = "NexusVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "nexus_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        
        init {
            System.loadLibrary("nexus_vpn_core")
        }
    }

    private var enginePtr: Long = 0
    private var isConnected = false

    override fun onCreate() {
        super.onCreate()
        enginePtr = createEngine()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> connectVpn(intent)
            "DISCONNECT" -> disconnectVpn()
        }
        return START_STICKY
    }

    private fun connectVpn(intent: Intent) {
        val sniHostname = intent.getStringExtra("sni_hostname") ?: ""
        val torEnabled = intent.getBooleanExtra("tor_enabled", true)
        
        Log.d(TAG, "Connecting: SNI=$sniHostname, Tor=$torEnabled")
        
        startForeground(NOTIFICATION_ID, createNotification("Connecting...", "Setting up VPN"))
        
        setSniConfig(enginePtr, sniHostname, sniHostname.isEmpty(), torEnabled)        
        val result = connectSniTor(enginePtr, "default")
        
        if (result == 0) {
            isConnected = true
            updateNotification("Connected", "SNI to Tor chain active")
            Log.d(TAG, "Connected successfully")
        } else {
            disconnectVpn()
            updateNotification("Failed", "Connection error: $result")
        }
    }

    private fun disconnectVpn() {
        Log.d(TAG, "Disconnecting")
        
        if (enginePtr != 0L) {
            destroyEngine(enginePtr)
            enginePtr = 0
        }
        
        isConnected = false
        updateNotification("Disconnected", "VPN tunnel closed")
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Nexus VPN Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
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
            .setOngoing(true)            .build()
    }

    private fun updateNotification(title: String, message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, message))
    }

    override fun onDestroy() {
        disconnectVpn()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // JNI Functions
    private external fun createEngine(): Long
    private external fun destroyEngine(enginePtr: Long)
    private external fun setSniConfig(enginePtr: Long, hostname: String, randomize: Boolean, torEnabled: Boolean): Int
    private external fun connectSniTor(enginePtr: Long, serverId: String): Int
}
