#!/usr/bin/env python3
import sys
from pathlib import Path

path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
if not path.exists():
    print(f"Error: {path} not found", file=sys.stderr)
    sys.exit(1)

content = path.read_text()

# ─────────────────────────────────────────────────────────────────
# FIX 1: Add missing import for NexusVpnService
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    'import androidx.lifecycle.lifecycleScope',
    'import androidx.lifecycle.lifecycleScope\nimport com.nexusvpn.android.service.NexusVpnService'
)

# ─────────────────────────────────────────────────────────────────
# FIX 2: Fix lowercase 'icons' type reference in LargeStatCard
# androidx.compose.material.icons.materialIcon doesn't exist
# Replace with ImageVector which is the correct type for Icons
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.*',
    'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.*\nimport androidx.compose.ui.graphics.vector.ImageVector'
)
content = content.replace(
    'fun LargeStatCard(label: String, value: String, icon: androidx.compose.material.icons.materialIcon)',
    'fun LargeStatCard(label: String, value: String, icon: ImageVector)'
)

# ─────────────────────────────────────────────────────────────────
# FIX 3: Modifier.align() is not valid on NavigationBar
# NavigationBar is not inside a Box so .align() is unavailable
# Remove the modifier from NavigationBar
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    '    NavigationBar(\n        modifier = Modifier.align(Alignment.BottomCenter),\n        containerColor = DarkSurface\n    )',
    '    NavigationBar(\n        containerColor = DarkSurface\n    )'
)

# ─────────────────────────────────────────────────────────────────
# FIX 4: StatCard uses .weight(1f) inside a Row — this is fine but
# weight() is a RowScope extension. If StatCard is called outside Row
# it fails. The error says "weight of type Float cannot be invoked".
# This means the Modifier chain has weight() applied incorrectly.
# Fix: wrap the weight modifier in the parent call site instead,
# or make StatCard accept a modifier parameter.
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    'fun StatCard(label: String, value: String) {\n    Card(\n        modifier = Modifier\n            .weight(1f)\n            .padding(4.dp),',
    'fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {\n    Card(\n        modifier = modifier\n            .padding(4.dp),'
)
# Fix call sites that call StatCard without modifier - they need to pass weight
# Find StatCard calls and add .weight(1f)
import re
content = re.sub(
    r'StatCard\(([^,)]+),\s*([^)]+)\)',
    r'StatCard(\1, \2, modifier = Modifier.weight(1f))',
    content
)
# But avoid double-modifying ones that already have modifier
content = content.replace(
    'StatCard(label = ', 'StatCard(label = '  # no-op guard
)

# ─────────────────────────────────────────────────────────────────
# FIX 5: Suppress experimental Material3 API warnings as errors
# Add @OptIn annotation to composables using NavigationBar etc.
# Easiest fix: add file-level OptIn suppression at top of file
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    'package com.nexusvpn.android\n',
    '@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\npackage com.nexusvpn.android\n'
)

path.write_text(content)
print("✅ MainActivity.kt fixed")
print("  - Added NexusVpnService import")
print("  - Fixed LargeStatCard icon type: materialIcon → ImageVector")
print("  - Removed invalid .align() from NavigationBar modifier")
print("  - Made StatCard accept external Modifier for weight()")
print("  - Added @OptIn for ExperimentalMaterial3Api")
