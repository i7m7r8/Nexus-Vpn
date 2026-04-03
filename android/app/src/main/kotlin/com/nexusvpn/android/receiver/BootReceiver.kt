package com.nexusvpn.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.nexusvpn.android.data.Prefs
import com.nexusvpn.android.service.NexusVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "android.intent.action.REBOOT") return

        val prefs = Prefs(context.applicationContext)
        if (!prefs.alwaysOnVpn) return

        // Check if VPN permission has been granted
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            Log.w("BootReceiver", "VPN permission not yet granted, skipping auto-connect")
            return
        }

        Log.i("BootReceiver", "Auto-connecting VPN on boot")
        val i = Intent(context, NexusVpnService::class.java).apply { action = "CONNECT" }
        context.startService(i)
    }
}
