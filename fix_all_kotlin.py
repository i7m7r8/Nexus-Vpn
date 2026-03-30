#!/usr/bin/env python3
import sys
from pathlib import Path

# ── ROOT CAUSE SUMMARY ──────────────────────────────────────────
# AI generated code with statements concatenated on same line
# (missing newlines), plus a bad import and missing icon.
# ────────────────────────────────────────────────────────────────

# ════════════════════════════════════════════════════════════════
# FILE 1: MainActivity.kt
# ════════════════════════════════════════════════════════════════
p = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
c = p.read_text()

# FIX 1: Remove bad weight import (line 97)
# weight() is a RowScope/ColumnScope extension, not importable directly
c = c.replace("import androidx.compose.foundation.layout.weight\n", "")

# FIX 2: Missing newline - L276: }Spacer concatenated
c = c.replace(
    "}                    Spacer(modifier = Modifier.height(24.dp))",
    "}\n                    Spacer(modifier = Modifier.height(24.dp))"
)

# FIX 3: QuickStatCard uses weight(1f) but it's a private fun inside class
# - NOT inside a RowScope. Remove weight, use fillMaxWidth instead.
c = c.replace(
    "Card(modifier = Modifier.weight(1f),",
    "Card(modifier = Modifier.fillMaxWidth(),"
)

# FIX 4: Missing newline - L424: currentServer = server + savePreferences()
c = c.replace(
    "currentServer = server                        savePreferences()",
    "currentServer = server\n                        savePreferences()"
)

# FIX 5: Icons.Default.Download doesn't exist - use ArrowDownward or DataUsage
c = c.replace(
    "Icons.Default.Download,",
    "Icons.Default.DataUsage,"
)

# FIX 6: Missing newline - L571: connectionStatus + val vpnIntent concatenated
c = c.replace(
    "connectionStatus = \"Requesting VPN Permission...\"        val vpnIntent = VpnService.prepare(this)",
    "connectionStatus = \"Requesting VPN Permission...\"\n        val vpnIntent = VpnService.prepare(this)"
)
# FIX 7: Add ArrowDownward icon import just in case
if "import androidx.compose.material.icons.filled.ArrowDownward" not in c:
    c = c.replace(
        "import androidx.compose.material.icons.filled.DataUsage\n",
        "import androidx.compose.material.icons.filled.DataUsage\nimport androidx.compose.material.icons.filled.ArrowDownward\n"
    )

p.write_text(c)
print("✅ MainActivity.kt fixed")

# ════════════════════════════════════════════════════════════════
# FILE 2: NexusVpnService.kt
# ════════════════════════════════════════════════════════════════
p2 = Path("android/app/src/main/kotlin/com/nexusvpn/android/service/NexusVpnService.kt")
c2 = p2.read_text()

# FIX: Missing newline - L189: delay(5000) + if concatenated
c2 = c2.replace(
    "delay(5000)            if (!isConnected.get())",
    "delay(5000)\n            if (!isConnected.get())"
)

p2.write_text(c2)
print("✅ NexusVpnService.kt fixed")

# ════════════════════════════════════════════════════════════════
# FILE 3: VpnMonitoringService.kt
# ════════════════════════════════════════════════════════════════
p3 = Path("android/app/src/main/kotlin/com/nexusvpn/android/service/VpnMonitoringService.kt")
c3 = p3.read_text()

# FIX: Missing newline - L37: sniHealthy + private var torHealthy concatenated
c3 = c3.replace(
    "AtomicBoolean(false)    private var torHealthy = AtomicBoolean(false)",
    "AtomicBoolean(false)\n    private var torHealthy = AtomicBoolean(false)"
)

p3.write_text(c3)
print("✅ VpnMonitoringService.kt fixed")

print("\n📋 ROOT CAUSE: AI generated Kotlin code with missing newlines")
print("   between statements on the same line (4 files affected).")
print("   Also: bad weight import + missing Icons.Default.Download.")
