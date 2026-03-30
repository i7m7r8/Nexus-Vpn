// ============================================================================
// NEXUS VPN - MainActivity with SNI→Tor Chain Integration
// ============================================================================

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexusvpn.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexusvpn.android.service.NexusVpnService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Color Scheme (Proton VPN Inspired)
val ProtonDarkPrimary = Color(0xFF6F02B5)
val ProtonLightPrimary = Color(0xFF8B2BE2)
val ProtonAccent = Color(0xFFFFA500)val DarkBg = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF1A1A1A)
val DarkSurfaceVariant = Color(0xFF2A2A2A)

class MainActivity : ComponentActivity() {
    private val sharedPrefs: SharedPreferences by lazy {
        getSharedPreferences("nexus_vpn_prefs", Context.MODE_PRIVATE)
    }

    private var vpnConnected = mutableStateOf(false)
    private var currentServer = mutableStateOf("Select Server")
    private var sniHostname = mutableStateOf("")
    private var torEnabled = mutableStateOf(true)  // DEFAULT: TOR ENABLED FOR SNI→TOR CHAIN!
    private var connectionSpeed = mutableStateOf("0 Mbps")
    private var connectionLatency = mutableStateOf("-- ms")
    private var dataUsed = mutableStateOf("0 B")
    private var connectionTime = mutableStateOf("00:00:00")
    private var currentScreen = mutableStateOf("HOME")
    private var connectionStatus = mutableStateOf("Not Connected")
    private var killSwitchEnabled = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadPreferences()
        requestVpnPermissions()

        setContent {
            NexusVpnTheme {
                NexusVpnApp(
                    vpnConnected = vpnConnected.value,
                    onVpnToggle = { toggleVpn() },
                    currentServer = currentServer.value,
                    onServerChange = { server -> updateServer(server) },
                    sniHostname = sniHostname.value,
                    onSniChange = { hostname -> updateSniHostname(hostname) },
                    torEnabled = torEnabled.value,
                    onTorToggle = { torEnabled.value = it },
                    connectionSpeed = connectionSpeed.value,
                    connectionLatency = connectionLatency.value,
                    dataUsed = dataUsed.value,
                    connectionTime = connectionTime.value,
                    connectionStatus = connectionStatus.value,
                    currentScreen = currentScreen.value,
                    onScreenChange = { screen -> currentScreen.value = screen },
                    killSwitchEnabled = killSwitchEnabled.value,
                    onKillSwitchChange = { killSwitchEnabled.value = it }
                )
            }
        }
        lifecycleScope.launch {
            startStatsUpdater()
        }
    }

    private fun loadPreferences() {
        sniHostname.value = sharedPrefs.getString("sni_hostname", "") ?: ""
        torEnabled.value = sharedPrefs.getBoolean("tor_enabled", true)  // DEFAULT: TRUE
        currentServer.value = sharedPrefs.getString("last_server", "🇺🇸 New York - Fast") ?: "🇺🇸 New York - Fast"
        killSwitchEnabled.value = sharedPrefs.getBoolean("kill_switch", true)
    }

    private fun savePreferences() {
        with(sharedPrefs.edit()) {
            putString("sni_hostname", sniHostname.value)
            putBoolean("tor_enabled", torEnabled.value)
            putString("last_server", currentServer.value)
            putBoolean("kill_switch", killSwitchEnabled.value)
            apply()
        }
    }

    private fun toggleVpn() {
        vpnConnected.value = !vpnConnected.value
        if (vpnConnected.value) {
            connectionStatus.value = "Connecting..."
            startVpnConnection()
        } else {
            connectionStatus.value = "Disconnected"
            stopVpnConnection()
        }
        savePreferences()
    }

    private fun updateServer(server: String) {
        currentServer.value = server
        savePreferences()
        if (vpnConnected.value) {
            toggleVpn()
            toggleVpn()
        }
    }

    private fun updateSniHostname(hostname: String) {
        sniHostname.value = hostname
        savePreferences()
    }

    // ============================================================================    // ======================== VPN CONNECTION (SNI→TOR CHAIN) ====================
    // ============================================================================
    
    private fun startVpnConnection() {
        // STEP 1: Get VPN permission from Android system
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_PERMISSION_REQUEST)
        } else {
            // STEP 2: Permission granted, start the SNI→Tor chain service
            connectToVpnService()
        }
    }

    private fun connectToVpnService() {
        val intent = Intent(this, NexusVpnService::class.java).apply {
            action = NexusVpnService.ACTION_CONNECT
            
            // CRITICAL: Pass SNI→Tor configuration to service
            putExtra("sni_hostname", sniHostname.value)
            putExtra("tor_enabled", torEnabled.value)  // MUST BE TRUE FOR SNI→TOR CHAIN!
            putExtra("kill_switch", killSwitchEnabled.value)
            putExtra("server_id", currentServer.value)
            
            Log.d("MainActivity", "Starting VPN: SNI=${sniHostname.value}, Tor=${torEnabled.value}")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        connectionStatus.value = "Connected"
    }

    private fun stopVpnConnection() {
        val intent = Intent(this, NexusVpnService::class.java).apply {
            action = NexusVpnService.ACTION_DISCONNECT
        }
        stopService(intent)
    }

    private suspend fun startStatsUpdater() {
        while (true) {
            if (vpnConnected.value) {
                // These would come from Rust core via JNI in production
                connectionSpeed.value = "${(50..500).random()} Mbps"
                connectionLatency.value = "${(10..200).random()} ms"
                dataUsed.value = formatBytes((0..1073741824L).random())            }
            kotlinx.coroutines.delay(2000)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun requestVpnPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.BIND_VPN_SERVICE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.QUERY_ALL_PACKAGES,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION_REQUEST && resultCode == RESULT_OK) {
            connectToVpnService()
        } else {
            vpnConnected.value = false
            connectionStatus.value = "Permission Denied"
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val VPN_PERMISSION_REQUEST = 101
    }}

// ... [Rest of the Compose UI code stays the same - it's already perfect!]
// Just add killSwitchEnabled parameter to the composables

@Composable
fun NexusVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ProtonDarkPrimary,
            secondary = ProtonLightPrimary,
            tertiary = ProtonAccent,
            background = DarkBg,
            surface = DarkSurface,
            error = Color.Red
        ),
        typography = Typography(),
        content = content
    )
}

// [Continue with existing Compose UI code...]
// The UI is already perfect, just pass the new parameters
