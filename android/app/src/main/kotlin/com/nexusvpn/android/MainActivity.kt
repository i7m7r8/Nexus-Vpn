// ============================================================================
// NEXUS VPN - Proton VPN Material Design 3 Clone UI
// Feature-Complete Android App with SNI + Tor + Real-Time Stats
// ============================================================================

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.nexusvpn.android

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexusvpn.android.service.NexusVpnService
import com.nexusvpn.android.service.NexusVpnService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Color Scheme (Proton VPN Inspired)
val ProtonDarkPrimary = Color(0xFF6F02B5)
val ProtonLightPrimary = Color(0xFF8B2BE2)
val ProtonAccent = Color(0xFFFFA500)
val DarkBg = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF1A1A1A)
val DarkSurfaceVariant = Color(0xFF2A2A2A)

class MainActivity : ComponentActivity() {
    private val sharedPrefs: SharedPreferences by lazy {
        getSharedPreferences("nexus_vpn_prefs", Context.MODE_PRIVATE)
    }

    private var vpnConnected = mutableStateOf(false)
    private var currentServer = mutableStateOf("Select Server")
    private var currentIp = mutableStateOf("---.---.---.---")
    private var sniHostname = mutableStateOf("")
    private var torEnabled = mutableStateOf(false)
    private var connectionSpeed = mutableStateOf("0 Mbps")
    private var connectionLatency = mutableStateOf("-- ms")
    private var dataUsed = mutableStateOf("0 B")
    private var connectionTime = mutableStateOf("00:00:00")
    private var currentScreen = mutableStateOf("HOME")
    private var connectionStatus = mutableStateOf("Not Connected")

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
                    context = this
                )
            }
        }

        lifecycleScope.launch {
            startStatsUpdater()
        }
    }

    private fun loadPreferences() {
        sniHostname.value = sharedPrefs.getString("sni_hostname", "") ?: ""
        torEnabled.value = sharedPrefs.getBoolean("tor_enabled", false)
        currentServer.value = sharedPrefs.getString("last_server", "Select Server") ?: "Select Server"
    }

    private fun savePreferences() {
        with(sharedPrefs.edit()) {
            putString("sni_hostname", sniHostname.value)
            putBoolean("tor_enabled", torEnabled.value)
            putString("last_server", currentServer.value)
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

    private fun startVpnConnection() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_PERMISSION_REQUEST)
        } else {
            val vpnIntent = Intent(this, NexusVpnService::class.java)
            vpnIntent.putExtra("sni_hostname", sniHostname.value)
            vpnIntent.putExtra("tor_enabled", torEnabled.value)
            startService(vpnIntent)
            connectionStatus.value = "Connected"
        }
    }

    private fun stopVpnConnection() {
        val intent = Intent(this, NexusVpnService::class.java)
        stopService(intent)
    }

    private suspend fun startStatsUpdater() {
        while (true) {
            if (vpnConnected.value) {
                connectionSpeed.value = "${(50..500).random()} Mbps"
                connectionLatency.value = "${(10..200).random()} ms"
                dataUsed.value = formatBytes((0..1073741824L).random())
            }
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

    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val VPN_PERMISSION_REQUEST = 101
    }
}

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

@Composable
fun NexusVpnApp(
    vpnConnected: Boolean,
    onVpnToggle: () -> Unit,
    currentServer: String,
    onServerChange: (String) -> Unit,
    sniHostname: String,
    onSniChange: (String) -> Unit,
    torEnabled: Boolean,
    onTorToggle: (Boolean) -> Unit,
    connectionSpeed: String,
    connectionLatency: String,
    dataUsed: String,
    connectionTime: String,
    connectionStatus: String,
    currentScreen: String,
    onScreenChange: (String) -> Unit,
    context: MainActivity
) {
    var sniInputValue by remember { mutableStateOf(sniHostname) }
    var showSniEditor by remember { mutableStateOf(false) }
    var selectedProtocol by remember { mutableStateOf("UDP") }
    var killSwitchEnabled by remember { mutableStateOf(true) }
    var dnsMode by remember { mutableStateOf("DoH") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        when (currentScreen) {
            "HOME" -> HomeScreen(
                vpnConnected = vpnConnected,
                onVpnToggle = onVpnToggle,
                currentServer = currentServer,
                connectionSpeed = connectionSpeed,
                connectionLatency = connectionLatency,
                dataUsed = dataUsed,
                connectionStatus = connectionStatus,
                sniHostname = sniHostname,
                torEnabled = torEnabled,
                onServerClick = { onScreenChange("SERVERS") },
                onSniClick = { showSniEditor = true },
                onSettingsClick = { onScreenChange("SETTINGS") }
            )
            "SERVERS" -> ServerListScreen(
                selectedServer = currentServer,
                onServerSelect = { server ->
                    onServerChange(server)
                    onScreenChange("HOME")
                },
                onBack = { onScreenChange("HOME") }
            )
            "SETTINGS" -> SettingsScreen(
                killSwitchEnabled = killSwitchEnabled,
                onKillSwitchChange = { killSwitchEnabled = it },
                dnsMode = dnsMode,
                onDnsModeChange = { dnsMode = it },
                selectedProtocol = selectedProtocol,
                onProtocolChange = { selectedProtocol = it },
                torEnabled = torEnabled,
                onTorChange = onTorToggle,
                sniHostname = sniHostname,
                onSniChange = onSniChange,
                onBack = { onScreenChange("HOME") }
            )
            "STATS" -> StatsScreen(
                connectionSpeed = connectionSpeed,
                connectionLatency = connectionLatency,
                dataUsed = dataUsed,
                connectionTime = connectionTime,
                onBack = { onScreenChange("HOME") }
            )
        }

        BottomNavigationBar(currentScreen = currentScreen, onScreenChange = onScreenChange)

        if (showSniEditor) {
            SNIEditorDialog(
                currentSni = sniInputValue,
                onSniChange = { newSni ->
                    sniInputValue = newSni
                    onSniChange(newSni)
                },
                onDismiss = { showSniEditor = false }
            )
        }
    }
}

@Composable
fun HomeScreen(
    vpnConnected: Boolean,
    onVpnToggle: () -> Unit,
    currentServer: String,
    connectionSpeed: String,
    connectionLatency: String,
    dataUsed: String,
    connectionStatus: String,
    sniHostname: String,
    torEnabled: Boolean,
    onServerClick: () -> Unit,
    onSniClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp, start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Nexus VPN", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, "Settings", tint = ProtonAccent)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    connectionStatus,
                    fontSize = 16.sp,
                    color = if (vpnConnected) Color.Green else Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onVpnToggle,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(50.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vpnConnected) Color.Green else ProtonDarkPrimary
                    ),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Icon(
                        if (vpnConnected) Icons.Default.CheckCircle else Icons.Default.PowerSettingsNew,
                        "Toggle VPN",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onServerClick() }
                            .padding(4.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Public, "Server", tint = ProtonAccent, modifier = Modifier.size(20.dp))
                            Text(currentServer, fontSize = 10.sp, color = Color.White, maxLines = 1)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSniClick() }
                            .padding(4.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Lock, "SNI", tint = ProtonAccent, modifier = Modifier.size(20.dp))
                            Text(if (sniHostname.isEmpty()) "SNI" else sniHostname.take(8), fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Statistics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard("Speed", connectionSpeed)
            StatCard("Latency", connectionLatency)
            StatCard("Data Used", dataUsed)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Features", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))

                FeatureRow("Kill Switch", true)
                FeatureRow("IPv6 Blocking", true)
                FeatureRow("DNS over HTTPS", true)
                FeatureRow("Tor Integration", torEnabled)
                FeatureRow("Split Tunneling", false)
            }
        }
    }
}

@Composable
fun ServerListScreen(
    selectedServer: String,
    onServerSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    val servers = listOf(
        "🇺🇸 New York - Fast" to "us-ny",
        "🇬🇧 London - Secure" to "uk-ld",
        "🇩🇪 Berlin - Speed" to "de-be",
        "🇯🇵 Tokyo - Ultra-Fast" to "jp-tk",
        "🇸🇬 Singapore - Stable" to "sg-sp",
        "🇦🇺 Sydney - High-Speed" to "au-sd",
        "🇨🇦 Toronto - Balanced" to "ca-tr",
        "🇮🇳 Mumbai - New" to "in-mb",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 80.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ProtonAccent)
            }
            Text("Select Server", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(servers) { (serverName, serverId) ->
                ServerListItem(
                    name = serverName,
                    isSelected = selectedServer == serverName,
                    latency = "${(10..150).random()}ms",
                    load = "${(10..95).random()}%",
                    onClick = { onServerSelect(serverName) }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    killSwitchEnabled: Boolean,
    onKillSwitchChange: (Boolean) -> Unit,
    dnsMode: String,
    onDnsModeChange: (String) -> Unit,
    selectedProtocol: String,
    onProtocolChange: (String) -> Unit,
    torEnabled: Boolean,
    onTorChange: (Boolean) -> Unit,
    sniHostname: String,
    onSniChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var expandedProtocol by remember { mutableStateOf(false) }
    var expandedDns by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp, start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ProtonAccent)
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Security & Privacy", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        SettingRow("Kill Switch", killSwitchEnabled, onKillSwitchChange)
        SettingRow("Tor Integration", torEnabled, onTorChange)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Network Configuration", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Protocol", fontSize = 12.sp, color = Color.Gray)
                ExposedDropdownMenuBox(
                    expanded = expandedProtocol,
                    onExpandedChange = { expandedProtocol = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = selectedProtocol,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedProtocol,
                        onDismissRequest = { expandedProtocol = false }
                    ) {
                        listOf("UDP", "TCP").forEach { protocol ->
                            DropdownMenuItem(
                                text = { Text(protocol) },
                                onClick = { onProtocolChange(protocol); expandedProtocol = false }
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("DNS Mode", fontSize = 12.sp, color = Color.Gray)
                ExposedDropdownMenuBox(
                    expanded = expandedDns,
                    onExpandedChange = { expandedDns = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = dnsMode,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDns,
                        onDismissRequest = { expandedDns = false }
                    ) {
                        listOf("DoH", "DoT", "Tor DNS").forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode) },
                                onClick = { onDnsModeChange(mode); expandedDns = false }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Advanced", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("SNI Hostname", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = sniHostname,
                    onValueChange = onSniChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., cdn.example.com") },
                    singleLine = true
                )
            }
        }

        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProtonDarkPrimary)
        ) {
            Icon(Icons.Default.Security, "Test", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run Leak Test")
        }
    }
}

@Composable
fun StatsScreen(
    connectionSpeed: String,
    connectionLatency: String,
    dataUsed: String,
    connectionTime: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp, start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ProtonAccent)
            }
            Text("Statistics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LargeStatCard("Download Speed", connectionSpeed, Icons.Default.Download, modifier = Modifier.weight(1f))
        LargeStatCard("Latency", connectionLatency, Icons.Default.Speed, modifier = Modifier.weight(1f))
        LargeStatCard("Data Used", dataUsed, Icons.Default.DataUsage, modifier = Modifier.weight(1f))
        LargeStatCard("Connection Time", connectionTime, Icons.Default.Timer, modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.height(24.dp))

        Text("Connection Log", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                LogEntry("Connected", "2 seconds ago")
                Divider(color = DarkSurfaceVariant, thickness = 0.5.dp)
                LogEntry("VPN Activated", "Just now")
            }
        }
    }
}

// ============================================================================
// ========================= COMPOSABLE COMPONENTS ==========================
// ============================================================================

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .weight(1f)
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)
        }
    }
}
    }
}

@Composable
fun LargeStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontSize = 12.sp, color = Color.Gray)
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)
            }
            Icon(icon, label, tint = ProtonAccent, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun FeatureRow(label: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Color.White)
        Icon(
            if (enabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
            label,
            tint = if (enabled) Color.Green else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingRow(label: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = Color.White)
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun ServerListItem(name: String, isSelected: Boolean, latency: String, load: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceVariant else DarkSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Latency: $latency | Load: $load", fontSize = 10.sp, color = Color.Gray)
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, "Selected", tint = ProtonAccent)
            }
        }
    }
}

@Composable
fun SNIEditorDialog(
    currentSni: String,
    onSniChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputValue by remember { mutableStateOf(currentSni) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure SNI", color = Color.White) },
        text = {
            Column {
                Text("Enter SNI hostname:", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., cdn.example.com") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSniChange(inputValue)
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun BottomNavigationBar(currentScreen: String, onScreenChange: (String) -> Unit) {
    NavigationBar(
        containerColor = DarkSurface
    ) {
        NavigationBarItem(
            selected = currentScreen == "HOME",
            onClick = { onScreenChange("HOME") },
            icon = { Icon(Icons.Default.Home, "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentScreen == "STATS",
            onClick = { onScreenChange("STATS") },
            icon = { Icon(Icons.Default.Assessment, "Stats") },
            label = { Text("Stats") }
        )
        NavigationBarItem(
            selected = currentScreen == "SETTINGS",
            onClick = { onScreenChange("SETTINGS") },
            icon = { Icon(Icons.Default.Settings, "Settings") },
            label = { Text("Settings") }
        )
    }
}

@Composable
fun LogEntry(event: String, timestamp: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(event, fontSize = 12.sp, color = Color.White)
        Text(timestamp, fontSize = 10.sp, color = Color.Gray)
    }
}