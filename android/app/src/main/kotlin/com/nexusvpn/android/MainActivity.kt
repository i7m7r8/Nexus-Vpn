package com.nexusvpn.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexusvpn.android.service.NexusVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NexusVpnApp() }
    }
}

// ===========================================================================
// App state
// ===========================================================================

data class VpnStatus(
    val connected: Boolean = false,
    val statusText: String = "Disconnected",
    val sniHost: String = "",
    val bridgesActive: Boolean = false,
)

@Composable
fun NexusVpnApp() {
    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val green = Color(0xFF3FB950)
    val purple = Color(0xFFA371F7)
    var screen by remember { mutableStateOf("home") }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = green, secondary = purple, background = darkBg, surface = cardBg)
    ) {
        Surface(modifier = Modifier.fillMaxSize().background(darkBg)) {
            Column {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (screen) {
                        "logs" -> IconButton(onClick = { screen = "home" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        "settings" -> IconButton(onClick = { screen = "home" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        else -> Text("Nexus VPN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    if (screen == "home") {
                        Row {
                            IconButton(onClick = { screen = "logs" }) {
                                Icon(Icons.Default.ListAlt, contentDescription = "Logs", tint = Color.White)
                            }
                            IconButton(onClick = { screen = "settings" }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }
                }

                // Screen content
                when (screen) {
                    "home" -> HomeScreen(darkBg, cardBg, green, purple)
                    "settings" -> SettingsScreen(darkBg, cardBg, green, purple)
                    "logs" -> LogScreen(darkBg, cardBg, green)
                }
            }
        }
    }
}

// ===========================================================================
// Home Screen — real status via BroadcastReceiver
// ===========================================================================

@Composable
fun HomeScreen(darkBg: Color, cardBg: Color, green: Color, purple: Color) {
    val ctx = LocalContext.current
    val prefs = (ctx.applicationContext as NexusVpnApplication).prefs
    var sniHost by remember { mutableStateOf(prefs.sniHostname ?: "cdn.cloudflare.net") }
    var vpnStatus by remember { mutableStateOf(VpnStatus()) }
    var elapsed by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // BroadcastReceiver for real-time status from NexusVpnService
    LaunchedEffect(Unit) {
        val filter = IntentFilter(NexusVpnService.ACTION_STATUS)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                vpnStatus = VpnStatus(
                    connected = intent.getStringExtra(NexusVpnService.EXTRA_STATUS) != "Disconnected",
                    statusText = intent.getStringExtra(NexusVpnService.EXTRA_STATUS) ?: "Disconnected",
                    sniHost = intent.getStringExtra(NexusVpnService.EXTRA_SNI) ?: "",
                    bridgesActive = intent.getBooleanExtra(NexusVpnService.EXTRA_BRIDGES, false),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, filter)
        }
        // Check initial state
        if (prefs.isVpnConnected) {
            vpnStatus = VpnStatus(
                connected = true,
                statusText = if (prefs.useBridges) "Connected via Bridge + Tor" else "Connected via Tor",
                sniHost = prefs.sniHostname ?: "",
                bridgesActive = prefs.useBridges,
            )
        }
        // Cleanup
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                runCatching { ctx.unregisterReceiver(receiver) }
            }
        }
    }

    LaunchedEffect(vpnStatus.connected) {
        if (vpnStatus.connected) {
            while (true) { delay(1000); elapsed++ }
        } else elapsed = 0
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status circle
        Box(
            Modifier.size(140.dp).clip(CircleShape).background(if (vpnStatus.connected) green else purple.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (vpnStatus.connected) "✓" else "⚡", fontSize = 48.sp, color = if (vpnStatus.connected) Color.Black else Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            vpnStatus.statusText,
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
        )
        if (vpnStatus.connected) {
            val m = elapsed / 60; val s = elapsed % 60
            Text("${m}m ${s}s", color = Color.Gray, fontSize = 16.sp)
        }

        Spacer(Modifier.height(28.dp))

        // CONNECT / DISCONNECT button
        Button(
            onClick = {
                if (!vpnStatus.connected) {
                    val vp = VpnService.prepare(ctx)
                    if (vp != null) { ctx.startActivity(vp); return@Button }
                    prefs.sniHostname = sniHost
                    ctx.startService(Intent(ctx, NexusVpnService::class.java).apply { action = "CONNECT" })
                } else {
                    ctx.startService(Intent(ctx, NexusVpnService::class.java).apply { action = "DISCONNECT" })
                }
            },
            Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (vpnStatus.connected) Color(0xFFFA1946) else green)
        ) {
            Text(if (vpnStatus.connected) "DISCONNECT" else "CONNECT", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        }
        Spacer(Modifier.height(24.dp))

        // SNI card
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("🔥 SNI Routing", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    sniHost, { sniHost = it },
                    label = { Text("SNI Hostname") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.Gray,
                        focusedBorderColor = green, unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(Modifier.height(8.dp))
                Button({ prefs.sniHostname = sniHost; NexusVpnService.setSniHostnameNative(sniHost) },
                    colors = ButtonDefaults.buttonColors(containerColor = purple), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Apply SNI Change", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Connection details card (shows bridge status)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("⚡ Connection Details", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                StatusRow("SNI Host", vpnStatus.sniHost.ifEmpty { prefs.sniHostname ?: "—" })
                HorizontalDivider(color = Color.Gray.copy(0.2f))
                StatusRow("Bridges", if (vpnStatus.bridgesActive) "✅ ${prefs.bridgeType}" else "❌ Disabled")
                HorizontalDivider(color = Color.Gray.copy(0.2f))
                StatusRow("Kill Switch", if (prefs.killSwitch) "✅ Enabled" else "❌ Disabled")
                HorizontalDivider(color = Color.Gray.copy(0.2f))
                StatusRow("Tor Core", "Arti v0.40.0 (Rust)")
            }
        }
        Spacer(Modifier.height(16.dp))

        // Quick toggles
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("🛡 Quick Settings", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
                ToggleRow("Kill Switch", prefs.killSwitch, { prefs.killSwitch = it })
                HorizontalDivider(color = Color.Gray.copy(0.2f))
                ToggleRow("Always-On VPN", prefs.alwaysOnVpn, { prefs.alwaysOnVpn = it })
                HorizontalDivider(color = Color.Gray.copy(0.2f))
                ToggleRow("Auto-Connect WiFi", prefs.autoConnectWifi, { prefs.autoConnectWifi = it })
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Switch(checked, onChange)
    }
}

// ===========================================================================
// Settings Screen
// ===========================================================================

@Composable
fun SettingsScreen(darkBg: Color, cardBg: Color, green: Color, purple: Color) {
    val prefs = (LocalContext.current.applicationContext as NexusVpnApplication).prefs
    var bridgeType by remember { mutableStateOf(prefs.bridgeType) }
    var customBridge by remember { mutableStateOf(prefs.customBridgeLine ?: "") }
    var useBridges by remember { mutableStateOf(prefs.useBridges) }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("🌐 Bridge Configuration", fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Use Bridges", color = Color.White); Switch(useBridges, { useBridges = it; prefs.useBridges = it })
                }
                Spacer(Modifier.height(4.dp))
                listOf("obfs4", "meek", "snowflake").forEach { bridge ->
                    Row(Modifier.fillMaxWidth().clickable { bridgeType = bridge; prefs.bridgeType = bridge }.padding(vertical = 6.dp)) {
                        RadioButton(bridgeType == bridge, { bridgeType = bridge; prefs.bridgeType = bridge }, colors = RadioButtonDefaults.colors(selectedColor = purple))
                        Spacer(Modifier.width(8.dp)); Text(bridge, color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(customBridge, { prefs.customBridgeLine = it; customBridge = it },
                    label = { Text("Custom Bridge Line") }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.Gray))
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🛡 Security", fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("✅ No Logs", color = Color.Gray, fontSize = 12.sp)
                Text("✅ Kill Switch", color = Color.Gray, fontSize = 12.sp)
                Text("✅ Pure Rust Tor (Arti)", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

// ===========================================================================
// Log Screen — reads from Rust-side log buffer via JNI
// ===========================================================================

@Composable
fun LogScreen(darkBg: Color, cardBg: Color, green: Color) {
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Poll logs from Rust buffer every 500ms when running
    LaunchedEffect(running) {
        if (running) {
            NexusVpnService.clearLogsNative()
            while (running) {
                delay(500)
                val raw = NexusVpnService.getLogsNative()
                if (raw.isNotEmpty()) {
                    val newLines = raw.split("\n").filter { it.isNotBlank() }
                    if (newLines.isNotEmpty()) {
                        logs = logs + newLines
                        // Auto-scroll to bottom
                        if (logs.size > 1) {
                            listState.animateScrollToItem(logs.size - 1)
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("📋 Live Logs", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
            Button(
                onClick = {
                    running = !running
                    if (!running) {
                        logs = logs + "--- Log stream stopped ---"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFFFA1946) else green),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (running) "Stop" else "Start", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Press Start to capture logs", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(cardBg, RoundedCornerShape(12.dp)).padding(12.dp),
            ) {
                items(logs) { line ->
                    val colored = when {
                        line.contains("ERROR", ignoreCase = true) || line.contains("❌", ignoreCase = true) -> Color(0xFFFF6B6B)
                        line.contains("WARN", ignoreCase = true) || line.contains("⚠️", ignoreCase = true) -> Color(0xFFFFD93D)
                        line.contains("✅", ignoreCase = true) -> Color(0xFF3FB950)
                        line.contains("🎭", ignoreCase = true) -> Color(0xFFA371F7)
                        line.contains("🔗", ignoreCase = true) -> Color(0xFF58A6FF)
                        else -> Color(0xFFC9D1D9)
                    }
                    Text(
                        text = line,
                        color = colored,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
