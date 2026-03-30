package com.nexusvpn.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
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
            Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
            else -> Log.w(TAG, "Unknown boot action: ${intent.action}")
        }
    }

    private fun handleBootCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStartEnabled = prefs.getBoolean(KEY_AUTO_START, false)
        if (!autoStartEnabled) { Log.d(TAG, "Auto-start disabled"); return }
        Handler(Looper.getMainLooper()).postDelayed({ startVpnService(context, prefs) }, BOOT_DELAY_MS)
    }

    private fun startVpnService(context: Context, prefs: SharedPreferences) {
        try {
            val lastServerId = prefs.getString(KEY_LAST_SERVER, "") ?: ""
            val torEnabled = prefs.getBoolean(KEY_TOR_ENABLED, true)
            val sniHostname = prefs.getString(KEY_SNI_HOSTNAME, "") ?: ""
            val intent = Intent(context, NexusVpnService::class.java).apply {
                action = "CONNECT"
                putExtra("sni_hostname", sniHostname)
                putExtra("tor_enabled", torEnabled)
                putExtra("server_id", lastServerId)
                putExtra("auto_reconnect", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            Log.d(TAG, "VPN service started after boot")
        } catch (e: Exception) { Log.e(TAG, "Failed to start VPN after boot", e) }
    }}
