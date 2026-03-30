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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexusvpn.android.service.NexusVpnService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sharedPrefs: SharedPreferences by lazy {
        getSharedPreferences("nexus_vpn_prefs", Context.MODE_PRIVATE)
    }

    private var vpnConnected = mutableStateOf(false)
    private var currentServer = mutableStateOf("Select Server")
    private var sniHostname = mutableStateOf("")
    private var torEnabled = mutableStateOf(true)
    private var connectionStatus = mutableStateOf("Not Connected")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()
        requestVpnPermissions()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nexus VPN", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B2BE2))
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(connectionStatus.value, fontSize = 16.sp, color = if (vpnConnected.value) Color.Green else Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { toggleVpn() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (vpnConnected.value) Color.Green else Color(0xFF6F02B5)),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Text(if (vpnConnected.value) "Disconnect" else "Connect", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = sniHostname.value,
                            onValueChange = { sniHostname.value = it },
                            label = { Text("SNI Hostname") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tor Enabled", color = Color.White)
                            Switch(checked = torEnabled.value, onCheckedChange = { torEnabled.value = it })
                        }
                    }
                }
            }
        }
    }

    private fun loadPreferences() {
        sniHostname.value = sharedPrefs.getString("sni_hostname", "") ?: ""
        torEnabled.value = sharedPrefs.getBoolean("tor_enabled", true)
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
        with(sharedPrefs.edit()) {
            putString("sni_hostname", sniHostname.value)
            putBoolean("tor_enabled", torEnabled.value)            apply()
        }
    }

    private fun startVpnConnection() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, 101)
        } else {
            val intent = Intent(this, NexusVpnService::class.java).apply {
                action = "CONNECT"
                putExtra("sni_hostname", sniHostname.value)
                putExtra("tor_enabled", torEnabled.value)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            connectionStatus.value = "Connected"
        }
    }

    private fun stopVpnConnection() {
        val intent = Intent(this, NexusVpnService::class.java).apply {
            action = "DISCONNECT"
        }
        stopService(intent)
    }

    private fun requestVpnPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.BIND_VPN_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }
}
