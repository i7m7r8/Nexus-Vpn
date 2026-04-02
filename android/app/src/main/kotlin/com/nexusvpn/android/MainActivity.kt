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
import com.nexusvpn.android.data.PreferencesManager
import com.nexusvpn.android.service.NexusVpnService
import com.nexusvpn.android.ui.theme.NexusVpnTheme
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NexusVpnApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusVpnApp() {
    NexusVpnTheme {
        var currentScreen by remember { mutableStateOf("connection") }

        Scaffold(
            topBar = {
                if (currentScreen == "settings") {
                    SmallTopAppBar(
                        title = { Text("Settings") },
                        navigationIcon = {
                            IconButton(onClick = { currentScreen = "connection" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.smallTopAppBarColors(
                            containerColor = Color(0xFF1B1B1F)
                        )
                    )
                } else {
                    SmallTopAppBar(
                        title = { Text("Nexus VPN", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { currentScreen = "settings" }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        colors = TopAppBarDefaults.smallTopAppBarColors(
                            containerColor = Color(0xFF1B1B1F)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .background(Color(0xFF13131F))
            ) {
                when (currentScreen) {
                    "connection" -> ConnectionScreen()
                    "settings" -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun ConnectionScreen() {
    val context = LocalContext.current
    val prefs = (context.applicationContext as com.nexusvpn.android.NexusVpnApplication).prefs
    val prefsObj = prefs

    var isConnected by remember { mutableStateOf(false) }
    var connectionStateText by remember { mutableStateOf("Disconnected") }
    var sniHost by remember { mutableStateOf(prefs.sniHostname ?: "www.cloudflare.com") }
    var duration by remember { mutableStateOf("00:00:00") }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            var elapsed = 0L
            while (true) {
                elapsed += 1000
                val h = elapsed / 3600000
                val m = (elapsed % 3600000) / 60000
                val s = (elapsed % 60000) / 1000
                duration = String.format("%02d:%02d:%02d", h, m, s)
                delay(1000)
            }
        } else {
            duration = "00:00:00"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Status indicator
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(50),
                color = if (isConnected) Color(0xFF01A981) else Color(0xFF8A2BE2),
                tonalElevation = 4.dp
            ) {}
            Text(
                text = if (isConnected) "🟢" else "⚡",
                fontSize = 40.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = connectionStateText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        if (isConnected) {
            Text(
                text = "Connected via Tor",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = duration,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Connect / Disconnect button
        Button(
            onClick = {
                if (!isConnected) {
                    val intent = VpnService.prepare(context)
                    if (intent != null) {
                        context.startActivity(intent)
                        // After permission granted
                        return@Button
                    }
                    prefs.sniHostname = sniHost
                    context.startService(
                        Intent(context, NexusVpnService::class.java).apply { action = "CONNECT" }
                    )
                    connectionStateText = "Connecting…"
                    isConnected = true
                } else {
                    context.startService(
                        Intent(context, NexusVpnService::class.java).apply { action = "DISCONNECT" }
                    )
                    connectionStateText = "Disconnected"
                    isConnected = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) Color(0xFFFF5252) else Color(0xFF8A2BE2),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = if (isConnected) "DISCONNECT" else "   CONNECT   ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SNI Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F2E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SNI Configuration",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = sniHost,
                    onValueChange = { sniHost = it },
                    label = { Text("SNI Hostname") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFAAAAAA),
                        focusedContainerColor = Color(0xFF13131F),
                        unfocusedContainerColor = Color(0xFF13131F),
                        focusedBorderColor = Color(0xFF8A2BE2),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        prefs.sniHostname = sniHost
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF01A981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply SNI Change", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = 0xFF1F1F2E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16)) {
                Text(
                    text = "Quick Settings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                ToggleSettingRow(
                    title = "Kill Switch",
                    checked = prefsObj.killSwitch,
                    onCheckedChange = { prefsObj.killSwitch = it }
                )
                HorizontalDivider(color = Color.Gray.copy(alpha = 2f))
                ToggleSettingRow(
                    title = "Always-on VPN",
                    checked = prefsObj.alwaysOnVpn,
                    onCheckedChange = { prefsObj.alwaysOnVpn = it }
                )
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                ToggleSettingRow(
                    title = "Auto-connect WiFi",
                    checked = prefsObj.autoConnectWifi,
                    onCheckedChange = { prefsObj.autoConnectWifi = it }
                )
            }
        }
    }
}

@Composable
fun ToggleSettingRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF01A981),
                checkedTrackColor = Color(0xFF01A981).copy(alpha = 0.5f)
            )
        )
    }
}

// ---------- Settings Screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val prefs = NexusVpnApplication.prefs
    var useBridges by remember { mutableStateOf(prefs.useBridges) }
    var bridgeType by remember { mutableStateOf(prefs.bridgeType ?: "obfs4") }
    var customBridgeLine by remember { mutableStateOf(prefs.customBridgeLine ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        // Bridge Configuration
        Text("Bridge Configuration", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F2E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SwitchSettingRow("Use Bridges", useBridges, {
                    useBridges = it
                    prefs.useBridges = it
                })
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                Text("Bridge Type", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val bridges = listOf("obfs4", "meek-amazon", "snowflake")
                bridges.forEach { bridge ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                bridgeType = bridge
                                prefs.bridgeType = bridge
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = bridgeType == bridge,
                            onClick = {
                                bridgeType = bridge
                                prefs.bridgeType = bridge
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF8A2BE2))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(bridge, color = Color.White, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customBridgeLine,
                    onValueChange = { 
                        customBridgeLine = it
                        prefs.customBridgeLine = it
                    },
                    label = { Text("Custom Bridge Line") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFAAAAAA),
                        focusedContainerColor = Color(0xFF13131F),
                        unfocusedContainerColor = Color(0xFF13131F)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Privacy & Security", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = 0xFF1F1F2E),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("• DNS Leak Protection Enabled", color = Color.White, fontSize = 14.sp)
                Text("• IPv6 Leak Protection Enabled", color = Color.White, fontSize = 14.sp)
                Text("• Real IP Leak Protection", color = Color.White, fontSize = 14.sp)
                Text("• All traffic routed through Tor", color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Connection Logs", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = 0xFF1F1F2E),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {}
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View Connection Logs", color = Color.White, fontSize = 14.sp)
                Text("→", color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("About", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F2E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Nexus VPN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("v1.0.0", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pure Rust Tor + SNI VPN", color = Color.Gray, fontSize = 12.sp)
                Text("No telemetry, no logs, no tracking", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        prefs.clear()
                        useBridges = false
                        prefs.useBridges = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Defaults", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
