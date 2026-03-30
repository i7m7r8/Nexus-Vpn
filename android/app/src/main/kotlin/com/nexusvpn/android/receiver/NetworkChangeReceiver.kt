package com.nexusvpn.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nexusvpn.android.service.NexusVpnService

class NetworkChangeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NetworkChangeReceiver"
        private const val RECONNECT_DELAY_MS = 3000L
    }
    private var wasConnected = false
    private var reconnectScheduled = false

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return
        Log.d(TAG, "Network change received")
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        handleNetworkStateChange(context, networkInfo, connectivityManager)
    }

    private fun handleNetworkStateChange(context: Context, networkInfo: android.net.NetworkInfo?, connectivityManager: ConnectivityManager) {
        val isConnected = networkInfo?.isConnected == true
        when {
            !isConnected && wasConnected -> { Log.w(TAG, "Network disconnected"); wasConnected = false }
            isConnected && !wasConnected -> { Log.d(TAG, "Network connected"); wasConnected = true; if (shouldReconnect()) scheduleReconnect(context) }
        }
    }

    private fun shouldReconnect(): Boolean = true

    private fun scheduleReconnect(context: Context) {
        if (reconnectScheduled) return
        reconnectScheduled = true
        Handler(Looper.getMainLooper()).postDelayed({
            reconnectScheduled = false
            val intent = Intent(context, NexusVpnService::class.java).apply { action = "CONNECT" }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) { Log.e(TAG, "Reconnect failed", e) }
        }, RECONNECT_DELAY_MS)
    }
}
