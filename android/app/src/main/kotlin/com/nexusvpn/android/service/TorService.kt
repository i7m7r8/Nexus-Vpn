package com.nexusvpn.android.service

import android.content.Context
import android.util.Log
import org.torproject.android.service.TorService
import org.torproject.android.service.TorServiceConfig
import java.util.concurrent.atomic.AtomicBoolean

class TorService(private val context: Context) {
    companion object {
        private const val TAG = "TorService"
    }

    private var torService: TorService? = null
    private var isReady = AtomicBoolean(false)
    private var isStarting = AtomicBoolean(false)

    fun start(): Boolean {
        if (isStarting.get() || isReady.get()) {
            Log.d(TAG, "Tor already starting or ready")
            return true
        }

        try {
            isStarting.set(true)
            Log.d(TAG, "Starting Tor service")

            // Start Tor using official Tor Android library
            val intent = TorService.getStartIntent(context)
            context.startService(intent)

            // Wait for Tor to be ready (polling)
            var attempts = 0
            while (attempts < 60) {
                if (isTorReady()) {
                    isReady.set(true)
                    isStarting.set(false)
                    Log.d(TAG, "Tor service started successfully")
                    return true
                }
                Thread.sleep(1000)
                attempts++
                Log.d(TAG, "Waiting for Tor... $attempts/60")
            }

            Log.e(TAG, "Tor startup timeout")
            isStarting.set(false)
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Tor", e)
            isStarting.set(false)
            return false
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping Tor service")
        try {
            val intent = TorService.getStopIntent(context)
            context.startService(intent)
            isReady.set(false)
            isStarting.set(false)
            Log.d(TAG, "Tor service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Tor", e)
        }
    }

    fun isReady(): Boolean {
        return isReady.get() || isTorReady()
    }

    private fun isTorReady(): Boolean {
        // Check if Tor socks port is listening
        return try {
            val sock = java.net.Socket("127.0.0.1", 9050)
            sock.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getTorAddresses(): List<String> {
        // Return Tor network addresses to bypass in VPN
        return listOf(
            "127.0.0.1" // Localhost for Tor socks
        )
    }
}
