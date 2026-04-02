package com.nexusvpn.android

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusvpn.android.service.NexusVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NexusVpnApp() }
    }
}

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
                    if (screen == "settings") {
                        IconButton(onClick = { screen = "home" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    } else {
                        Text("Nexus VPN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    if (screen == "home") {
                        IconButton(onClick = { screen = "settings" }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                }

                // Screen content
                when (screen) {
                    "home" -> HomeScreen(darkBg, cardBg, green, purple)
                    "settings" -> SettingsScreen(darkBg, cardBg, green, purple)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(darkBg: Color, cardBg: Color, green: Color, purple: Color) {
    val ctx = LocalContext.current
    val prefs = (ctx.applicationContext as NexusVpnApplication).prefs
    var sniHost by remember { mutableStateOf(prefs.sniHostname ?: "cdn.cloudflare.net") }
    var connected by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }

    LaunchedEffect(connected) {
        if (connected) {
            while (true) { delay(1000); elapsed++ }
        } else elapsed = 0
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status circle
        Box(
            Modifier.size(140.dp).clip(CircleShape).background(if (connected) green else purple.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (connected) "✓" else "⚡", fontSize = 48.sp, color = if (connected) Color.Black else Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (connected) "Connected" else "Disconnected",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
        )
        if (connected) {
            val m = elapsed / 60; val s = elapsed % 60
            Text("${m}m ${s}s", color = Color.Gray, fontSize = 16.sp)
        }

        Spacer(Modifier.height(28.dp))

        // CONNECT button
        Button(
            onClick = {
                if (!connected) {
                    val vp = VpnService.prepare(ctx)
                    if (vp != null) { ctx.startActivity(vp); return@Button }
                    prefs.sniHostname = sniHost
                    ctx.startService(Intent(ctx, NexusVpnService::class.java).apply { action = "CONNECT" })
                    connected = true
                } else {
                    ctx.startService(Intent(ctx, NexusVpnService::class.java).apply { action = "DISCONNECT" })
                    connected = false
                }
            },
            Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (connected) Color(0xFFFA1946) else green)
        ) {
            Text(if (connected) "DISCONNECT" else "CONNECT", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
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
        // Stats card
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("⚡ Quick Settings", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
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
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Switch(checked, onChange)
    }
}

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
