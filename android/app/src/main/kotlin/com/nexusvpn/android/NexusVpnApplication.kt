package com.nexusvpn.android

import android.app.Application
import com.nexusvpn.android.data.PreferencesManager

class NexusVpnApplication : Application() {
    companion object {
        lateinit var prefs: PreferencesManager
            private set
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }
}
