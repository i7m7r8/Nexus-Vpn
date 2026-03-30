#!/usr/bin/env python3
import sys
from pathlib import Path

kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()

# Nuclear approach: replace ALL variations of StatCard definition
# that could exist in the repo after multiple partial fixes.
# Use RowScope receiver - the correct fix since StatCard is always in a Row.

import re

# Replace any StatCard function signature variant with the correct RowScope version
content = re.sub(
    r'@Composable\s*\nfun (?:RowScope\.)?StatCard\(label: String, value: String(?:, modifier: [^)]+)?\) \{(\s*Card\(\s*modifier = (?:Modifier|modifier)[^\n]*\n(?:\s*\.[^\n]+\n)*)',
    lambda m: '@Composable\nfun RowScope.StatCard(label: String, value: String) {\n    Card(\n        modifier = Modifier\n            .weight(1f)\n            .padding(4.dp),\n',
    content
)

# Fix LargeStatCard icon type
content = re.sub(
    r'fun LargeStatCard\(label: String, value: String, icon: [^)]+\)',
    'fun LargeStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector)',
    content
)

# Remove NavigationBar modifier.align
content = content.replace(
    'NavigationBar(\n        modifier = Modifier.align(Alignment.BottomCenter),\n        containerColor = DarkSurface\n    )',
    'NavigationBar(\n        containerColor = DarkSurface\n    )'
)

# Revert any broken StatCard call sites from previous scripts
content = content.replace(
    'StatCard("Speed", connectionSpeed, modifier = Modifier.weight(1f))',
    'StatCard("Speed", connectionSpeed)'
)
content = content.replace(
    'StatCard("Latency", connectionLatency, modifier = Modifier.weight(1f))',
    'StatCard("Latency", connectionLatency)'
)
content = content.replace(
    'StatCard("Data Used", dataUsed, modifier = Modifier.weight(1f))',
    'StatCard("Data Used", dataUsed)'
)

# Ensure NexusVpnService import exists (once)
if 'import com.nexusvpn.android.service.NexusVpnService' not in content:
    content = content.replace(
        'import androidx.lifecycle.lifecycleScope\n',
        'import androidx.lifecycle.lifecycleScope\nimport com.nexusvpn.android.service.NexusVpnService\n'
    )

# Ensure ImageVector import exists (once)
if 'import androidx.compose.ui.graphics.vector.ImageVector' not in content:
    content = content.replace(
        'import androidx.compose.material.icons.filled.*\n',
        'import androidx.compose.material.icons.filled.*\nimport androidx.compose.ui.graphics.vector.ImageVector\n'
    )

# Ensure OptIn exists (once)
if '@file:OptIn' not in content:
    content = content.replace(
        'package com.nexusvpn.android\n',
        '@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\npackage com.nexusvpn.android\n'
    )

kt_path.write_text(content)
print("Done")
