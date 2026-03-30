package com.nexusvpn.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.nexusvpn.android.service.NexusVpnService

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

        handleNetworkStateChange(context, networkInfo, connectivityManager)    }

    private fun handleNetworkStateChange(
        context: Context,
        networkInfo: android.net.NetworkInfo?,
        connectivityManager: ConnectivityManager
    ) {
        val isConnected = networkInfo?.isConnected == true
        val networkType = networkInfo?.type ?: -1

        Log.d(TAG, "Network state: connected=$isConnected, type=$networkType")

        when {
            !isConnected && wasConnected -> {
                Log.w(TAG, "Network disconnected")
                wasConnected = false
            }

            isConnected && !wasConnected -> {
                Log.d(TAG, "Network connected")
                wasConnected = true
                if (shouldReconnect(networkType)) {
                    scheduleReconnect(context)
                }
            }

            isConnected && wasConnected && networkType != lastNetworkType -> {
                Log.d(TAG, "Network type changed: $lastNetworkType -> $networkType")
                scheduleReconnect(context)
            }
        }

        lastNetworkType = networkType
    }

    private fun shouldReconnect(networkType: Int): Boolean {
        return networkType != -1
    }

    private fun scheduleReconnect(context: Context) {
        if (reconnectScheduled) {
            return
        }

        reconnectScheduled = true

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Executing scheduled reconnect")
            reconnectScheduled = false
            val intent = Intent(context, NexusVpnService::class.java).apply {
                action = "CONNECT"
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

        Log.d(TAG, "Reconnect scheduled")
    }
}
