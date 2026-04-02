package com.nexusvpn.android

import android.app.Application
import com.nexusvpn.android.data.Prefs

class NexusVpnApplication : Application() {
    companion object {
        lateinit var prefs: Prefs
            private set
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }
}
