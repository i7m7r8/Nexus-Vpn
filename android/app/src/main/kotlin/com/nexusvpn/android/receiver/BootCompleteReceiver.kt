package com.nexusvpn.android.receiver
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.IconButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.weight
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.nexusvpn.android.service.NexusVpnService

class BootCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompleteReceiver"
        private const val BOOT_DELAY_MS = 30000L
        private const val PREFS_NAME = "nexus_vpn_prefs"
        private const val KEY_AUTO_START = "auto_start_on_boot"
        private const val KEY_LAST_SERVER = "last_server_id"
        private const val KEY_TOR_ENABLED = "tor_enabled"
        private const val KEY_SNI_HOSTNAME = "sni_hostname"
    }
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot broadcast received: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Device boot completed")
                handleBootCompleted(context)
            }
            else -> {
                Log.w(TAG, "Unknown boot action: ${intent.action}")
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Handling boot completed")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStartEnabled = prefs.getBoolean(KEY_AUTO_START, false)

        if (!autoStartEnabled) {
            Log.d(TAG, "Auto-start disabled, skipping VPN start")
            return
        }

        Log.d(TAG, "Auto-start enabled, scheduling VPN start")

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startVpnService(context, prefs)
        }, BOOT_DELAY_MS)

        Log.d(TAG, "VPN start scheduled")
    }

    private fun startVpnService(context: Context, prefs: SharedPreferences) {
        Log.d(TAG, "Starting VPN service after boot")

        try {
            val lastServerId = prefs.getString(KEY_LAST_SERVER, "") ?: ""
            val torEnabled = prefs.getBoolean(KEY_TOR_ENABLED, true)
            val sniHostname = prefs.getString(KEY_SNI_HOSTNAME, "") ?: ""

            Log.d(TAG, "Saved config: server=$lastServerId, tor=$torEnabled")

            val intent = Intent(context, NexusVpnService::class.java).apply {
                action = "CONNECT"
                putExtra("sni_hostname", sniHostname)
                putExtra("tor_enabled", torEnabled)
                putExtra("server_id", lastServerId)                putExtra("auto_reconnect", true)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            Log.d(TAG, "VPN service started after boot")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN service after boot", e)
        }
    }
}
