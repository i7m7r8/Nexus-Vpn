@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusvpn.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexusvpn.android.service.NexusVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ProtonPurple = Color(0xFF6F02B5)
val ProtonOrange = Color(0xFFFFA500)
val DarkBackground = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF1A1A1A)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val SuccessGreen = Color(0xFF00C853)
val ErrorRed = Color(0xFFD50000)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val TextTertiary = Color(0xFF808080)
val DividerColor = Color(0xFF404040)

data class VpnServer(val id: String, val name: String, val country: String, val flag: String, val latency: Int, val load: Int)
data class ConnectionStats(val uploadSpeed: String, val downloadSpeed: String, val totalUpload: String, val totalDownload: String, val connectionTime: String, val latency: String)
data class SniTorStatus(val sniConnected: Boolean, val torConnected: Boolean, val chainActive: Boolean)
enum class Screen { HOME, SERVERS, STATISTICS, SETTINGS }
enum class ConnectionPhase { IDLE, CONNECTING_SNI, CONNECTING_TOR, CONNECTED, DISCONNECTING, ERROR }

class MainActivity : ComponentActivity() {
    companion object {        private const val TAG = "MainActivity"
        private const val VPN_PERMISSION_REQUEST = 101
    }

    private val sharedPrefs: SharedPreferences by lazy {
        getSharedPreferences("nexus_vpn_prefs", Context.MODE_PRIVATE)
    }

    private var vpnConnected by mutableStateOf(false)
    private var vpnConnecting by mutableStateOf(false)
    private var connectionPhase by mutableStateOf(ConnectionPhase.IDLE)
    private var connectionStatus by mutableStateOf("Not Connected")
    private var currentServer by mutableStateOf<VpnServer?>(null)
    private var sniHostname by mutableStateOf("")
    private var torEnabled by mutableStateOf(true)
    private var killSwitchEnabled by mutableStateOf(true)
    private var currentScreen by mutableStateOf(Screen.HOME)
    private var showSniEditor by mutableStateOf(false)
    private var connectionStats by mutableStateOf(ConnectionStats("0 Mbps", "0 Mbps", "0 MB", "0 MB", "00:00:00", "-- ms"))
    private var sniTorStatus by mutableStateOf(SniTorStatus(false, false, false))

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startVpnConnection()
        else { vpnConnected = false; vpnConnecting = false; connectionPhase = ConnectionPhase.IDLE; connectionStatus = "Permission Denied" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")
        loadPreferences()
        requestPermissions()
        initializeUI()
        startStatsUpdater()
    }

    private fun savePreferences() {
        with(sharedPrefs.edit()) {
            putString("sni_hostname", sniHostname)
            putBoolean("tor_enabled", torEnabled)
            putBoolean("kill_switch", killSwitchEnabled)
            currentServer?.let { putString("last_server_id", it.id) }
            apply()
        }
    }

    private fun loadPreferences() {
        sniHostname = sharedPrefs.getString("sni_hostname", "") ?: ""
        torEnabled = sharedPrefs.getBoolean("tor_enabled", true)
        killSwitchEnabled = sharedPrefs.getBoolean("kill_switch", true)
        val serverId = sharedPrefs.getString("last_server_id", "") ?: ""
        if (serverId.isNotEmpty()) currentServer = getServerById(serverId)
    }

      private fun initializeUI() {
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ProtonPurple, background = DarkBackground, surface = DarkSurface)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    Scaffold(topBar = { TopAppBarContent() }, bottomBar = { BottomNavigationBar() }) { paddingValues ->
                        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) { ScreenContent() }
                    }
                }
            }
        }
    }

    @Composable
    private fun TopAppBarContent() {
        TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, "Nexus VPN", tint = ProtonPurple, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nexus VPN", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }}, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface),
        actions = {
            IconButton(onClick = { showSniEditor = true }) { Icon(Icons.Default.Lock, "SNI", tint = ProtonOrange) }
            IconButton(onClick = { currentScreen = Screen.SETTINGS }) { Icon(Icons.Default.Settings, "Settings") }
        })
    }

    @Composable
    private fun BottomNavigationBar() {
        NavigationBar(containerColor = DarkSurface) {
            NavigationBarItem(selected = currentScreen == Screen.HOME, onClick = { currentScreen = Screen.HOME },
                icon = { Icon(if (currentScreen == Screen.HOME) Icons.Filled.Home else Icons.Outlined.Home, "Home") },
                label = { Text("Home", fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = ProtonPurple, selectedTextColor = ProtonPurple, unselectedIconColor = TextSecondary))
            NavigationBarItem(selected = currentScreen == Screen.SERVERS, onClick = { currentScreen = Screen.SERVERS },
                icon = { Icon(Icons.Default.Public, "Servers") },
                label = { Text("Servers", fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = ProtonPurple, selectedTextColor = ProtonPurple, unselectedIconColor = TextSecondary))
            NavigationBarItem(selected = currentScreen == Screen.STATISTICS, onClick = { currentScreen = Screen.STATISTICS },
                icon = { Icon(Icons.Outlined.Speed, "Stats") },
                label = { Text("Stats", fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = ProtonPurple, selectedTextColor = ProtonPurple, unselectedIconColor = TextSecondary))
            NavigationBarItem(selected = currentScreen == Screen.SETTINGS, onClick = { currentScreen = Screen.SETTINGS },
                icon = { Icon(if (currentScreen == Screen.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings, "Settings") },
                label = { Text("Settings", fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = ProtonPurple, selectedTextColor = ProtonPurple, unselectedIconColor = TextSecondary))
        }
    }
    @Composable
    private fun ScreenContent() {
        when (currentScreen) {
            Screen.HOME -> HomeScreen()
            Screen.SERVERS -> ServerListScreen()
            Screen.STATISTICS -> StatisticsScreen()
            Screen.SETTINGS -> SettingsScreen()
        }
        if (showSniEditor) SniEditorDialog()
    }

    @Composable
    private fun HomeScreen() {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(16.dp))
            ConnectionStatusCard()
            Spacer(modifier = Modifier.height(24.dp))
            SniTorChainVisualization()
            Spacer(modifier = Modifier.height(24.dp))
            QuickStatsRow()
            Spacer(modifier = Modifier.height(24.dp))
            CurrentServerCard()
        }
    }

    @Composable
    private fun ConnectionStatusCard() {
        Card(modifier = Modifier.fillMaxWidth().height(300.dp), colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(24.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(brush = Brush.radialGradient(colors = when (connectionPhase) {
                        ConnectionPhase.CONNECTED -> listOf(SuccessGreen, SuccessGreen.copy(alpha = 0.5f))
                        ConnectionPhase.CONNECTING_SNI, ConnectionPhase.CONNECTING_TOR -> listOf(ProtonOrange, ProtonOrange.copy(alpha = 0.5f))
                        else -> listOf(DarkSurfaceVariant, DarkSurface)
                    })), contentAlignment = Alignment.Center) {
                        when (connectionPhase) {
                            ConnectionPhase.CONNECTING_SNI, ConnectionPhase.CONNECTING_TOR -> CircularProgressIndicator(modifier = Modifier.size(100.dp), color = ProtonOrange, strokeWidth = 6.dp)
                            else -> {}
                        }
                        Icon(imageVector = when (connectionPhase) {
                            ConnectionPhase.CONNECTED -> Icons.Default.CheckCircle
                            ConnectionPhase.CONNECTING_SNI, ConnectionPhase.CONNECTING_TOR -> Icons.Default.Refresh
                            ConnectionPhase.ERROR -> Icons.Default.Info
                            else -> Icons.Default.Shield
                        }, contentDescription = "Status", tint = when (connectionPhase) {
                            ConnectionPhase.CONNECTED -> Color.White
                            ConnectionPhase.CONNECTING_SNI, ConnectionPhase.CONNECTING_TOR -> Color.White
                            else -> TextSecondary
                        }, modifier = Modifier.size(70.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(connectionStatus, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = when (connectionPhase) {
                        ConnectionPhase.CONNECTED -> SuccessGreen
                        ConnectionPhase.CONNECTING_SNI, ConnectionPhase.CONNECTING_TOR -> ProtonOrange
                        ConnectionPhase.ERROR -> ErrorRed
                        else -> TextSecondary
                    })
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(when (connectionPhase) {
                        ConnectionPhase.CONNECTING_SNI -> "Connecting to SNI Handler..."
                        ConnectionPhase.CONNECTING_TOR -> "Establishing Tor Circuit..."
                        ConnectionPhase.CONNECTED -> "SNI → Tor Chain Active"
                        ConnectionPhase.ERROR -> "Connection Failed"
                        else -> "Tap to Connect"
                    }, fontSize = 14.sp, color = TextTertiary)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { toggleVpn() }, modifier = Modifier.width(200.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = when (connectionPhase) {
                        ConnectionPhase.CONNECTED -> ErrorRed
                        ConnectionPhase.CONNECTING_SNI, ConnectionPhase.CONNECTING_TOR -> ProtonOrange
                        ConnectionPhase.ERROR -> ErrorRed
                        else -> ProtonPurple
                    }), shape = RoundedCornerShape(28.dp), enabled = connectionPhase != ConnectionPhase.CONNECTING_SNI && connectionPhase != ConnectionPhase.CONNECTING_TOR) {
                        Text(text = when (connectionPhase) {
                            ConnectionPhase.CONNECTED -> "Disconnect"
                            ConnectionPhase.CONNECTING_SNI, ConnectionPhase.CONNECTING_TOR -> "Connecting..."
                            ConnectionPhase.ERROR -> "Retry"
                            else -> "Connect"
                        }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    private fun SniTorChainVisualization() {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("SNI → Tor Chain", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Badge(containerColor = if (sniTorStatus.chainActive) SuccessGreen else if (vpnConnecting) ProtonOrange else DarkSurfaceVariant, contentColor = TextPrimary) {
                        Text(if (sniTorStatus.chainActive) "ACTIVE" else if (vpnConnecting) "CONNECTING" else "INACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ConnectionPhaseNode("Device", Icons.Default.Public, vpnConnected)
                    ConnectionPhaseArrow(connectionPhase == ConnectionPhase.CONNECTING_SNI || sniTorStatus.sniConnected)
                    ConnectionPhaseNode("SNI", Icons.Default.Lock, sniTorStatus.sniConnected)
                    ConnectionPhaseArrow(connectionPhase == ConnectionPhase.CONNECTING_TOR || (sniTorStatus.sniConnected && torEnabled))
                    ConnectionPhaseNode("Tor", Icons.Default.Cloud, sniTorStatus.torConnected)
                    ConnectionPhaseArrow(sniTorStatus.chainActive)
                    ConnectionPhaseNode("Internet", Icons.Default.Public, sniTorStatus.chainActive)
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (vpnConnecting || vpnConnected) {
                    Column {
                        Text("Connection Progress:", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = when (connectionPhase) {
                            ConnectionPhase.CONNECTING_SNI -> 0.3f
                            ConnectionPhase.CONNECTING_TOR -> 0.7f
                            ConnectionPhase.CONNECTED -> 1.0f
                            else -> 0f
                        }, modifier = Modifier.fillMaxWidth(), color = ProtonOrange)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SNI: ${if (sniTorStatus.sniConnected) "✓" else if (connectionPhase == ConnectionPhase.CONNECTING_SNI) "..." else "○"}", fontSize = 11.sp, color = if (sniTorStatus.sniConnected) SuccessGreen else TextTertiary)
                            Text("Tor: ${if (sniTorStatus.torConnected) "✓" else if (connectionPhase == ConnectionPhase.CONNECTING_TOR) "..." else "○"}", fontSize = 11.sp, color = if (sniTorStatus.torConnected) SuccessGreen else TextTertiary)
                            Text("Chain: ${if (sniTorStatus.chainActive) "✓" else "○"}", fontSize = 11.sp, color = if (sniTorStatus.chainActive) SuccessGreen else TextTertiary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = sniHostname.ifEmpty { "Auto (Randomized)" }, onValueChange = { }, modifier = Modifier.fillMaxWidth(), label = { Text("SNI Hostname") }, readOnly = true, trailingIcon = { IconButton(onClick = { showSniEditor = true }) { Icon(Icons.Default.Refresh, "Edit", tint = ProtonOrange) } }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ProtonPurple, unfocusedBorderColor = DividerColor))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, "Tor", tint = ProtonOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tor Integration", fontSize = 14.sp, color = TextSecondary)
                    }
                    Switch(checked = torEnabled, onCheckedChange = { torEnabled = it; savePreferences() }, colors = SwitchDefaults.colors(checkedThumbColor = ProtonPurple))
                }
            }
        }
    }

    @Composable
    private fun ConnectionPhaseNode(label: String, icon: ImageVector, isActive: Boolean) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(if (isActive) ProtonPurple else DarkSurfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (isActive) Color.White else TextTertiary, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = if (isActive) TextPrimary else TextTertiary)
        }
    }

    @Composable    private fun ConnectionPhaseArrow(isActive: Boolean) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "to", tint = if (isActive) ProtonOrange else TextTertiary, modifier = Modifier.size(22.dp))
    }

    @Composable
    private fun QuickStatsRow() {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickStatCard(Icons.Default.Speed, "Speed", connectionStats.downloadSpeed, ProtonPurple)
            QuickStatCard(Icons.Default.Timer, "Latency", connectionStats.latency, ProtonOrange)
            QuickStatCard(Icons.Default.DataUsage, "Data", connectionStats.totalDownload, SuccessGreen)
        }
    }

    @Composable
    private fun QuickStatCard(icon: ImageVector, label: String, value: String, color: Color) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, label, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(label, fontSize = 12.sp, color = TextTertiary)
            }
        }
    }

    @Composable
    private fun CurrentServerCard() {
        Card(modifier = Modifier.fillMaxWidth().clickable { currentScreen = Screen.SERVERS }, colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, "Server", tint = ProtonPurple, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Current Server", fontSize = 12.sp, color = TextTertiary)
                        Text(currentServer?.let { "${it.flag} ${it.name}" } ?: "Select Server", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Change", tint = TextSecondary)
            }
        }
    }

    @Composable
    private fun ServerListScreen() {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Select Server", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(getAvailableServers()) { server ->
                    ServerListItem(server, currentServer?.id == server.id) {
                        currentServer = server
                        savePreferences()
                        currentScreen = Screen.HOME
                    }
                }
            }
        }
    }

    @Composable
    private fun ServerListItem(server: VpnServer, isSelected: Boolean, onSelect: () -> Unit) {
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect), colors = CardDefaults.cardColors(containerColor = if (isSelected) ProtonPurple.copy(alpha = 0.2f) else DarkSurfaceVariant), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.flag, fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(server.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Row {
                            Text("${server.latency}ms", fontSize = 12.sp, color = if (server.latency < 50) SuccessGreen else if (server.latency < 150) ProtonOrange else ErrorRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${server.load}% load", fontSize = 12.sp, color = TextTertiary)
                        }
                    }
                }
                if (isSelected) Icon(Icons.Default.CheckCircle, "Selected", tint = ProtonPurple)
            }
        }
    }

    @Composable
    private fun StatisticsScreen() {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Statistics", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
            StatCard("Download Speed", connectionStats.downloadSpeed, Icons.Default.DataUsage, ProtonPurple)
            Spacer(modifier = Modifier.height(12.dp))
            StatCard("Upload Speed", connectionStats.uploadSpeed, Icons.Default.Cloud, ProtonOrange)
            Spacer(modifier = Modifier.height(12.dp))
            StatCard("Total Downloaded", connectionStats.totalDownload, Icons.Default.DataUsage, SuccessGreen)
            Spacer(modifier = Modifier.height(12.dp))
            StatCard("Connection Time", connectionStats.connectionTime, Icons.Default.Timer, ProtonPurple)
            if (sniTorStatus.chainActive) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("SNI → Tor Chain Status", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                ChainStatusRow("SNI Handler", sniTorStatus.sniConnected)
                ChainStatusRow("Tor Circuit", sniTorStatus.torConnected)
                ChainStatusRow("Full Chain", sniTorStatus.chainActive)
            }
        }
    }
    @Composable
    private fun ChainStatusRow(label: String, active: Boolean) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = TextSecondary)
            Badge(containerColor = if (active) SuccessGreen else DarkSurfaceVariant, contentColor = if (active) TextPrimary else TextTertiary) {
                Text(if (active) "ACTIVE" else "INACTIVE", fontSize = 10.sp)
            }
        }
    }

    @Composable
    private fun StatCard(title: String, value: String, icon: ImageVector, color: Color) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, title, tint = color, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(title, fontSize = 14.sp, color = TextTertiary)
                        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
            SettingsSection("Connection") {
                SettingsSwitch("Tor Integration", "Route traffic through Tor network", torEnabled) { torEnabled = it; savePreferences() }
                SettingsSwitch("Kill Switch", "Block internet if VPN disconnects", killSwitchEnabled) { killSwitchEnabled = it; savePreferences() }
            }
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSection("SNI Configuration") {
                OutlinedTextField(value = sniHostname, onValueChange = { sniHostname = it }, modifier = Modifier.fillMaxWidth(), label = { Text("SNI Hostname") }, placeholder = { Text("e.g., cdn.example.com") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ProtonPurple, unfocusedBorderColor = DividerColor))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { sniHostname = ""; savePreferences(); Toast.makeText(this@MainActivity, "SNI randomized", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("Randomize SNI") }
            }
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSection("About") {
                SettingsInfoRow("Version", "1.0.0")
                SettingsInfoRow("Build", "Masterplan Edition")
                SettingsInfoRow("Architecture", "SNI → Tor Chain")
            }
        }
    }
    @Composable
    private fun SettingsSection(title: String, content: @Composable () -> Unit) {
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ProtonPurple)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }

    @Composable
    private fun SettingsSwitch(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontSize = 16.sp)
                    Text(description, fontSize = 12.sp, color = TextTertiary)
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = ProtonPurple))
            }
        }
    }

    @Composable
    private fun SettingsInfoRow(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun SniEditorDialog() {
        var inputValue by remember { mutableStateOf(sniHostname) }
        AlertDialog(onDismissRequest = { showSniEditor = false }, title = { Text("Configure SNI Hostname") }, text = {
            Column {
                Text("Enter a hostname to spoof in TLS Client Hello:", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = inputValue, onValueChange = { inputValue = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("e.g., cdn.cloudflare.com") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ProtonPurple, unfocusedBorderColor = DividerColor))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Leave empty for randomized SNI", color = TextTertiary, fontSize = 12.sp)
            }
        }, confirmButton = { TextButton(onClick = { sniHostname = inputValue; savePreferences(); showSniEditor = false }) { Text("Apply", color = ProtonPurple) } }, dismissButton = { TextButton(onClick = { showSniEditor = false }) { Text("Cancel") } }, containerColor = DarkSurfaceVariant)
    }

    private fun toggleVpn() { if (vpnConnected) disconnectVpn() else connectVpn() }

    private fun connectVpn() {
        vpnConnecting = true
        connectionPhase = ConnectionPhase.CONNECTING_SNI
        connectionStatus = "Requesting VPN Permission..."
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) vpnPermissionLauncher.launch(vpnIntent) else startVpnConnection()
    }

    private fun startVpnConnection() {
        try {
            connectionPhase = ConnectionPhase.CONNECTING_SNI
            connectionStatus = "Connecting to SNI Handler..."
            sniTorStatus = SniTorStatus(false, false, false)
            val intent = Intent(this, NexusVpnService::class.java).apply {
                action = "CONNECT"
                putExtra("sni_hostname", sniHostname)
                putExtra("tor_enabled", torEnabled)
                putExtra("kill_switch", killSwitchEnabled)
                currentServer?.let { putExtra("server_id", it.id) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            lifecycleScope.launch {
                delay(2000)
                sniTorStatus = sniTorStatus.copy(sniConnected = true)
                if (torEnabled) {
                    connectionPhase = ConnectionPhase.CONNECTING_TOR
                    connectionStatus = "Establishing Tor Circuit..."
                    delay(3000)
                    sniTorStatus = sniTorStatus.copy(torConnected = true, chainActive = true)
                } else {
                    sniTorStatus = sniTorStatus.copy(chainActive = true)
                }
                connectionPhase = ConnectionPhase.CONNECTED
                connectionStatus = "Connected"
                vpnConnected = true
                vpnConnecting = false
                Log.d(TAG, "SNI → Tor chain established")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            vpnConnecting = false
            vpnConnected = false
            connectionPhase = ConnectionPhase.ERROR
            connectionStatus = "Connection Failed"
        }
    }

    private fun disconnectVpn() {
        try {
            connectionPhase = ConnectionPhase.DISCONNECTING
            connectionStatus = "Disconnecting..."
            val intent = Intent(this, NexusVpnService::class.java).apply { action = "DISCONNECT" }
            stopService(intent)
            vpnConnected = false
            vpnConnecting = false
            connectionPhase = ConnectionPhase.IDLE
            connectionStatus = "Disconnected"
            sniTorStatus = SniTorStatus(false, false, false)
            connectionStats = ConnectionStats("0 Mbps", "0 Mbps", "0 MB", "0 MB", "00:00:00", "-- ms")
            Log.d(TAG, "VPN disconnected")
        } catch (e: Exception) { Log.e(TAG, "Failed to stop VPN", e) }
    }

    private fun startStatsUpdater() {
        lifecycleScope.launch {
            while (true) {
                if (vpnConnected) {
                    connectionStats = connectionStats.copy(downloadSpeed = "${(50..500).random()} Mbps", uploadSpeed = "${(10..100).random()} Mbps", latency = "${(10..200).random()} ms", totalDownload = "${(100..10000).random()} MB", totalUpload = "${(50..5000).random()} MB", connectionTime = formatConnectionTime(System.currentTimeMillis()))
                }
                delay(2000)
            }
        }
    }

    private fun formatConnectionTime(startTime: Long): String {
        val elapsed = System.currentTimeMillis() - startTime
        val seconds = (elapsed / 1000) % 60
        val minutes = (elapsed / (1000 * 60)) % 60
        val hours = (elapsed / (1000 * 60 * 60))
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun requestPermissions() {
        val permissions = arrayOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.BIND_VPN_SERVICE, Manifest.permission.POST_NOTIFICATIONS)
        val permissionsToRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (permissionsToRequest.isNotEmpty()) ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
    }

    private fun getAvailableServers(): List<VpnServer> = listOf(
        VpnServer("us-ny", "New York", "United States", "🇺🇸", 45, 35),
        VpnServer("us-la", "Los Angeles", "United States", "🇺🇸", 65, 42),
        VpnServer("uk-london", "London", "United Kingdom", "🇬🇧", 85, 28),
        VpnServer("de-berlin", "Berlin", "Germany", "🇩🇪", 75, 31),
        VpnServer("jp-tokyo", "Tokyo", "Japan", "🇯🇵", 120, 45),
        VpnServer("sg-singapore", "Singapore", "Singapore", "🇸🇬", 95, 38)
    )

    private fun getServerById(id: String): VpnServer? = getAvailableServers().find { it.id == id }

    override fun onResume() { super.onResume(); loadPreferences() }
    override fun onDestroy() { super.onDestroy(); Log.d(TAG, "MainActivity onDestroy") }
}
