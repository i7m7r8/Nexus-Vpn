package com.nexusvpn.android

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.nexusvpn.android.service.NexusVpnService

// Modern dark theme colors
val ProtonViolet = Color(0xFF6D4AFF)
val ProtonDarkBg = Color(0xFF0F0D1A)
val ProtonCardBg = Color(0xFF1A1625)
val ProtonGreen = Color(0xFF00E676)
val ProtonOrange = Color(0xFFFF9500)
val ProtonRed = Color(0xFFFF5555)
val ProtonGray = Color(0xFF8B8B8B)
val ProtonLightGray = Color(0xFFB0B0B0)

class MainActivity : ComponentActivity() {
    // State variables
    private var isVpnConnected by mutableStateOf(false)
    private var currentServer by mutableStateOf("US - New York")
    private var currentCountry by mutableStateOf("🇺🇸")
    private var currentSpeed by mutableStateOf("0.0 Mbps")
    private var currentIp by mutableStateOf("--")
    private var bytesTransferred by mutableStateOf("0 B")
    private var connectionTime by mutableStateOf("00:00:00")
    private var showServerList by mutableStateOf(false)
    private var showAdvancedSettings by mutableStateOf(false)
    private var showSniPanel by mutableStateOf(false)
    private var selectedProtocol by mutableStateOf("UDP")
    private var sniEnabled by mutableStateOf(false)
    private var torEnabled by mutableStateOf(false)
    private var killSwitchEnabled by mutableStateOf(true)
    private var splitTunnelingEnabled by mutableStateOf(false)
    private var autoReconnectEnabled by mutableStateOf(true)
    private var customSniHostname by mutableStateOf("")
    private var showConnectionLogs by mutableStateOf(false)
    private var torStatus by mutableStateOf("Not Started")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ProtonViolet,
                    secondary = ProtonGreen,
                    tertiary = ProtonOrange,
                    error = ProtonRed,
                    background = ProtonDarkBg,
                    surface = ProtonCardBg
                )
            ) {
                NexusVpnApp(
                    isVpnConnected = isVpnConnected,
                    currentServer = currentServer,
                    currentCountry = currentCountry,
                    currentSpeed = currentSpeed,
                    currentIp = currentIp,
                    bytesTransferred = bytesTransferred,
                    connectionTime = connectionTime,
                    torStatus = torStatus,
                    onConnectClick = { connectVpn() },
                    onDisconnectClick = { disconnectVpn() },
                    onServerSelectClick = { showServerList = true },
                    onAdvancedSettingsClick = { showAdvancedSettings = true },
                    onSniPanelClick = { showSniPanel = true },
                    onConnectionLogsClick = { showConnectionLogs = true },
                    showServerList = showServerList,
                    onServerListDismiss = { showServerList = false },
                    onServerSelected = { server, flag ->
                        currentServer = server
                        currentCountry = flag
                        showServerList = false
                    },
                    showAdvancedSettings = showAdvancedSettings,
                    onAdvancedSettingsDismiss = { showAdvancedSettings = false },
                    selectedProtocol = selectedProtocol,
                    onProtocolChange = { selectedProtocol = it },
                    killSwitchEnabled = killSwitchEnabled,
                    onKillSwitchChange = { killSwitchEnabled = it },
                    splitTunnelingEnabled = splitTunnelingEnabled,
                    onSplitTunnelingChange = { splitTunnelingEnabled = it },
                    autoReconnectEnabled = autoReconnectEnabled,
                    onAutoReconnectChange = { autoReconnectEnabled = it },
                    showSniPanel = showSniPanel,
                    onSniPanelDismiss = { showSniPanel = false },
                    sniEnabled = sniEnabled,
                    onSniEnabledChange = { enabled ->
                        sniEnabled = enabled
                        if (isVpnConnected) updateServiceConfig()
                    },
                    torEnabled = torEnabled,
                    onTorEnabledChange = { enabled ->
                        torEnabled = enabled
                        torStatus = if (enabled) "Starting..." else "Stopped"
                        if (isVpnConnected) updateServiceConfig()
                    },
                    customSniHostname = customSniHostname,
                    onCustomSniHostnameChange = { hostname ->
                        customSniHostname = hostname
                        if (isVpnConnected) updateServiceConfig()
                    },
                    showConnectionLogs = showConnectionLogs,
                    onConnectionLogsDismiss = { showConnectionLogs = false }
                )
            }
        }

        // Start monitoring service
        startMonitoringService()
    }

    private fun updateServiceConfig() {
        val intent = Intent(this, NexusVpnService::class.java).apply {
            action = "UPDATE_CONFIG"
            putExtra("sni_enabled", sniEnabled)
            putExtra("tor_enabled", torEnabled)
            putExtra("custom_sni", customSniHostname)
        }
        startService(intent)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
        )
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun connectVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            onActivityResult(0, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val serviceIntent = Intent(this, NexusVpnService::class.java).apply {
                action = "CONNECT"
                putExtra("server", currentServer)
                putExtra("protocol", selectedProtocol)
                putExtra("sni_enabled", sniEnabled)
                putExtra("tor_enabled", torEnabled)
                putExtra("custom_sni", customSniHostname)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            isVpnConnected = true
            currentIp = "Connecting..."
            torStatus = if (torEnabled) "Starting Tor..." else "Disabled"
            simulateConnectionStats()
        }
    }

    private fun disconnectVpn() {
        val serviceIntent = Intent(this, NexusVpnService::class.java).apply {
            action = "DISCONNECT"
        }
        startService(serviceIntent)
        isVpnConnected = false
        currentIp = "--"
        currentSpeed = "0.0 Mbps"
        bytesTransferred = "0 B"
        connectionTime = "00:00:00"
        torStatus = if (torEnabled) "Stopped" else "Disabled"
    }

    private fun simulateConnectionStats() {
        var seconds = 0
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isVpnConnected) {
                    seconds++
                    val hours = seconds / 3600
                    val mins = (seconds % 3600) / 60
                    val secs = seconds % 60
                    connectionTime = String.format("%02d:%02d:%02d", hours, mins, secs)
                    currentSpeed = "${(Math.random() * 100 + 50).toInt()}.${(Math.random() * 10).toInt()} Mbps"
                    bytesTransferred = "${(Math.random() * 1000 + 100).toLong()} MB"
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(runnable)
    }

    private fun startMonitoringService() {
        val intent = Intent(this, NexusVpnService::class.java).apply {
            action = "MONITOR"
        }
        startService(intent)
    }
}

@Composable
fun NexusVpnApp(
    isVpnConnected: Boolean,
    currentServer: String,
    currentCountry: String,
    currentSpeed: String,
    currentIp: String,
    bytesTransferred: String,
    connectionTime: String,
    torStatus: String,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onServerSelectClick: () -> Unit,
    onAdvancedSettingsClick: () -> Unit,
    onSniPanelClick: () -> Unit,
    onConnectionLogsClick: () -> Unit,
    showServerList: Boolean,
    onServerListDismiss: () -> Unit,
    onServerSelected: (String, String) -> Unit,
    showAdvancedSettings: Boolean,
    onAdvancedSettingsDismiss: () -> Unit,
    selectedProtocol: String,
    onProtocolChange: (String) -> Unit,
    killSwitchEnabled: Boolean,
    onKillSwitchChange: (Boolean) -> Unit,
    splitTunnelingEnabled: Boolean,
    onSplitTunnelingChange: (Boolean) -> Unit,
    autoReconnectEnabled: Boolean,
    onAutoReconnectChange: (Boolean) -> Unit,
    showSniPanel: Boolean,
    onSniPanelDismiss: () -> Unit,
    sniEnabled: Boolean,
    onSniEnabledChange: (Boolean) -> Unit,
    torEnabled: Boolean,
    onTorEnabledChange: (Boolean) -> Unit,
    customSniHostname: String,
    onCustomSniHostnameChange: (String) -> Unit,
    showConnectionLogs: Boolean,
    onConnectionLogsDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProtonDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with logo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NEXUS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtonViolet
                )
                Text(
                    "VPN",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Box(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Connection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = ProtonCardBg),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Connection Status
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = if (isVpnConnected) ProtonGreen.copy(alpha = 0.15f) else ProtonRed.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            if (isVpnConnected) "● CONNECTED" else "○ DISCONNECTED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isVpnConnected) ProtonGreen else ProtonRed,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // IP Display
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            "YOUR IP",
                            fontSize = 11.sp,
                            color = ProtonGray,
                            letterSpacing = 1.sp
                        )
                        Text(
                            currentIp,
                            fontSize = 24.sp,
                            color = ProtonViolet,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Large Connect/Disconnect Button
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = if (isVpnConnected) onDisconnectClick else onConnectClick,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(60.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVpnConnected) ProtonRed else ProtonGreen
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 8.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Icon(
                                if (isVpnConnected) Icons.Filled.PowerOff else Icons.Filled.VpnKey,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Server Info
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !isVpnConnected) { onServerSelectClick() },
                        color = ProtonDarkBg,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        currentCountry,
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        currentServer,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    "Recommended for you",
                                    fontSize = 12.sp,
                                    color = ProtonGray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = ProtonViolet,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard("SPEED", currentSpeed)
                        StatCard("DATA", bytesTransferred)
                        StatCard("TIME", connectionTime)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tor Status (if enabled)
                    if (torEnabled) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ProtonViolet.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Security,
                                        contentDescription = null,
                                        tint = ProtonViolet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tor Circuit", fontSize = 12.sp, color = ProtonLightGray)
                                }
                                Text(torStatus, fontSize = 12.sp, color = if (torStatus.contains("Running")) ProtonGreen else ProtonOrange)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Settings Section
            Text(
                "QUICK SETTINGS",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProtonViolet,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )

            // Protocol Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ProtonCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Protocol", fontSize = 12.sp, color = ProtonGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("UDP", "TCP", "SNI", "Tor").forEach { protocol ->
                            FilterChip(
                                selected = selectedProtocol == protocol,
                                onClick = { onProtocolChange(protocol) },
                                label = { Text(protocol, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ProtonViolet,
                                    selectedLabelColor = Color.White,
                                    disabledContainerColor = ProtonCardBg,
                                    disabledLabelColor = ProtonGray
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle Settings
            ToggleSettingCard("Kill Switch", killSwitchEnabled, onKillSwitchChange, "Prevent traffic leaks if VPN drops")
            ToggleSettingCard("Auto Reconnect", autoReconnectEnabled, onAutoReconnectChange, "Reconnect automatically on network change")
            ToggleSettingCard("Split Tunneling", splitTunnelingEnabled, onSplitTunnelingChange, "Select apps to bypass VPN")

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            ActionButton(
                onClick = onSniPanelClick,
                icon = Icons.Filled.Tune,
                text = "SNI & Tor Settings",
                description = "Configure Server Name Indication and Tor"
            )
            ActionButton(
                onClick = onAdvancedSettingsClick,
                icon = Icons.Filled.Settings,
                text = "Advanced Settings",
                description = "DNS, IPv6, WebRTC leak protection"
            )
            ActionButton(
                onClick = onConnectionLogsClick,
                icon = Icons.Filled.History,
                text = "Connection Logs",
                description = "View connection history and events"
            )
        }

        // Modals
        if (showServerList) {
            ServerListModal(
                onDismiss = onServerListDismiss,
                onServerSelected = onServerSelected
            )
        }

        if (showSniPanel) {
            SniPanelModal(
                onDismiss = onSniPanelDismiss,
                sniEnabled = sniEnabled,
                onSniEnabledChange = onSniEnabledChange,
                torEnabled = torEnabled,
                onTorEnabledChange = onTorEnabledChange,
                customSniHostname = customSniHostname,
                onCustomSniHostnameChange = onCustomSniHostnameChange
            )
        }

        if (showAdvancedSettings) {
            AdvancedSettingsModal(onDismiss = onAdvancedSettingsDismiss)
        }

        if (showConnectionLogs) {
            ConnectionLogsModal(onDismiss = onConnectionLogsDismiss)
        }
    }
}

@Composable
fun StatCard(label: String, value: String) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = ProtonDarkBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = ProtonGray, letterSpacing = 0.5.sp)
            Text(
                value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ProtonGreen,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
fun ToggleSettingCard(label: String, enabled: Boolean, onToggle: (Boolean) -> Unit, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProtonCardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(description, fontSize = 11.sp, color = ProtonGray, maxLines = 1)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ProtonGreen,
                    checkedTrackColor = ProtonGreen.copy(alpha = 0.3f),
                    uncheckedThumbColor = ProtonGray,
                    uncheckedTrackColor = ProtonCardBg
                )
            )
        }
    }
}

@Composable
fun ActionButton(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = ProtonCardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ProtonViolet, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(description, fontSize = 11.sp, color = ProtonGray, maxLines = 1)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ProtonGray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ServerListModal(
    onDismiss: () -> Unit,
    onServerSelected: (String, String) -> Unit
) {
    val servers = listOf(
        "US - New York" to "🇺🇸",
        "US - Los Angeles" to "🇺🇸",
        "US - Miami" to "🇺🇸",
        "UK - London" to "🇬🇧",
        "DE - Berlin" to "🇩🇪",
        "FR - Paris" to "🇫🇷",
        "JP - Tokyo" to "🇯🇵",
        "SG - Singapore" to "🇸🇬",
        "AU - Sydney" to "🇦🇺",
        "CA - Toronto" to "🇨🇦",
        "BR - Sao Paulo" to "🇧🇷",
        "IN - Mumbai" to "🇮🇳"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Server", color = ProtonViolet, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(servers) { (server, flag) ->
                    TextButton(
                        onClick = { onServerSelected(server, flag) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(flag, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(server, color = Color.White)
                        }
                    }
                    Divider(color = ProtonCardBg)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ProtonViolet)
            }
        },
        containerColor = ProtonCardBg,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun SniPanelModal(
    onDismiss: () -> Unit,
    sniEnabled: Boolean,
    onSniEnabledChange: (Boolean) -> Unit,
    torEnabled: Boolean,
    onTorEnabledChange: (Boolean) -> Unit,
    customSniHostname: String,
    onCustomSniHostnameChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SNI & Tor Settings", color = ProtonViolet, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Tor Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ProtonDarkBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tor Integration", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Route all traffic through the Tor network", fontSize = 11.sp, color = ProtonGray)
                        }
                        Switch(checked = torEnabled, onCheckedChange = onTorEnabledChange)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SNI Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ProtonDarkBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("SNI Spoofing", color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Hide VPN traffic by mimicking normal HTTPS", fontSize = 11.sp, color = ProtonGray)
                            }
                            Switch(checked = sniEnabled, onCheckedChange = onSniEnabledChange)
                        }

                        if (sniEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customSniHostname,
                                onValueChange = onCustomSniHostnameChange,
                                label = { Text("Custom SNI Hostname", color = ProtonGray) },
                                placeholder = { Text("e.g., www.google.com", color = ProtonGray.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ProtonViolet,
                                    unfocusedBorderColor = ProtonCardBg,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Info Card
                Surface(
                    color = ProtonViolet.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = ProtonViolet, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SNI spoofing helps bypass DPI by making your VPN traffic look like regular HTTPS. "
                            + "Tor provides complete anonymity through onion routing.",
                            fontSize = 11.sp,
                            color = ProtonLightGray
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Apply", color = ProtonViolet)
            }
        },
        containerColor = ProtonCardBg,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun AdvancedSettingsModal(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Advanced Settings", color = ProtonViolet, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(listOf(
                    "DNS Leak Protection" to true,
                    "IPv6 Leak Prevention" to true,
                    "WebRTC Leak Blocking" to true,
                    "Perfect Forward Secrecy" to true,
                    "TLS 1.3 Only" to false,
                    "Allow Local Network" to false,
                    "Custom DNS Servers" to false,
                    "Quantum Resistance" to false
                )) { (setting, enabled) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(setting, fontSize = 13.sp, color = Color.White)
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = {},
                            colors = CheckboxDefaults.colors(checkedColor = ProtonGreen)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Save", color = ProtonViolet)
            }
        },
        containerColor = ProtonCardBg,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun ConnectionLogsModal(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection Logs", color = ProtonViolet, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(listOf(
                    "[00:00:01] VPN service starting",
                    "[00:00:02] SNI TLS handshake complete",
                    "[00:00:03] Connected to $currentServer",
                    "[00:00:04] Kill switch enabled",
                    "[00:00:05] Tor circuit building",
                    "[00:00:07] Tor circuit established",
                    "[00:00:08] Traffic routing active",
                    "[00:05:23] Auto-reconnect triggered",
                    "[00:05:24] New circuit built",
                    "[10:23:45] Connection stable - 95.2 Mbps"
                )) { log ->
                    Text(
                        log,
                        fontSize = 11.sp,
                        color = ProtonGray,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ProtonViolet)
            }
        },
        containerColor = ProtonCardBg,
        shape = RoundedCornerShape(24.dp)
    )
}
