package com.nexusvpn.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexusvpn.android.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context.applicationContext)
        if (prefs.alwaysOnVpn) {
            val cls = Class.forName("com.nexusvpn.android.service.NexusVpnService")
            val i = Intent(context, cls).apply { action = "CONNECT" }
            context.startService(i)
        }
    }
}
