#!/usr/bin/env python3
import sys
from pathlib import Path

# ─────────────────────────────────────────────────────────────────
# FIX 1: Add material-icons-extended dependency to build.gradle
# Icons is from material.icons which needs a separate artifact
# ─────────────────────────────────────────────────────────────────
gradle_path = Path("android/app/build.gradle")
gradle = gradle_path.read_text()
gradle = gradle.replace(
    "    implementation 'androidx.compose.material3:material3'",
    "    implementation 'androidx.compose.material3:material3'\n    implementation 'androidx.compose.material:material-icons-extended'"
)
gradle_path.write_text(gradle)
print("✅ Added material-icons-extended to dependencies")

# ─────────────────────────────────────────────────────────────────
# FIX 2: Fix MainActivity.kt
# ─────────────────────────────────────────────────────────────────
kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()

# Fix 2a: Add NexusVpnService import
content = content.replace(
    'import androidx.lifecycle.lifecycleScope\n',
    'import androidx.lifecycle.lifecycleScope\nimport com.nexusvpn.android.service.NexusVpnService\n'
)

# Fix 2b: Add ImageVector import
content = content.replace(
    'import androidx.compose.material.icons.filled.*\n',
    'import androidx.compose.material.icons.filled.*\nimport androidx.compose.ui.graphics.vector.ImageVector\n'
)

# Fix 2c: Fix LargeStatCard icon param type
content = content.replace(
    'fun LargeStatCard(label: String, value: String, icon: androidx.compose.material.icons.materialIcon)',
    'fun LargeStatCard(label: String, value: String, icon: ImageVector)'
)

# Fix 2d: StatCard uses .weight(1f) but it's a top-level @Composable not inside RowScope
# Change the function to accept a Modifier param, remove weight from inside
content = content.replace(
    'fun StatCard(label: String, value: String) {\n    Card(\n        modifier = Modifier\n            .weight(1f)\n            .padding(4.dp),',
    'fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {\n    Card(\n        modifier = modifier.padding(4.dp),'
)

# Fix 2e: Update StatCard call sites to pass weight from RowScope
content = content.replace(
    'StatCard("Speed", connectionSpeed)',
    'StatCard("Speed", connectionSpeed, modifier = Modifier.weight(1f))'
)
content = content.replace(
    'StatCard("Latency", connectionLatency)',
    'StatCard("Latency", connectionLatency, modifier = Modifier.weight(1f))'
)
content = content.replace(
    'StatCard("Data Used", dataUsed)',
    'StatCard("Data Used", dataUsed, modifier = Modifier.weight(1f))'
)

# Fix 2f: Remove invalid .align() on NavigationBar
content = content.replace(
    '    NavigationBar(\n        modifier = Modifier.align(Alignment.BottomCenter),\n        containerColor = DarkSurface\n    )',
    '    NavigationBar(\n        containerColor = DarkSurface\n    )'
)

# Fix 2g: Add file-level OptIn for experimental Material3 APIs
if '@file:OptIn' not in content:
    content = content.replace(
        'package com.nexusvpn.android\n',
        '@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\npackage com.nexusvpn.android\n'
    )

kt_path.write_text(content)
print("✅ MainActivity.kt fixed:")
print("   - NexusVpnService import added")
print("   - ImageVector import + LargeStatCard icon type fixed")
print("   - StatCard weight moved to call sites via modifier param")
print("   - NavigationBar align removed")
print("   - ExperimentalMaterial3Api OptIn added")
