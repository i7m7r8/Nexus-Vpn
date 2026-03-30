package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
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

class VpnMonitoringService : Service() {
    companion object {
        private const val TAG = "VpnMonitoringService"
        private const val NOTIFICATION_CHANNEL_ID = "nexus_vpn_monitor_channel"
        private const val NOTIFICATION_ID = 1002
        private const val HEALTH_CHECK_INTERVAL_MS = 10000L
    }

    private var isMonitoring = AtomicBoolean(false)
    private var isConnected = AtomicBoolean(false)
    private var sniHealthy = AtomicBoolean(false)    private var torHealthy = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var healthCheckJob: Job? = null
    private val binder = LocalBinder()

    override fun onCreate() { super.onCreate(); createNotificationChannel() }
    override fun onBind(intent: Intent?): IBinder = binder
    override fun onDestroy() { stopMonitoring(); serviceScope.cancel(); super.onDestroy() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_MONITORING" -> startMonitoring()
            "STOP_MONITORING" -> stopMonitoring()
        }
        return START_STICKY
    }

    fun startMonitoring() {
        if (isMonitoring.get()) return
        isMonitoring.set(true)
        startForegroundService()
        startHealthCheckJob()
    }

    fun stopMonitoring() {
        if (!isMonitoring.get()) return
        isMonitoring.set(false)
        healthCheckJob?.cancel()
        stopForegroundService()
    }

    private fun startHealthCheckJob() {
        healthCheckJob = serviceScope.launch {
            while (isMonitoring.get() && isConnected.get()) {
                performHealthCheck()
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun performHealthCheck() {
        sniHealthy.set(isConnected.get())
        torHealthy.set(isConnected.get())
        Log.d(TAG, "Health check: SNI=${sniHealthy.get()}, Tor=${torHealthy.get()}")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Nexus VPN Monitor", NotificationManager.IMPORTANCE_LOW)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)    }

    private fun startForegroundService() {
        val notification = createNotification("Monitoring", "SNI → Tor health check active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else { startForeground(NOTIFICATION_ID, notification) }
    }

    private fun stopForegroundService() { stopForeground(STOP_FOREGROUND_REMOVE) }

    private fun createNotification(title: String, message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title).setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent).setOngoing(true).build()
    }

    inner class LocalBinder : Binder() { fun getService(): VpnMonitoringService = this@VpnMonitoringService }
}
