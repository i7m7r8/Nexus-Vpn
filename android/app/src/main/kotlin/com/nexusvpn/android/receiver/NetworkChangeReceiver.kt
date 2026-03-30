// ============================================================================
// NEXUS VPN - NetworkChangeReceiver
// Masterplan Implementation: Network State Change Handler
// ============================================================================
// Features:
// - WiFi/Cellular Switch Detection
// - Network Quality Assessment
// - Auto-Reconnect Trigger
// - Connection Migration
// - Network Type Logging
// ============================================================================

package com.nexusvpn.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.util.Log
import com.nexusvpn.android.service.NexusVpnService

// ============================================================================
// NETWORK CHANGE RECEIVER
// ============================================================================

class NetworkChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkChangeReceiver"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private var lastNetworkType = -1
    private var wasConnected = false
    private var reconnectScheduled = false

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) {
            return
        }

        Log.d(TAG, "Network change received")

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        // Handle network state change
        handleNetworkStateChange(context, networkInfo, connectivityManager)
    }

    private fun handleNetworkStateChange(
        context: Context,
        networkInfo: NetworkInfo?,
        connectivityManager: ConnectivityManager
    ) {
        val isConnected = networkInfo?.isConnected == true
        val networkType = networkInfo?.type ?: -1

        Log.d(TAG, "Network state: connected=$isConnected, type=$networkType, lastType=$lastNetworkType")

        when {
            // Network disconnected
            !isConnected && wasConnected -> {
                Log.w(TAG, "Network disconnected")
                wasConnected = false
                onNetworkDisconnected(context)
            }

            // Network connected
            isConnected && !wasConnected -> {
                Log.d(TAG, "Network connected")
                wasConnected = true
                onNetworkConnected(context, networkType, connectivityManager)
            }

            // Network type changed (WiFi <-> Cellular)
            isConnected && wasConnected && networkType != lastNetworkType -> {
                Log.d(TAG, "Network type changed: $lastNetworkType -> $networkType")
                onNetworkTypeChanged(context, lastNetworkType, networkType)
            }
        }

        lastNetworkType = networkType
    }

    private fun onNetworkDisconnected(context: Context) {
        Log.w(TAG, "Handling network disconnection")

        // Cancel any pending reconnect
        reconnectScheduled = false

        // In production, would notify VpnService to pause
        // and schedule reconnect when network returns
    }

    private fun onNetworkConnected(context: Context, networkType: Int, connectivityManager: ConnectivityManager) {        Log.d(TAG, "Handling network connection: type=$networkType")

        // Assess network quality
        val networkQuality = assessNetworkQuality(context, connectivityManager)
        Log.d(TAG, "Network quality: $networkQuality")

        // Check if VPN should reconnect
        if (shouldReconnect(networkType, networkQuality)) {
            scheduleReconnect(context)
        }
    }

    private fun onNetworkTypeChanged(context: Context, oldType: Int, newType: Int) {
        Log.d(TAG, "Network type changed from $oldType to $newType")

        // Handle WiFi <-> Cellular transition
        when {
            isWifi(newType) && !isWifi(oldType) -> {
                Log.d(TAG, "Switched to WiFi")
                onSwitchedToWifi(context)
            }
            !isWifi(newType) && isWifi(oldType) -> {
                Log.d(TAG, "Switched to Cellular")
                onSwitchedToCellular(context)
            }
        }

        // May need to re-establish VPN tunnel
        scheduleReconnect(context)
    }

    private fun onSwitchedToWifi(context: Context) {
        Log.d(TAG, "Switched to WiFi - may improve performance")

        // WiFi typically has better bandwidth
        // Could trigger speed test or quality check
    }

    private fun onSwitchedToCellular(context: Context) {
        Log.d(TAG, "Switched to Cellular - monitoring data usage")

        // Cellular may have data limits
        // Could enable data saving mode
    }

    private fun assessNetworkQuality(
        context: Context,
        connectivityManager: ConnectivityManager
    ): NetworkQuality {
        val network = connectivityManager.activeNetwork ?: return NetworkQuality.UNKNOWN
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkQuality.UNKNOWN

        // Check bandwidth
        val downlinkBandwidth = capabilities.linkDownstreamBandwidthKbps
        val uplinkBandwidth = capabilities.linkUpstreamBandwidthKbps

        // Check capabilities
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isMetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).not()
        val isVpn = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).not()

        Log.d(TAG, "Network capabilities: downlink=$downlinkBandwidth, uplink=$uplinkBandwidth, metered=$isMetered")

        return when {
            downlinkBandwidth < 100 -> NetworkQuality.POOR
            downlinkBandwidth < 1000 -> NetworkQuality.FAIR
            downlinkBandwidth < 10000 -> NetworkQuality.GOOD
            else -> NetworkQuality.EXCELLENT
        }
    }

    private fun shouldReconnect(networkType: Int, quality: NetworkQuality): Boolean {
        // Don't reconnect on poor quality networks
        if (quality == NetworkQuality.POOR) {
            Log.w(TAG, "Skipping reconnect on poor quality network")
            return false
        }

        // Don't reconnect on metered networks if configured
        // (would check user preferences)

        return true
    }

    private fun scheduleReconnect(context: Context) {
        if (reconnectScheduled) {
            Log.d(TAG, "Reconnect already scheduled")
            return
        }

        reconnectScheduled = true

        // Schedule reconnect with delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Executing scheduled reconnect")
            reconnectScheduled = false

            // Start VPN service
            val intent = Intent(context, NexusVpnService::class.java).apply {                action = "CONNECT"
            }

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Reconnect initiated")
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect failed", e)
            }
        }, RECONNECT_DELAY_MS)

        Log.d(TAG, "Reconnect scheduled in ${RECONNECT_DELAY_MS / 1000}s")
    }

    private fun isWifi(networkType: Int): Boolean {
        return networkType == ConnectivityManager.TYPE_WIFI
    }

    private fun isCellular(networkType: Int): Boolean {
        return networkType == ConnectivityManager.TYPE_MOBILE
    }
}

// ============================================================================
// NETWORK QUALITY ENUM
// ============================================================================

enum class NetworkQuality {
    UNKNOWN,
    POOR,
    FAIR,
    GOOD,
    EXCELLENT
}
