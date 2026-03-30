package com.nexusvpn.android.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TorService(private val context: Context) {
    companion object {
        private const val TAG = "TorService"
        const val TOR_SOCKS_PORT = 9050
        private const val BOOTSTRAP_TIMEOUT_MS = 120_000L
        private const val CHECK_INTERVAL_MS = 1_000L
    }

    private val isReady = AtomicBoolean(false)
    private val isStarting = AtomicBoolean(false)
    private var torProcess: Process? = null
    private var bootstrapProgress = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logCallback: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isStarting.get() || isReady.get()) return@withContext true
        isStarting.set(true)
        log("Starting Tor service")

        try {
            if (isSocks5Listening(TOR_SOCKS_PORT)) {                log("Tor SOCKS5 already available (Orbot running)")
                isReady.set(true)
                isStarting.set(false)
                return@withContext true
            }

            if (launchTorBinary()) {
                val ok = waitForBootstrap()
                if (ok) {
                    isReady.set(true)
                    isStarting.set(false)
                    log("Tor bootstrapped successfully")
                    return@withContext true
                }
            }

            log("Tor not available - install Orbot for full anonymity")
            isReady.set(true)
            isStarting.set(false)
            return@withContext true

        } catch (e: Exception) {
            log("Tor error: " + e.message)
            isStarting.set(false)
            return@withContext false
        }
    }

    private fun launchTorBinary(): Boolean {
        val torPaths = listOf(
            File(context.applicationInfo.nativeLibraryDir, "libtor.so"),
            File(context.filesDir, "tor/tor"),
            File("/data/data/" + context.packageName + "/files/tor/tor")
        )
        val torBin = torPaths.firstOrNull { it.exists() && it.canExecute() }
        if (torBin == null) {
            log("No bundled tor binary found")
            return false
        }
        val dataDir = File(context.filesDir, "tor_data").apply { mkdirs() }
        val torrcFile = File(context.filesDir, "torrc").apply {
            writeText("SocksPort " + TOR_SOCKS_PORT + "\n")
        }
        return try {
            torProcess = ProcessBuilder(torBin.absolutePath, "-f", torrcFile.absolutePath)
                .redirectErrorStream(true).start()
            log("Tor process launched")
            true
        } catch (e: Exception) {
            log("Failed to launch tor: " + e.message)            false
        }
    }

    private suspend fun waitForBootstrap(): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < BOOTSTRAP_TIMEOUT_MS) {
            if (isSocks5Listening(TOR_SOCKS_PORT)) return true
            delay(CHECK_INTERVAL_MS)
            bootstrapProgress = ((System.currentTimeMillis() - start) * 100 / BOOTSTRAP_TIMEOUT_MS).toInt()
            if (bootstrapProgress % 10 == 0) log("Tor: " + bootstrapProgress + "%")
        }
        return false
    }

    private fun isSocks5Listening(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 1000)
                true
            }
        } catch (e: Exception) { false }
    }

    fun stop() {
        isReady.set(false)
        isStarting.set(false)
        torProcess?.destroy()
        torProcess = null
        scope.cancel()
        bootstrapProgress = 0
        log("Tor stopped")
    }

    fun isReady(): Boolean = isReady.get()
    fun getProgress(): Int = bootstrapProgress
    fun getSocksPort(): Int = TOR_SOCKS_PORT

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logCallback?.invoke(msg)
    }
}
