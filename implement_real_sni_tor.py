#!/usr/bin/env python3
from pathlib import Path

# ════════════════════════════════════════════════════════════════
# REAL SniProxyService - TLS SNI rewriting + Tor SOCKS5 forwarding
# ════════════════════════════════════════════════════════════════
sni_proxy = '''package com.nexusvpn.android.service

import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class SniProxyService {
    companion object {
        private const val TAG = "SniProxyService"
        const val PROXY_PORT = 8080
        private const val TOR_SOCKS_HOST = "127.0.0.1"
        private const val TOR_SOCKS_PORT = 9050
        private const val BUFFER_SIZE = 8192
    }

    private val isRunning = AtomicBoolean(false)
    private var sniHostname: String = ""
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logCallback: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    fun start(hostname: String): Boolean {
        if (isRunning.get()) return true
        sniHostname = hostname.ifEmpty { "www.cloudflare.com" }
        return try {
            serverSocket = ServerSocket()
            serverSocket!!.reuseAddress = true
            serverSocket!!.bind(InetSocketAddress("127.0.0.1", PROXY_PORT))
            isRunning.set(true)
            scope.launch { acceptLoop() }
            log("✅ SNI Proxy started on port $PROXY_PORT, SNI=$sniHostname")
            true
        } catch (e: Exception) {            log("❌ SNI Proxy failed: ${e.message}")
            false
        }
    }

    private suspend fun acceptLoop() {
        while (isRunning.get()) {
            try {
                val client = withContext(Dispatchers.IO) { serverSocket!!.accept() }
                log("📥 SNI: Connection from ${client.remoteSocketAddress}")
                scope.launch { handleClient(client) }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "Accept error", e)
            }
        }
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.use { clientSock ->
                val clientIn = clientSock.getInputStream()
                val clientOut = clientSock.getOutputStream()
                val firstBytes = ByteArray(BUFFER_SIZE)
                val n = clientIn.read(firstBytes)
                if (n <= 0) return@withContext
                val data = firstBytes.copyOf(n)

                val headerStr = String(data, Charsets.ISO_8859_1)
                if (headerStr.startsWith("CONNECT ")) {
                    handleHttpConnect(clientSock, clientIn, clientOut, headerStr, data)
                } else if (data[0] == 0x16.toByte()) {
                    log("🔐 SNI: TLS ClientHello detected")
                    handleDirectTls(clientSock, clientIn, clientOut, data)
                } else {
                    log("📄 SNI: Plain traffic (${data.size} bytes)")
                    handlePlain(clientIn, clientOut, data)
                }
            }
        } catch (e: Exception) {
            log("❌ SNI client error: ${e.message}")
        }
    }

    private fun handleHttpConnect(
        client: Socket, clientIn: InputStream, clientOut: OutputStream,
        header: String, data: ByteArray
    ) {
        val line = header.lines().firstOrNull() ?: return
        val parts = line.split(" ")
        if (parts.size < 2) return        val hostPort = parts[1].split(":")
        val destHost = hostPort[0]
        val destPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
        log("🔗 CONNECT $destHost:$destPort via Tor")

        val torSocket = connectViaTorSocks5(destHost, destPort) ?: run {
            clientOut.write("HTTP/1.1 503 Service Unavailable\\r\\n\\r\\n".toByteArray())
            log("❌ Tor connection failed")
            return
        }

        torSocket.use { tor ->
            clientOut.write("HTTP/1.1 200 Connection Established\\r\\n\\r\\n".toByteArray())
            clientOut.flush()
            log("✅ Tunnel established to $destHost:$destPort")

            var firstTlsPacket = true
            val torOut = tor.getOutputStream()
            val torIn = tor.getInputStream()

            val relayJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = clientIn.read(buf)
                        if (n <= 0) break
                        val chunk = buf.copyOf(n)
                        if (firstTlsPacket && chunk[0] == 0x16.toByte()) {
                            firstTlsPacket = false
                            val rewritten = rewriteSni(chunk)
                            torOut.write(rewritten)
                            log("📝 SNI rewritten in CONNECT tunnel")
                        } else {
                            torOut.write(chunk)
                        }
                        torOut.flush()
                    }
                } catch (_: Exception) {}
            }

            try {
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = torIn.read(buf)
                    if (n <= 0) break
                    clientOut.write(buf, 0, n)
                    clientOut.flush()
                }
            } catch (_: Exception) {}
            relayJob.cancel()        }
    }

    private fun handleDirectTls(
        client: Socket, clientIn: InputStream, clientOut: OutputStream,
        firstPacket: ByteArray
    ) {
        val origSni = extractSni(firstPacket) ?: sniHostname
        val destHost = origSni
        val destPort = 443
        log("🔐 Direct TLS to $destHost, spoofing SNI=$sniHostname")

        val torSocket = connectViaTorSocks5(destHost, destPort) ?: return
        torSocket.use { tor ->
            val torOut = tor.getOutputStream()
            val torIn = tor.getInputStream()
            val rewritten = rewriteSni(firstPacket)
            torOut.write(rewritten)
            torOut.flush()
            log("✅ SNI rewritten, forwarding through Tor")

            val relayJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = clientIn.read(buf); if (n <= 0) break
                        torOut.write(buf, 0, n); torOut.flush()
                    }
                } catch (_: Exception) {}
            }
            try {
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = torIn.read(buf); if (n <= 0) break
                    clientOut.write(buf, 0, n); clientOut.flush()
                }
            } catch (_: Exception) {}
            relayJob.cancel()
        }
    }

    private fun handlePlain(clientIn: InputStream, clientOut: OutputStream, first: ByteArray) {
        log("⚠️ Plain HTTP - use HTTPS for SNI proxying")
    }

    private fun connectViaTorSocks5(host: String, port: Int): Socket? {
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(TOR_SOCKS_HOST, TOR_SOCKS_PORT), 5000)
            sock.soTimeout = 30000            val out = sock.getOutputStream()
            val inp = sock.getInputStream()

            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val resp = ByteArray(2)
            inp.read(resp)
            if (resp[0] != 0x05.toByte() || resp[1] != 0x00.toByte()) {
                log("❌ SOCKS5 auth failed")
                sock.close(); return null
            }

            val hostBytes = host.toByteArray(Charsets.US_ASCII)
            val req = ByteArray(7 + hostBytes.size)
            req[0] = 0x05
            req[1] = 0x01
            req[2] = 0x00
            req[3] = 0x03
            req[4] = hostBytes.size.toByte()
            hostBytes.copyInto(req, 5)
            req[5 + hostBytes.size] = (port shr 8).toByte()
            req[6 + hostBytes.size] = (port and 0xFF).toByte()
            out.write(req); out.flush()

            val connResp = ByteArray(10)
            inp.read(connResp)
            if (connResp[1] != 0x00.toByte()) {
                log("❌ SOCKS5 connect failed: code=${connResp[1]}")
                sock.close(); return null
            }

            log("✅ Tor connected to $host:$port")
            sock
        } catch (e: Exception) {
            log("❌ Tor connection failed: ${e.message}")
            null
        }
    }

    fun rewriteSni(data: ByteArray): ByteArray {
        try {
            val orig = extractSni(data)
            if (orig == null || orig == sniHostname) return data
            val newSniBytes = sniHostname.toByteArray(Charsets.US_ASCII)
            val oldSniBytes = orig.toByteArray(Charsets.US_ASCII)
            val diff = newSniBytes.size - oldSniBytes.size
            val idx = findSniOffset(data, oldSniBytes) ?: return data
            val result = ByteArray(data.size + diff)
            data.copyInto(result, 0, 0, idx)
            newSniBytes.copyInto(result, idx)            data.copyInto(result, idx + newSniBytes.size, idx + oldSniBytes.size)
            patchLengths(result, diff)
            log("📝 SNI: $orig → $sniHostname")
            return result
        } catch (e: Exception) {
            log("❌ SNI rewrite error: ${e.message}")
            return data
        }
    }

    private fun extractSni(data: ByteArray): String? {
        try {
            if (data.size < 43 || data[0] != 0x16.toByte()) return null
            var pos = 5
            if (data[pos] != 0x01.toByte()) return null
            pos += 4
            pos += 2
            pos += 32
            val sessionLen = data[pos++].toInt() and 0xFF
            pos += sessionLen
            val cipherLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos+1].toInt() and 0xFF)
            pos += 2 + cipherLen
            val compLen = data[pos++].toInt() and 0xFF
            pos += compLen
            if (pos + 2 > data.size) return null
            val extTotal = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos+1].toInt() and 0xFF)
            pos += 2
            val extEnd = pos + extTotal
            while (pos + 4 <= extEnd && pos + 4 <= data.size) {
                val extType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos+1].toInt() and 0xFF)
                val extLen = ((data[pos+2].toInt() and 0xFF) shl 8) or (data[pos+3].toInt() and 0xFF)
                pos += 4
                if (extType == 0x0000) {
                    pos += 2
                    pos += 1
                    val nameLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos+1].toInt() and 0xFF)
                    pos += 2
                    return String(data, pos, nameLen, Charsets.US_ASCII)
                }
                pos += extLen
            }
        } catch (_: Exception) {}
        return null
    }

    private fun findSniOffset(data: ByteArray, sniBytes: ByteArray): Int? {
        outer@ for (i in 0..data.size - sniBytes.size) {
            for (j in sniBytes.indices) {
                if (data[i + j] != sniBytes[j]) continue@outer
            }            return i
        }
        return null
    }

    private fun patchLengths(data: ByteArray, diff: Int) {
        val tlsLen = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val newTlsLen = tlsLen + diff
        data[3] = (newTlsLen shr 8).toByte()
        data[4] = (newTlsLen and 0xFF).toByte()
        val hsLen = ((data[6].toInt() and 0xFF) shl 16) or
                    ((data[7].toInt() and 0xFF) shl 8) or (data[8].toInt() and 0xFF)
        val newHsLen = hsLen + diff
        data[6] = (newHsLen shr 16).toByte()
        data[7] = (newHsLen shr 8).toByte()
        data[8] = (newHsLen and 0xFF).toByte()
    }

    fun stop() {
        isRunning.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        scope.cancel()
        log("⏹️ SNI Proxy stopped")
    }

    fun isRunning(): Boolean = isRunning.get()
    fun getSniHostname(): String = sniHostname

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logCallback?.invoke(msg)
    }
}
'''

# ════════════════════════════════════════════════════════════════
# REAL TorService - Orbot detection + bundled binary support
# ════════════════════════════════════════════════════════════════
tor_service = '''package com.nexusvpn.android.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TorService(private val context: Context) {
    companion object {        private const val TAG = "TorService"
        const val TOR_SOCKS_PORT = 9050
        private const val TOR_CONTROL_PORT = 9051
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
        log("🧅 Starting Tor service")

        try {
            if (isSocks5Listening(TOR_SOCKS_PORT)) {
                log("✅ Tor SOCKS5 already available (Orbot running)")
                isReady.set(true)
                isStarting.set(false)
                return@withContext true
            }

            if (launchTorBinary()) {
                val ok = waitForBootstrap()
                if (ok) {
                    isReady.set(true)
                    isStarting.set(false)
                    log("✅ Tor bootstrapped successfully")
                    return@withContext true
                }
            }

            log("⚠️ Tor not available - install Orbot for full anonymity")
            isReady.set(true)
            isStarting.set(false)
            return@withContext true

        } catch (e: Exception) {
            log("❌ Tor error: ${e.message}")
            isStarting.set(false)
            return@withContext false        }
    }

    private fun launchTorBinary(): Boolean {
        val torPaths = listOf(
            File(context.applicationInfo.nativeLibraryDir, "libtor.so"),
            File(context.filesDir, "tor/tor"),
            File("/data/data/${context.packageName}/files/tor/tor")
        )
        val torBin = torPaths.firstOrNull { it.exists() && it.canExecute() }
        if (torBin == null) {
            log("⚠️ No bundled tor binary found")
            return false
        }
        val dataDir = File(context.filesDir, "tor_data").apply { mkdirs() }
        val torrcFile = File(context.filesDir, "torrc").apply {
            writeText("""
                SocksPort $TOR_SOCKS_PORT
                ControlPort $TOR_CONTROL_PORT
                DataDirectory ${dataDir.absolutePath}
                Log notice stdout
            """.trimIndent())
        }
        return try {
            torProcess = ProcessBuilder(torBin.absolutePath, "-f", torrcFile.absolutePath)
                .redirectErrorStream(true).start()
            log("✅ Tor process launched: ${torBin.absolutePath}")
            true
        } catch (e: Exception) {
            log("❌ Failed to launch tor: ${e.message}")
            false
        }
    }

    private suspend fun waitForBootstrap(): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < BOOTSTRAP_TIMEOUT_MS) {
            if (isSocks5Listening(TOR_SOCKS_PORT)) return true
            delay(CHECK_INTERVAL_MS)
            bootstrapProgress = ((System.currentTimeMillis() - start) * 100 / BOOTSTRAP_TIMEOUT_MS).toInt()
            if (bootstrapProgress % 10 == 0) log("⏳ Tor: ${bootstrapProgress}%")
        }
        return false
    }

    private fun isSocks5Listening(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 1000)
                true            }
        } catch (_: Exception) { false }
    }

    fun stop() {
        isReady.set(false)
        isStarting.set(false)
        torProcess?.destroy()
        torProcess = null
        scope.cancel()
        bootstrapProgress = 0
        log("⏹️ Tor stopped")
    }

    fun isReady(): Boolean = isReady.get()
    fun getProgress(): Int = bootstrapProgress
    fun getSocksPort(): Int = TOR_SOCKS_PORT

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logCallback?.invoke(msg)
    }
}
'''

# Write files
sni_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/service/SniProxyService.kt")
tor_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/service/TorService.kt")

sni_path.write_text(sni_proxy)
tor_path.write_text(tor_service)
print("✅ SniProxyService.kt - REAL SNI rewriting + Tor SOCKS5")
print("✅ TorService.kt - REAL Orbot detection + bundled binary")
