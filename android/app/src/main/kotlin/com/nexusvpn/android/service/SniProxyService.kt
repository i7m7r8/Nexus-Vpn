package com.nexusvpn.android.service

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

class SniProxyService(private val context: Context) {
    companion object {
        private const val TAG = "SniProxyService"
        private const val PROXY_PORT = 8080
    }

    private var isRunning = AtomicBoolean(false)
    private var sniHostname: String = ""
    private var serverThread: Thread? = null

    fun start(hostname: String = ""): Boolean {
        if (isRunning.get()) {
            return true
        }

        try {
            sniHostname = hostname.ifEmpty { generateRandomSni() }
            isRunning.set(true)
            
            Log.d(TAG, "SNI Proxy started")
            Log.d(TAG, "SNI Hostname: $sniHostname")
            
            serverThread = Thread {
                runProxyServer()
            }
            serverThread?.start()
            
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SNI Proxy", e)
            isRunning.set(false)
            return false
        }
    }
    private fun runProxyServer() {
        try {
            val serverSocket = ServerSocketChannel.open()
            serverSocket.socket().bind(InetSocketAddress("127.0.0.1", PROXY_PORT))
            serverSocket.configureBlocking(true)
            
            Log.d(TAG, "SNI Proxy listening on 127.0.0.1:$PROXY_PORT")
            
            while (isRunning.get()) {
                try {
                    val client = serverSocket.accept()
                    handleClient(client)
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error accepting client", e)
                    }
                }
            }
            
            serverSocket.close()
            Log.d(TAG, "SNI Proxy server stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "SNI Proxy server error", e)
        }
    }

    private fun handleClient(client: SocketChannel) {
        Log.d(TAG, "New SNI Proxy client connection")
        
        try {
            val buffer = ByteBuffer.allocate(4096)
            val bytesRead = client.read(buffer)
            
            if (bytesRead > 5) {
                buffer.flip()
                
                if (buffer.get() == 0x16.toByte() &&
                    buffer.get() == 0x03.toByte() &&
                    buffer.get() <= 0x03.toByte()
                ) {
                    Log.d(TAG, "TLS Client Hello detected")
                    Log.d(TAG, "SNI hostname: $sniHostname")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling SNI client", e)
        } finally {
            try { client.close() } catch (e: Exception) {}        }
    }

    fun stop() {
        Log.d(TAG, "Stopping SNI Proxy")
        isRunning.set(false)
        serverThread?.interrupt()
        serverThread = null
        sniHostname = ""
    }

    fun isRunning(): Boolean = isRunning.get()
    fun getSniHostname(): String = sniHostname

    private fun generateRandomSni(): String {
        val domains = listOf(
            "cdn.cloudflare.com",
            "www.microsoft.com",
            "update.google.com",
            "api.github.com"
        )
        return domains.random()
    }
}
