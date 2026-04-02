package com.nexusvpn.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexus_vpn_prefs", Context.MODE_PRIVATE)

    var sniHostname: String?
        get() = prefs.getString(KEY_SNI_HOSTNAME, null)
        set(value) = prefs.edit { putString(KEY_SNI_HOSTNAME, value) }

    var useBridges: Boolean
        get() = prefs.getBoolean(KEY_USE_BRIDGES, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_BRIDGES, value) }

    var bridgeType: String?
        get() = prefs.getString(KEY_BRIDGE_TYPE, null)
        set(value) = prefs.edit { putString(KEY_BRIDGE_TYPE, value) }

    var customBridgeLine: String?
        get() = prefs.getString(KEY_CUSTOM_BRIDGE, null)
        set(value) = prefs.edit { putString(KEY_CUSTOM_BRIDGE, value) }

    var killSwitch: Boolean
        get() = prefs.getBoolean(KEY_KILL_SWITCH, true)
        set(value) = prefs.edit { putBoolean(KEY_KILL_SWITCH, value) }

    var alwaysOnVpn: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_ON, false)
        set(value) = prefs.edit { putBoolean(KEY_ALWAYS_ON, value) }

    var autoConnectWifi: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT_WIFI, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CONNECT_WIFI, value) }

    var splitTunneling: Boolean
        get() = prefs.getBoolean(KEY_SPLIT_TUNNELING, false)
        set(value) = prefs.edit { putBoolean(KEY_SPLIT_TUNNELING, value) }

    fun clear() { prefs.edit { clear() } }

    companion object {
        private const val KEY_SNI_HOSTNAME = "sni_hostname"
        private const val KEY_USE_BRIDGES = "use_bridges"
        private const val KEY_BRIDGE_TYPE = "bridge_type"
        private const val KEY_CUSTOM_BRIDGE = "custom_bridge"
        private const val KEY_KILL_SWITCH = "kill_switch"
        private const val KEY_ALWAYS_ON = "always_on"
        private const val KEY_AUTO_CONNECT_WIFI = "auto_connect_wifi"
        private const val KEY_SPLIT_TUNNELING = "split_tunneling"
    }
}
