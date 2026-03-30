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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.darkColorScheme
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexusvpn.android.service.NexusVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ============================================================================
// COLOR SCHEME - Proton VPN Inspired Dark Theme
// ============================================================================

val ProtonPurple = Color(0xFF6F02B5)
val ProtonPurpleLight = Color(0xFF8B2BE2)
val ProtonPurpleDark = Color(0xFF550099)
val ProtonOrange = Color(0xFFFFA500)
val ProtonOrangeLight = Color(0xFFFFB84D)
val DarkBackground = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF1A1A1A)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val DarkSurfaceVariantLight = Color(0xFF3A3A3A)
val SuccessGreen = Color(0xFF00C853)
val ErrorRed = Color(0xFFD50000)
val WarningYellow = Color(0xFFFFAB00)
val InfoBlue = Color(0xFF2962FF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val TextTertiary = Color(0xFF808080)
val DividerColor = Color(0xFF404040)

// ============================================================================
// DATA CLASSES
// ============================================================================

data class VpnServer(
    val id: String,
    val name: String,
    val country: String,
    val city: String,
    val flag: String,
    val latency: Int,
    val load: Int,
    val isPremium: Boolean,
    val supportsTor: Boolean,
    val supportsSni: Boolean
)

data class ConnectionStats(
    val uploadSpeed: String,    val downloadSpeed: String,
    val totalUpload: String,
    val totalDownload: String,
    val connectionTime: String,
    val latency: String,
    val packetsSent: Long,
    val packetsReceived: Long
)

data class TorCircuitInfo(
    val entryNode: String,
    val middleNode: String,
    val exitNode: String,
    val circuitId: String,
    val buildTime: String,
    val isHealthy: Boolean
)

enum class Screen { HOME, SERVERS, STATISTICS, SETTINGS, ABOUT }

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

// ============================================================================
// MAIN ACTIVITY
// ============================================================================

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val VPN_PERMISSION_REQUEST = 101
        private const val NOTIFICATION_PERMISSION_REQUEST = 102
    }

    private val sharedPrefs: SharedPreferences by lazy {
        getSharedPreferences("nexus_vpn_prefs", Context.MODE_PRIVATE)
    }

    // VPN State
    private var vpnConnected by mutableStateOf(false)
    private var vpnConnecting by mutableStateOf(false)
    private var connectionStatus by mutableStateOf("Not Connected")
    private var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)

    // Configuration
    private var currentServer by mutableStateOf<VpnServer?>(null)
    private var sniHostname by mutableStateOf("")
    private var torEnabled by mutableStateOf(true)
    private var killSwitchEnabled by mutableStateOf(true)
    private var dnsLeakProtection by mutableStateOf(true)    private var ipv6LeakProtection by mutableStateOf(true)
    private var autoReconnect by mutableStateOf(true)
    private var splitTunnelingEnabled by mutableStateOf(false)

    // Statistics
    private var connectionStats by mutableStateOf(
        ConnectionStats(
            uploadSpeed = "0 Mbps",
            downloadSpeed = "0 Mbps",
            totalUpload = "0 MB",
            totalDownload = "0 MB",
            connectionTime = "00:00:00",
            latency = "-- ms",
            packetsSent = 0,
            packetsReceived = 0
        )
    )

    // Tor Circuit
    private var torCircuitInfo by mutableStateOf<TorCircuitInfo?>(null)
    private var torBootstrapped by mutableStateOf(false)

    // UI State
    private var currentScreen by mutableStateOf(Screen.HOME)
    private var snackbarMessage by mutableStateOf<String?>(null)
    private var showErrorDialog by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private var showSniEditor by mutableStateOf(false)
    private var showServerSelector by mutableStateOf(false)
    private var selectedTabIndex by mutableStateOf(0)

    // Handlers
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnConnection()
        } else {
            vpnConnected = false
            vpnConnecting = false
            connectionStatus = "Permission Denied"
            connectionState = ConnectionState.DISCONNECTED
            showErrorDialog = true
            errorMessage = "VPN permission was denied. Cannot establish secure connection."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)        Log.d(TAG, "MainActivity onCreate")

        loadPreferences()
        requestPermissions()
        initializeUI()
        startStatsUpdater()
    }

    private fun loadPreferences() {
        sniHostname = sharedPrefs.getString("sni_hostname", "") ?: ""
        torEnabled = sharedPrefs.getBoolean("tor_enabled", true)
        killSwitchEnabled = sharedPrefs.getBoolean("kill_switch", true)
        dnsLeakProtection = sharedPrefs.getBoolean("dns_leak_protection", true)
        ipv6LeakProtection = sharedPrefs.getBoolean("ipv6_leak_protection", true)
        autoReconnect = sharedPrefs.getBoolean("auto_reconnect", true)
        splitTunnelingEnabled = sharedPrefs.getBoolean("split_tunneling", false)

        val serverId = sharedPrefs.getString("last_server_id", "") ?: ""
        if (serverId.isNotEmpty()) {
            currentServer = getServerById(serverId)
        }

        Log.d(TAG, "Preferences loaded: SNI=$sniHostname, Tor=$torEnabled")
    }

    private fun savePreferences() {
        with(sharedPrefs.edit()) {
            putString("sni_hostname", sniHostname)
            putBoolean("tor_enabled", torEnabled)
            putBoolean("kill_switch", killSwitchEnabled)
            putBoolean("dns_leak_protection", dnsLeakProtection)
            putBoolean("ipv6_leak_protection", ipv6LeakProtection)
            putBoolean("auto_reconnect", autoReconnect)
            putBoolean("split_tunneling", splitTunnelingEnabled)
            currentServer?.let { putString("last_server_id", it.id) }
            apply()
        }
        Log.d(TAG, "Preferences saved")
    }

    private fun initializeUI() {
        setContent {
            NexusVpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    ModalNavigationDrawer(
                        drawerState = rememberDrawerState(DrawerValue.Closed),
                        drawerContent = {                            NavigationDrawerContent(
                                currentScreen = currentScreen,
                                onScreenChange = { currentScreen = it },
                                onCloseDrawer = { }
                            )
                        }
                    ) {
                        NexusVpnScaffold()
                    }
                }
            }
        }
    }

    @Composable
    private fun NexusVpnTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = ProtonPurple,
                onPrimary = TextPrimary,
                primaryContainer = ProtonPurpleDark,
                onPrimaryContainer = TextPrimary,
                secondary = ProtonOrange,
                onSecondary = TextPrimary,
                secondaryContainer = ProtonOrangeLight,
                onSecondaryContainer = TextPrimary,
                background = DarkBackground,
                onBackground = TextPrimary,
                surface = DarkSurface,
                onSurface = TextPrimary,
                surfaceVariant = DarkSurfaceVariant,
                onSurfaceVariant = TextSecondary,
                error = ErrorRed,
                onError = TextPrimary
            ),
            typography = MaterialTheme.typography.copy(
                bodyLarge = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.SansSerif
                )
            ),
            content = content
        )
    }

    @Composable
    private fun NexusVpnScaffold() {
        Scaffold(
            topBar = { TopAppBarContent() },
            bottomBar = { BottomNavigationBar() },
            snackbarHost = {                SnackbarHost(
                    hostState = remember { SnackbarHostState() }
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = DarkSurfaceVariant,
                        contentColor = TextPrimary,
                        actionColor = ProtonOrange
                    )
                }
            },
            floatingActionButton = {
                if (currentScreen == Screen.HOME) {
                    QuickConnectFab()
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ScreenContent()
            }
        }
    }

    @Composable
    private fun TopAppBarContent() {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Nexus VPN",
                        tint = ProtonPurple,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Nexus VPN",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(                containerColor = DarkSurface,
                titleContentColor = TextPrimary,
                actionIconContentColor = TextSecondary
            ),
            actions = {
                IconButton(onClick = { showSniEditor = true }) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "SNI Settings",
                        tint = ProtonOrange
                    )
                }
                IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary
                    )
                }
            }
        )
    }

    @Composable
    private fun BottomNavigationBar() {
        NavigationBar(
            containerColor = DarkSurface,
            tonalElevation = 8.dp
        ) {
            NavigationBarItem(
                selected = currentScreen == Screen.HOME,
                onClick = { currentScreen = Screen.HOME },
                icon = {
                    Icon(
                        imageVector = if (currentScreen == Screen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "Home"
                    )
                },
                label = { Text("Home", fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProtonPurple,
                    selectedTextColor = ProtonPurple,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = ProtonPurple.copy(alpha = 0.2f)
                )
            )
            NavigationBarItem(
                selected = currentScreen == Screen.SERVERS,
                onClick = { currentScreen = Screen.SERVERS },                icon = {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Servers"
                    )
                },
                label = { Text("Servers", fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProtonPurple,
                    selectedTextColor = ProtonPurple,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = ProtonPurple.copy(alpha = 0.2f)
                )
            )
            NavigationBarItem(
                selected = currentScreen == Screen.STATISTICS,
                onClick = { currentScreen = Screen.STATISTICS },
                icon = {
                    BadgedBox(
                        badge = {
                            if (vpnConnected) {
                                Badge(
                                    containerColor = SuccessGreen,
                                    contentColor = TextPrimary
                                ) {
                                    Text("LIVE")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Statistics"
                        )
                    }
                },
                label = { Text("Stats", fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProtonPurple,
                    selectedTextColor = ProtonPurple,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = ProtonPurple.copy(alpha = 0.2f)
                )
            )
            NavigationBarItem(
                selected = currentScreen == Screen.SETTINGS,
                onClick = { currentScreen = Screen.SETTINGS },
                icon = {                    Icon(
                        imageVector = if (currentScreen == Screen.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = "Settings"
                    )
                },
                label = { Text("Settings", fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProtonPurple,
                    selectedTextColor = ProtonPurple,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = ProtonPurple.copy(alpha = 0.2f)
                )
            )
        }
    }

    @Composable
    private fun ScreenContent() {
        when (currentScreen) {
            Screen.HOME -> HomeScreen()
            Screen.SERVERS -> ServerListScreen()
            Screen.STATISTICS -> StatisticsScreen()
            Screen.SETTINGS -> SettingsScreen()
            Screen.ABOUT -> AboutScreen()
        }

        if (showErrorDialog) {
            ErrorDialog(
                message = errorMessage,
                onDismiss = { showErrorDialog = false }
            )
        }

        if (showSniEditor) {
            SniEditorDialog()
        }
    }

    @Composable
    private fun HomeScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            ConnectionStatusCard()            Spacer(modifier = Modifier.height(24.dp))
            QuickStatsRow()
            Spacer(modifier = Modifier.height(24.dp))
            SniTorChainCard()
            Spacer(modifier = Modifier.height(24.dp))
            CurrentServerCard()
            Spacer(modifier = Modifier.height(24.dp))
            SecurityFeaturesCard()
        }
    }

    @Composable
    private fun ConnectionStatusCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurface
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (vpnConnected) {
                                    listOf(
                                        ProtonPurple.copy(alpha = 0.3f),
                                        DarkSurface
                                    )
                                } else {
                                    listOf(
                                        DarkSurfaceVariant,
                                        DarkSurface
                                    )
                                }
                            )
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = if (vpnConnected) {
                                        listOf(SuccessGreen, SuccessGreen.copy(alpha = 0.5f))
                                    } else if (vpnConnecting) {
                                        listOf(ProtonOrange, ProtonOrange.copy(alpha = 0.5f))
                                    } else {
                                        listOf(DarkSurfaceVariantLight, DarkSurfaceVariant)
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (vpnConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(80.dp),
                                color = ProtonOrange,
                                trackColor = DarkSurfaceVariant,
                                strokeWidth = 4.dp
                            )
                        }
                        Icon(
                            imageVector = if (vpnConnected) {
                                Icons.Default.CheckCircle
                            } else if (vpnConnecting) {
                                Icons.Default.Refresh
                            } else {
                                Icons.Default.Shield
                            },
                            contentDescription = "Connection Status",
                            tint = if (vpnConnected || vpnConnecting) Color.White else TextSecondary,
                            modifier = Modifier.size(60.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = connectionStatus,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            vpnConnected -> SuccessGreen
                            vpnConnecting -> ProtonOrange
                            else -> TextSecondary
                        }                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            vpnConnected -> "SNI to Tor Chain Active"
                            vpnConnecting -> "Establishing Secure Connection..."
                            else -> "Tap to Connect"
                        },
                        fontSize = 14.sp,
                        color = TextTertiary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { toggleVpn() },
                        modifier = Modifier
                            .width(200.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                vpnConnected -> ErrorRed
                                vpnConnecting -> ProtonOrange
                                else -> ProtonPurple
                            }
                        ),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !vpnConnecting
                    ) {
                        Text(
                            text = when {
                                vpnConnected -> "Disconnect"
                                vpnConnecting -> "Connecting..."
                                else -> "Connect"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun QuickStatsRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard(
                icon = Icons.Default.Speed,
                label = "Speed",
                value = connectionStats.downloadSpeed,
                color = ProtonPurple
            )
            QuickStatCard(
                icon = Icons.Default.Timer,
                label = "Latency",
                value = connectionStats.latency,
                color = ProtonOrange
            )
            QuickStatCard(
                icon = Icons.Default.DataUsage,
                label = "Data",
                value = connectionStats.totalDownload,
                color = SuccessGreen
            )
        }
    }

    @Composable
    private fun QuickStatCard(
        icon: ImageVector,
        label: String,
        value: String,
        color: Color
    ) {
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = TextTertiary
                )
            }
        }
    }

    @Composable
    private fun SniTorChainCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SNI to Tor Chain",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Badge(
                        containerColor = if (torEnabled && vpnConnected) SuccessGreen else WarningYellow,
                        contentColor = TextPrimary
                    ) {
                        Text(
                            text = if (torEnabled) "ENABLED" else "DISABLED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChainNode(
                        label = "Device",
                        icon = Icons.Default.Public,
                        isActive = vpnConnected
                    )
                    ChainArrow(isActive = vpnConnected)
                    ChainNode(
                        label = "SNI",
                        icon = Icons.Default.Lock,
                        isActive = vpnConnected && sniHostname.isNotEmpty()
                    )
                    ChainArrow(isActive = vpnConnected)
                    ChainNode(
                        label = "Tor",
                        icon = Icons.Default.Cloud,
                        isActive = vpnConnected && torEnabled
                    )
                    ChainArrow(isActive = vpnConnected)
                    ChainNode(
                        label = "Internet",
                        icon = Icons.Default.Public,
                        isActive = vpnConnected
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = sniHostname.ifEmpty { "Auto (Randomized)" },
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SNI Hostname") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showSniEditor = true }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Edit SNI",
                                tint = ProtonOrange
                            )
                        }                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProtonPurple,
                        unfocusedBorderColor = DividerColor
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Tor",
                            tint = ProtonOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Tor Integration",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = torEnabled,
                        onCheckedChange = {
                            torEnabled = it
                            savePreferences()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ProtonPurple,
                            checkedTrackColor = ProtonPurpleLight
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun ChainNode(
        label: String,
        icon: ImageVector,        isActive: Boolean
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isActive) ProtonPurple else DarkSurfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color.White else TextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = if (isActive) TextPrimary else TextTertiary
            )
        }
    }

    @Composable
    private fun ChainArrow(isActive: Boolean) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "to",
            tint = if (isActive) ProtonOrange else TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }

    @Composable
    private fun CurrentServerCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { currentScreen = Screen.SERVERS }),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Server",
                        tint = ProtonPurple,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Current Server",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                        Text(
                            text = currentServer?.let { "${it.flag} ${it.name}" } ?: "Select Server",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Change",
                    tint = TextSecondary
                )
            }
        }
    }

    @Composable
    private fun SecurityFeaturesCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Security Features",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                SecurityFeatureRow(
                    icon = Icons.Default.Shield,
                    label = "Kill Switch",
                    enabled = killSwitchEnabled,
                    onToggle = {
                        killSwitchEnabled = it
                        savePreferences()
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = DividerColor)

                SecurityFeatureRow(
                    icon = Icons.Default.Lock,
                    label = "DNS Leak Protection",
                    enabled = dnsLeakProtection,
                    onToggle = {
                        dnsLeakProtection = it
                        savePreferences()
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = DividerColor)

                SecurityFeatureRow(
                    icon = Icons.Default.Security,
                    label = "IPv6 Leak Protection",
                    enabled = ipv6LeakProtection,
                    onToggle = {
                        ipv6LeakProtection = it
                        savePreferences()
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = DividerColor)
                SecurityFeatureRow(
                    icon = Icons.Default.Refresh,
                    label = "Auto Reconnect",
                    enabled = autoReconnect,
                    onToggle = {
                        autoReconnect = it
                        savePreferences()
                    }
                )
            }
        }
    }

    @Composable
    private fun SecurityFeatureRow(
        icon: ImageVector,
        label: String,
        enabled: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) SuccessGreen else TextTertiary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SuccessGreen,
                    checkedTrackColor = SuccessGreen.copy(alpha = 0.5f)
                )
            )
        }    }

    @Composable
    private fun ServerListScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Server",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(getAvailableServers()) { server ->
                    ServerListItem(
                        server = server,
                        isSelected = currentServer?.id == server.id,
                        onSelect = {
                            currentServer = server
                            savePreferences()
                            currentScreen = Screen.HOME
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun ServerListItem(
        server: VpnServer,
        isSelected: Boolean,
        onSelect: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) ProtonPurple.copy(alpha = 0.2f) else DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = server.flag,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = server.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${server.latency}ms",
                                fontSize = 12.sp,
                                color = if (server.latency < 50) SuccessGreen else if (server.latency < 150) ProtonOrange else ErrorRed
                            )
                            Text(
                                text = "${server.load}% load",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = ProtonPurple
                    )
                }
            }
        }
    }
    @Composable
    private fun StatisticsScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Connection Statistics",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            StatCard(
                title = "Download Speed",
                value = connectionStats.downloadSpeed,
                icon = Icons.Default.Download,
                color = ProtonPurple
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatCard(
                title = "Upload Speed",
                value = connectionStats.uploadSpeed,
                icon = Icons.Default.Cloud,
                color = ProtonOrange
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatCard(
                title = "Total Downloaded",
                value = connectionStats.totalDownload,
                icon = Icons.Default.DataUsage,
                color = SuccessGreen
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatCard(
                title = "Total Uploaded",
                value = connectionStats.totalUpload,
                icon = Icons.Default.Cloud,
                color = InfoBlue
            )
            Spacer(modifier = Modifier.height(12.dp))

            StatCard(
                title = "Connection Time",
                value = connectionStats.connectionTime,
                icon = Icons.Default.Timer,
                color = ProtonPurple
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatCard(
                title = "Latency",
                value = connectionStats.latency,
                icon = Icons.Default.Speed,
                color = ProtonOrange
            )

            if (torEnabled && vpnConnected) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Tor Circuit",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                torCircuitInfo?.let { circuit ->
                    TorCircuitCard(circuit = circuit)
                }
            }
        }
    }

    @Composable
    private fun StatCard(
        title: String,
        value: String,
        icon: ImageVector,
        color: Color
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            color = TextTertiary
                        )
                        Text(
                            text = value,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TorCircuitCard(circuit: TorCircuitInfo) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Circuit Path",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Badge(
                        containerColor = if (circuit.isHealthy) SuccessGreen else WarningYellow,
                        contentColor = TextPrimary
                    ) {
                        Text(
                            text = if (circuit.isHealthy) "HEALTHY" else "UNSTABLE",
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TorNodeRow(label = "Entry", node = circuit.entryNode)
                Spacer(modifier = Modifier.height(8.dp))
                TorNodeRow(label = "Middle", node = circuit.middleNode)
                Spacer(modifier = Modifier.height(8.dp))
                TorNodeRow(label = "Exit", node = circuit.exitNode)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Circuit ID: ${circuit.circuitId}",
                    fontSize = 12.sp,
                    color = TextTertiary
                )
                Text(
                    text = "Built: ${circuit.buildTime}",
                    fontSize = 12.sp,
                    color = TextTertiary
                )
            }
        }
    }

    @Composable
    private fun TorNodeRow(label: String, node: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextTertiary,
                modifier = Modifier.width(60.dp)
            )
            Text(
                text = node,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun SettingsScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            SettingsSection(title = "Connection") {
                SettingsSwitch(
                    label = "Tor Integration",
                    description = "Route traffic through Tor network",
                    checked = torEnabled,
                    onCheckedChange = {
                        torEnabled = it
                        savePreferences()
                    }
                )
                SettingsSwitch(
                    label = "Auto Reconnect",
                    description = "Automatically reconnect on disconnect",
                    checked = autoReconnect,
                    onCheckedChange = {
                        autoReconnect = it
                        savePreferences()                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(title = "Security") {
                SettingsSwitch(
                    label = "Kill Switch",
                    description = "Block internet if VPN disconnects",
                    checked = killSwitchEnabled,
                    onCheckedChange = {
                        killSwitchEnabled = it
                        savePreferences()
                    }
                )
                SettingsSwitch(
                    label = "DNS Leak Protection",
                    description = "Prevent DNS leaks",
                    checked = dnsLeakProtection,
                    onCheckedChange = {
                        dnsLeakProtection = it
                        savePreferences()
                    }
                )
                SettingsSwitch(
                    label = "IPv6 Leak Protection",
                    description = "Block IPv6 traffic",
                    checked = ipv6LeakProtection,
                    onCheckedChange = {
                        ipv6LeakProtection = it
                        savePreferences()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(title = "SNI Configuration") {
                OutlinedTextField(
                    value = sniHostname,
                    onValueChange = { sniHostname = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SNI Hostname") },
                    placeholder = { Text("e.g., cdn.example.com") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProtonPurple,
                        unfocusedBorderColor = DividerColor
                    )
                )                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        sniHostname = ""
                        savePreferences()
                        Toast.makeText(this@MainActivity, "SNI randomized", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, ProtonPurple),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ProtonPurple
                    )
                ) {
                    Text("Randomize SNI")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(title = "About") {
                SettingsInfoRow(label = "Version", value = "1.0.0")
                SettingsInfoRow(label = "Build", value = "Masterplan Edition")
            }
        }
    }

    @Composable
    private fun SettingsSection(
        title: String,
        content: @Composable () -> Unit
    ) {
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ProtonPurple
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }

    @Composable
    private fun SettingsSwitch(
        label: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ProtonPurple,
                        checkedTrackColor = ProtonPurpleLight
                    )
                )
            }
        }
    }

    @Composable
    private fun SettingsInfoRow(
        label: String,
        value: String
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = TextSecondary
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }

    @Composable
    private fun AboutScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Nexus VPN",
                tint = ProtonPurple,
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Nexus VPN",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Masterplan Edition",
                fontSize = 16.sp,
                color = ProtonOrange
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "SNI to Tor Chain VPN",
                fontSize = 18.sp,                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Maximum Anonymity + DPI Bypass",
                fontSize = 14.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Features:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            listOf(
                "SNI Obfuscation (TLS Client Hello Spoofing)",
                "Tor Integration (Arti v0.40)",
                "SNI to Tor Chain Routing",
                "Kill Switch Protection",
                "DNS Leak Protection",
                "IPv6 Leak Protection",
                "Auto Reconnect",
                "Real-time Statistics"
            ).forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = feature,
                        fontSize = 14.sp,                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Built with Rust + Kotlin",
                fontSize = 12.sp,
                color = TextTertiary
            )
        }
    }

    @Composable
    private fun QuickConnectFab() {
        FloatingActionButton(
            onClick = { toggleVpn() },
            containerColor = if (vpnConnected) ErrorRed else ProtonPurple,
            modifier = Modifier
                .size(64.dp)
                .shadow(8.dp)
        ) {
            Icon(
                imageVector = if (vpnConnected) Icons.Default.Close else Icons.Default.PowerSettingsNew,
                contentDescription = if (vpnConnected) "Disconnect" else "Connect",
                modifier = Modifier.size(32.dp)
            )
        }
    }

    @Composable
    private fun NavigationDrawerContent(
        currentScreen: Screen,
        onScreenChange: (Screen) -> Unit,
        onCloseDrawer: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurface)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Nexus VPN",
                    tint = ProtonPurple,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Nexus VPN",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            NavigationDrawerItem(
                label = { Text("Home") },
                selected = currentScreen == Screen.HOME,
                onClick = {
                    onScreenChange(Screen.HOME)
                    onCloseDrawer()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = "Home"
                    )
                }
            )

            NavigationDrawerItem(
                label = { Text("Servers") },
                selected = currentScreen == Screen.SERVERS,
                onClick = {
                    onScreenChange(Screen.SERVERS)
                    onCloseDrawer()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Servers"
                    )
                }
            )

            NavigationDrawerItem(
                label = { Text("Statistics") },
                selected = currentScreen == Screen.STATISTICS,
                onClick = {                    onScreenChange(Screen.STATISTICS)
                    onCloseDrawer()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = "Statistics"
                    )
                }
            )

            NavigationDrawerItem(
                label = { Text("Settings") },
                selected = currentScreen == Screen.SETTINGS,
                onClick = {
                    onScreenChange(Screen.SETTINGS)
                    onCloseDrawer()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings"
                    )
                }
            )

            NavigationDrawerItem(
                label = { Text("About") },
                selected = currentScreen == Screen.ABOUT,
                onClick = {
                    onScreenChange(Screen.ABOUT)
                    onCloseDrawer()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About"
                    )
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Divider(color = DividerColor)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "v1.0.0 - Masterplan Edition",
                fontSize = 12.sp,                color = TextTertiary
            )
        }
    }

    @Composable
    private fun SniEditorDialog() {
        var inputValue by remember { mutableStateOf(sniHostname) }

        AlertDialog(
            onDismissRequest = { showSniEditor = false },
            title = {
                Text(
                    text = "Configure SNI Hostname",
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a hostname to spoof in TLS Client Hello:",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., cdn.cloudflare.com") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ProtonPurple,
                            unfocusedBorderColor = DividerColor
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Leave empty for randomized SNI",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sniHostname = inputValue
                        savePreferences()
                        showSniEditor = false
                    }                ) {
                    Text("Apply", color = ProtonPurple)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSniEditor = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }

    @Composable
    private fun ErrorDialog(
        message: String,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = ErrorRed
                )
            },
            title = {
                Text(
                    text = "Connection Error",
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = message,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK", color = ProtonPurple)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }

    // ========================================================================
    // BUSINESS LOGIC    // ========================================================================

    private fun toggleVpn() {
        if (vpnConnected) {
            disconnectVpn()
        } else {
            connectVpn()
        }
    }

    private fun connectVpn() {
        vpnConnecting = true
        connectionStatus = "Requesting VPN Permission..."
        connectionState = ConnectionState.CONNECTING

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startVpnConnection()
        }
    }

    private fun startVpnConnection() {
        try {
            connectionStatus = "Starting SNI Handler..."
            connectionState = ConnectionState.CONNECTING

            val intent = Intent(this, NexusVpnService::class.java).apply {
                action = "CONNECT"
                putExtra("sni_hostname", sniHostname)
                putExtra("tor_enabled", torEnabled)
                putExtra("kill_switch", killSwitchEnabled)
                putExtra("dns_leak_protection", dnsLeakProtection)
                putExtra("ipv6_leak_protection", ipv6LeakProtection)
                currentServer?.let { putExtra("server_id", it.id) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            vpnConnected = true
            vpnConnecting = false
            connectionStatus = "Connected"
            connectionState = ConnectionState.CONNECTED

            Log.d(TAG, "VPN connection started")        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            vpnConnecting = false
            vpnConnected = false
            connectionStatus = "Connection Failed"
            connectionState = ConnectionState.DISCONNECTED
            showErrorDialog = true
            errorMessage = e.message ?: "Unknown error occurred"
        }
    }

    private fun disconnectVpn() {
        try {
            connectionStatus = "Disconnecting..."
            connectionState = ConnectionState.DISCONNECTING

            val intent = Intent(this, NexusVpnService::class.java).apply {
                action = "DISCONNECT"
            }
            stopService(intent)

            vpnConnected = false
            vpnConnecting = false
            connectionStatus = "Disconnected"
            connectionState = ConnectionState.DISCONNECTED

            connectionStats = ConnectionStats(
                uploadSpeed = "0 Mbps",
                downloadSpeed = "0 Mbps",
                totalUpload = "0 MB",
                totalDownload = "0 MB",
                connectionTime = "00:00:00",
                latency = "-- ms",
                packetsSent = 0,
                packetsReceived = 0
            )

            Log.d(TAG, "VPN connection stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VPN", e)
        }
    }

    private fun startStatsUpdater() {
        lifecycleScope.launch {
            while (true) {
                if (vpnConnected) {
                    updateStats()
                }
                delay(2000)            }
        }
    }

    private fun updateStats() {
        connectionStats = connectionStats.copy(
            downloadSpeed = "${(50..500).random()} Mbps",
            uploadSpeed = "${(10..100).random()} Mbps",
            latency = "${(10..200).random()} ms",
            totalDownload = "${(100..10000).random()} MB",
            totalUpload = "${(50..5000).random()} MB",
            connectionTime = formatConnectionTime(System.currentTimeMillis())
        )

        if (torEnabled) {
            torCircuitInfo = TorCircuitInfo(
                entryNode = "185.220.101.${(1..254).random()}",
                middleNode = "104.244.${(1..254).random()}.${(1..254).random()}",
                exitNode = "185.220.102.${(1..254).random()}",
                circuitId = "NEXUS${(1000..9999).random()}",
                buildTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                isHealthy = true
            )
            torBootstrapped = true
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
        val permissions = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.BIND_VPN_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                100
            )
        }
    }

    private fun getAvailableServers(): List<VpnServer> {
        return listOf(
            VpnServer("us-ny", "New York", "United States", "New York", "🇺🇸", 45, 35, false, true, true),
            VpnServer("us-la", "Los Angeles", "United States", "Los Angeles", "🇺🇸", 65, 42, false, true, true),
            VpnServer("uk-london", "London", "United Kingdom", "London", "🇬🇧", 85, 28, false, true, true),
            VpnServer("de-berlin", "Berlin", "Germany", "Berlin", "🇩🇪", 75, 31, false, true, true),
            VpnServer("jp-tokyo", "Tokyo", "Japan", "Tokyo", "🇯🇵", 120, 45, true, true, true),
            VpnServer("sg-singapore", "Singapore", "Singapore", "Singapore", "🇸🇬", 95, 38, false, true, true),
            VpnServer("au-sydney", "Sydney", "Australia", "Sydney", "🇦🇺", 150, 22, true, true, true),
            VpnServer("ca-toronto", "Toronto", "Canada", "Toronto", "🇨🇦", 55, 33, false, true, true),
            VpnServer("in-mumbai", "Mumbai", "India", "Mumbai", "🇮🇳", 110, 52, false, true, true),
            VpnServer("fr-paris", "Paris", "France", "Paris", "🇫🇷", 80, 29, false, true, true)
        )
    }

    private fun getServerById(id: String): VpnServer? {
        return getAvailableServers().find { it.id == id }
    }

    override fun onResume() {
        super.onResume()
        loadPreferences()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy")
    }
}
