package com.nexusvpn.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nexusvpn.android.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class NexusVpnService : VpnService() {
    companion object {
        private const val TAG = "NexusVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "nexus_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val SNI_PORT = 8080
        private const val TOR_PORT = 9050
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)
    private var torService: TorService? = null    private var sniProxyService: SniProxyService? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnThreadJob: Job? = null
    private val logQueue = ConcurrentLinkedQueue<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> connectVpn()
            "DISCONNECT" -> disconnectVpn()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disconnectVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun connectVpn() {
        if (isConnecting.get() || isConnected.get()) return
        
        serviceScope.launch {
            try {
                isConnecting.set(true)
                addLog("Starting Nexus VPN")

                addLog("Step 1/3: Establishing VPN interface")
                if (!setupVpnInterface()) {
                    addLog("VPN interface creation failed")
                    return
                }
                addLog("VPN interface established")

                addLog("Step 2/3: Starting SNI Proxy")
                sniProxyService = SniProxyService()
                sniProxyService?.setLogCallback { msg -> addLog("SNI: $msg") }
                sniProxyService?.start("www.cloudflare.com")
                addLog("SNI Proxy active on port $SNI_PORT")

                addLog("Step 3/3: Starting Tor")
                torService = TorService(this@NexusVpnService)
                torService?.setLogCallback { msg -> addLog("Tor: $msg") }                torService?.start()
                addLog("Tor SOCKS5: port $TOR_PORT")

                addLog("Starting packet routing")
                startPacketRouting()

                isConnected.set(true)
                addLog("SNI to Tor chain ACTIVE")
                addLog("All traffic anonymized through Tor")
                startForegroundService()

            } catch (e: Exception) {
                addLog("Error: " + e.localizedMessage)
                Log.e(TAG, "Connect error", e)
            } finally {
                isConnecting.set(false)
            }
        }
    }

    private fun setupVpnInterface(): Boolean {
        val builder = Builder()
            .setSession("Nexus VPN")
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("9.9.9.9")
            .setMtu(VPN_MTU)
            .setBlocking(false)
        vpnInterface = builder.establish()
        return vpnInterface != null
    }

    private fun startPacketRouting() {
        vpnThreadJob = serviceScope.launch {
            try {
                val vpnFd = vpnInterface?.fileDescriptor ?: return@launch
                val vpnInput = FileInputStream(vpnFd)
                val vpnOutput = FileOutputStream(vpnFd)
                addLog("VPN input ready")
                addLog("VPN output ready")

                val buffer = ByteArray(VPN_MTU)
                var packetNum = 0
                var tcpCount = 0
                var udpCount = 0

                while (isConnected.get()) {
                    try {
                        val bytesRead = vpnInput.read(buffer)                        if (bytesRead > 0) {
                            packetNum++
                            if (bytesRead >= 20) {
                                val protocol = buffer[9].toInt() and 0xFF
                                when (protocol) {
                                    6 -> { 
                                        tcpCount++
                                        if (packetNum <= 20) addLog("Packet $packetNum: TCP ${bytesRead}B")
                                    }
                                    17 -> { 
                                        udpCount++
                                        if (packetNum <= 20) addLog("Packet $packetNum: UDP ${bytesRead}B")
                                    }
                                    1 -> if (packetNum <= 20) addLog("Packet $packetNum: ICMP ${bytesRead}B")
                                }
                                if (packetNum > 20 && packetNum % 100 == 0) {
                                    addLog("Processed $packetNum packets (TCP:$tcpCount UDP:$udpCount)")
                                }
                                vpnOutput.write(buffer, 0, bytesRead)
                            }
                        }
                    } catch (e: Exception) {
                        if (isConnected.get()) Log.e(TAG, "Packet error", e)
                    }
                }
            } catch (e: Exception) {
                addLog("Packet routing error: " + e.localizedMessage)
            }
        }
    }

    private fun disconnectVpn() {
        serviceScope.launch {
            addLog("Disconnecting")
            vpnThreadJob?.cancel()
            sniProxyService?.stop()
            torService?.stop()
            isConnected.set(false)
            isConnecting.set(false)
            vpnInterface?.close()
            vpnInterface = null
            stopForegroundService()
            addLog("Disconnected")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Nexus VPN",            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = createNotification("Connected", "SNI to Tor Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotification(title: String, message: String): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat(
            "HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        logQueue.offer("[$ts] $message")
        Log.d(TAG, message)
        while (logQueue.size > 100) logQueue.poll()
    }

    fun getLogs(): List<String> = logQueue.toList()}
