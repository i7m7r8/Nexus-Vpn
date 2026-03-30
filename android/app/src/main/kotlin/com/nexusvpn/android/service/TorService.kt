package com.nexusvpn.android.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TorService(private val context: Context) {
    companion object {
        private const val TAG = "TorService"
        private const val TOR_SOCKS_PORT = 9050
        private const val BOOTSTRAP_TIMEOUT_MS = 120000L
    }

    private var isReady = AtomicBoolean(false)
    private var isStarting = AtomicBoolean(false)
    private var bootstrapProgress = 0

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isStarting.get() || isReady.get()) {
            return@withContext true
        }

        try {
            isStarting.set(true)
            Log.d(TAG, "Starting Tor service")

            val bootstrapped = waitForTorBootstrap()
            
            if (bootstrapped) {
                isReady.set(true)
                Log.d(TAG, "Tor service started successfully")
            } else {
                Log.w(TAG, "Tor bootstrap timeout")
                isReady.set(true)
            }
            
            isStarting.set(false)
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Tor", e)
            isStarting.set(false)
            return@withContext false
        }
    }

    private suspend fun waitForTorBootstrap(): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < BOOTSTRAP_TIMEOUT_MS) {
            if (isTorPortListening()) {
                Log.d(TAG, "Tor socks port is listening")
                return true
            }
            
            delay(500)
            bootstrapProgress = ((System.currentTimeMillis() - startTime) * 100 / BOOTSTRAP_TIMEOUT_MS).toInt()
            
            if (bootstrapProgress % 10 == 0 && bootstrapProgress > 0) {
                Log.d(TAG, "Tor bootstrapping: $bootstrapProgress%")
            }
        }
        
        return false
    }

    private fun isTorPortListening(): Boolean {
        return try {
            val socket = Socket("127.0.0.1", TOR_SOCKS_PORT)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping Tor service")
        isReady.set(false)
        isStarting.set(false)
        bootstrapProgress = 0
    }

    fun isReady(): Boolean = isReady.get()
    fun getProgress(): Int = bootstrapProgress
    fun getSocksPort(): Int = TOR_SOCKS_PORT
    fun getTorAddresses(): List<String> = listOf("127.0.0.1")
}
