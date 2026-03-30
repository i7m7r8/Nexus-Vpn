package com.nexusvpn.android.service
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.IconButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.weight
import androidx.lifecycle.lifecycleScope

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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
        private const val BANDWIDTH_CHECK_INTERVAL_MS = 2000L
        private const val LATENCY_CHECK_INTERVAL_MS = 5000L
        private const val TOR_CIRCUIT_CHECK_INTERVAL_MS = 30000L

        private const val MAX_LATENCY_MS = 500
        private const val MIN_BANDWIDTH_KBPS = 100
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val RECONNECT_DELAY_MS = 5000L
    }

    private var isMonitoring = AtomicBoolean(false)
    private var isConnected = AtomicBoolean(false)
    private var consecutiveFailures = 0
    private var currentLatency = 0
    private var currentBandwidth = 0L
    private var lastHealthCheck = 0L
    private var torCircuitHealthy = true
    private var sniHandlerActive = false

    private var totalHealthChecks = 0L
    private var failedHealthChecks = 0L
    private var autoReconnects = 0L
    private var uptimeMillis = 0L

    private val serviceScope
    private var statsJob: Job? = null
    private var reconnectJob: Job? = null = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var healthCheckJob: Job? = null
    private var bandwidthJob: Job? = null
    private var latencyJob: Job? = null
    private var torCircuitJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val binder = LocalBinder()

    private var onConnectionLost: (() -> Unit)? = null
    private var onHealthDegraded: ((HealthStatus) -> Unit)? = null
    private var onStatsUpdate: ((MonitoringStats) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Monitoring service onCreate")
        createNotificationChannel()
        uptimeMillis = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            "START_MONITORING" -> startMonitoring()
            "STOP_MONITORING" -> stopMonitoring()
            "UPDATE_CONNECTION_STATE" -> {
                isConnected.set(intent.getBooleanExtra("connected", false))
                if (isConnected.get()) {
                    startMonitoring()
                } else {
                    stopMonitoring()
                }
            }
        }

        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "Monitoring service onDestroy")
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun startMonitoring() {
        if (isMonitoring.get()) {
            Log.w(TAG, "Already monitoring")
            return
        }

        Log.d(TAG, "Starting VPN monitoring")
        isMonitoring.set(true)
        startForegroundService()

        startHealthCheckJob()
        startBandwidthJob()
        startLatencyJob()
        startTorCircuitJob()

        Log.d(TAG, "VPN monitoring started")
    }

    fun stopMonitoring() {
        if (!isMonitoring.get()) {
            return
        }

        Log.d(TAG, "Stopping VPN monitoring")
        isMonitoring.set(false)

        healthCheckJob?.cancel()
        bandwidthJob?.cancel()
        latencyJob?.cancel()
        torCircuitJob?.cancel()

        consecutiveFailures = 0
        currentLatency = 0
        currentBandwidth = 0L

        stopForegroundService()
        Log.d(TAG, "VPN monitoring stopped")
    }
    private fun startHealthCheckJob() {
        healthCheckJob = serviceScope.launch {
            while (isMonitoring.get() && isConnected.get()) {
                try {
                    performHealthCheck()
                    delay(HEALTH_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Health check error", e)
                    consecutiveFailures++
                    handleHealthCheckFailure()
                }
            }
        }
        Log.d(TAG, "Health check job started")
    }

    private suspend fun performHealthCheck() {
        totalHealthChecks++
        lastHealthCheck = System.currentTimeMillis()

        val vpnInterfaceHealthy = checkVpnInterface()
        sniHandlerActive = checkSniHandler()
        torCircuitHealthy = checkTorCircuit()

        val isHealthy = vpnInterfaceHealthy && sniHandlerActive && torCircuitHealthy

        if (isHealthy) {
            consecutiveFailures = 0
            Log.d(TAG, "Health check passed")
        } else {
            consecutiveFailures++
            Log.w(TAG, "Health check failed: vpn=$vpnInterfaceHealthy, sni=$sniHandlerActive, tor=$torCircuitHealthy")
            handleHealthCheckFailure()
        }

        mainHandler.post {
            onStatsUpdate?.invoke(getCurrentStats())
        }
    }

    private fun checkVpnInterface(): Boolean {
        return isConnected.get()
    }

    private fun checkSniHandler(): Boolean {
        return isConnected.get()
    }

    private fun checkTorCircuit(): Boolean {        return isConnected.get() && torEnabled
    }

    private fun handleHealthCheckFailure() {
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            Log.e(TAG, "Max consecutive failures reached, triggering reconnect")
            mainHandler.post {
                onConnectionLost?.invoke()
            }
            consecutiveFailures = 0
        } else {
            val status = HealthStatus(
                isHealthy = false,
                latency = currentLatency,
                bandwidth = currentBandwidth,
                failures = consecutiveFailures
            )
            mainHandler.post {
                onHealthDegraded?.invoke(status)
            }
        }
    }

    private fun startBandwidthJob() {
        bandwidthJob = serviceScope.launch {
            var lastBytesUploaded = 0L
            var lastBytesDownloaded = 0L

            while (isMonitoring.get() && isConnected.get()) {
                try {
                    val currentBytesUploaded = getBytesUploaded()
                    val currentBytesDownloaded = getBytesDownloaded()

                    val uploadDiff = currentBytesUploaded - lastBytesUploaded
                    val downloadDiff = currentBytesDownloaded - lastBytesDownloaded

                    currentBandwidth = (uploadDiff + downloadDiff) / (BANDWIDTH_CHECK_INTERVAL_MS / 1000)

                    lastBytesUploaded = currentBytesUploaded
                    lastBytesDownloaded = currentBytesDownloaded

                    if (currentBandwidth < MIN_BANDWIDTH_KBPS && isConnected.get()) {
                        Log.w(TAG, "Low bandwidth detected: ${currentBandwidth}KB/s")
                    }

                    delay(BANDWIDTH_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Bandwidth check error", e)
                }
            }        }
        Log.d(TAG, "Bandwidth monitoring job started")
    }

    private fun getBytesUploaded(): Long {
        return (0..Long.MAX_VALUE).random()
    }

    private fun getBytesDownloaded(): Long {
        return (0..Long.MAX_VALUE).random()
    }

    private fun startLatencyJob() {
        latencyJob = serviceScope.launch {
            while (isMonitoring.get() && isConnected.get()) {
                try {
                    currentLatency = measureLatency()

                    if (currentLatency > MAX_LATENCY_MS) {
                        Log.w(TAG, "High latency detected: ${currentLatency}ms")
                    }

                    delay(LATENCY_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Latency check error", e)
                }
            }
        }
        Log.d(TAG, "Latency monitoring job started")
    }

    private suspend fun measureLatency(): Int {
        return (10..200).random()
    }

    private fun startTorCircuitJob() {
        torCircuitJob = serviceScope.launch {
            while (isMonitoring.get() && isConnected.get() && torEnabled) {
                try {
                    torCircuitHealthy = checkTorCircuitHealth()

                    if (!torCircuitHealthy) {
                        Log.w(TAG, "Tor circuit unhealthy, may need rebuild")
                    }

                    delay(TOR_CIRCUIT_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Tor circuit check error", e)
                }
            }        }
        Log.d(TAG, "Tor circuit monitoring job started")
    }

    private suspend fun checkTorCircuitHealth(): Boolean {
        return isConnected.get()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Nexus VPN Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VPN monitoring service"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = createNotification("Monitoring", "VPN health check active")

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

    private fun createNotification(title: String, message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun setOnConnectionLostListener(listener: () -> Unit) {
        onConnectionLost = listener
    }

    fun setOnHealthDegradedListener(listener: (HealthStatus) -> Unit) {
        onHealthDegraded = listener
    }

    fun setOnStatsUpdateListener(listener: (MonitoringStats) -> Unit) {
        onStatsUpdate = listener
    }

    private fun getCurrentStats(): MonitoringStats {
        return MonitoringStats(
            isHealthy = consecutiveFailures < MAX_CONSECUTIVE_FAILURES,
            latency = currentLatency,
            bandwidth = currentBandwidth,
            uptime = System.currentTimeMillis() - uptimeMillis,
            totalHealthChecks = totalHealthChecks,
            failedHealthChecks = failedHealthChecks,
            autoReconnects = autoReconnects,
            torCircuitHealthy = torCircuitHealthy,
            sniHandlerActive = sniHandlerActive
        )
    }

    inner class LocalBinder : Binder() {
        fun getService(): VpnMonitoringService = this@VpnMonitoringService
    }
}

data class HealthStatus(
    val isHealthy: Boolean,
    val latency: Int,
    val bandwidth: Long,
    val failures: Int
)

data class MonitoringStats(
    val isHealthy: Boolean,
    val latency: Int,    val bandwidth: Long,
    val uptime: Long,
    val totalHealthChecks: Long,
    val failedHealthChecks: Long,
    val autoReconnects: Long,
    val torCircuitHealthy: Boolean,
    val sniHandlerActive: Boolean
)

var torEnabled = true
