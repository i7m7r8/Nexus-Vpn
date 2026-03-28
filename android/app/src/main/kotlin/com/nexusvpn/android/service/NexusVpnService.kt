package com.nexusvpn.android.service

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder

class NexusVpnService : VpnService() {


    companion object {
        init {
            System.loadLibrary("nexus_vpn_core")
        }
        private external fun nativeStartTor(enginePtr: Long)
        private external fun nativeStopTor(enginePtr: Long)
        private external fun nativeSetSniConfig(enginePtr: Long, sniEnabled: Boolean, customSni: String, torEnabled: Boolean)
    }
    private var enginePtr: Long = 0

    override fun onBind(intent: Intent): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> connectVpn(intent)
            "DISCONNECT" -> disconnectVpn()
            "MONITOR" -> startMonitoring()
        "UPDATE_CONFIG" -> {
            val sniEnabled = intent.getBooleanExtra("sni_enabled", false)
            val torEnabled = intent.getBooleanExtra("tor_enabled", false)
            val customSni = intent.getStringExtra("custom_sni") ?: ""
            nativeSetSniConfig(enginePtr, sniEnabled, customSni, torEnabled)
        }
    }
        return START_STICKY
    }
    
    private fun connectVpn(intent: Intent) {
        // Will be implemented with Rust JNI
    }
    
    private fun disconnectVpn() {
        // Will be implemented with Rust JNI
    }
    
    private fun startMonitoring() {
        // Will be implemented with Rust JNI
    }
}
