package com.nexusvpn.android.data

import android.content.Context
import androidx.core.content.edit

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("nexus", Context.MODE_PRIVATE)

    var sniHostname: String?
        get() = sp.getString("sni_host", "cdn.cloudflare.net")
        set(v) = sp.edit { putString("sni_host", v) }

    var useBridges: Boolean
        get() = sp.getBoolean("use_bridges", false)
        set(v) = sp.edit { putBoolean("use_bridges", v) }

    var bridgeType: String
        get() = sp.getString("bridge_type", "obfs4") ?: "obfs4"
        set(v) = sp.edit { putString("bridge_type", v) }

    var customBridgeLine: String?
        get() = sp.getString("custom_bridge", null)
        set(v) = sp.edit { putString("custom_bridge", v) }

    var killSwitch: Boolean
        get() = sp.getBoolean("kill_switch", true)
        set(v) = sp.edit { putBoolean("kill_switch", v) }

    var alwaysOnVpn: Boolean
        get() = sp.getBoolean("always_on", false)
        set(v) = sp.edit { putBoolean("always_on", v) }

    var autoConnectWifi: Boolean
        get() = sp.getBoolean("auto_conn_wifi", false)
        set(v) = sp.edit { putBoolean("auto_conn_wifi", v) }

    var isVpnConnected: Boolean
        get() = sp.getBoolean("is_connected", false)
        set(v) = sp.edit { putBoolean("is_connected", v) }

    fun clear() = sp.edit { clear() }
}
