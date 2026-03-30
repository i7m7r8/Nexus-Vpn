package com.nexusvpn.android.service

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PHASE 2: SNI Proxy Service
 * Handles SNI hostname spoofing
 */
class SniProxyService(private val context: Context) {
    companion object {
        private const val TAG = "SniProxyService"
    }

    private var isRunning = AtomicBoolean(false)
    private var sniHostname: String = ""

    fun start(hostname: String = ""): Boolean {
        if (isRunning.get()) {
            Log.d(TAG, "SNI Proxy already running")
            return true
        }

        try {
            sniHostname = hostname.ifEmpty { generateRandomSni() }
            isRunning.set(true)
            Log.d(TAG, "SNI Proxy started: $sniHostname")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SNI Proxy", e)
            isRunning.set(false)
            return false
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping SNI Proxy")
        isRunning.set(false)
        sniHostname = ""
    }

    fun isRunning(): Boolean = isRunning.get()
    fun getSniHostname(): String = sniHostname

    private fun generateRandomSni(): String {
        val domains = listOf(
            "cdn.cloudflare.com",
            "www.microsoft.com",
            "update.google.com",
            "api.github.com",
            "cdn.jsdelivr.net"
        )
        return domains.random()
    }
}
