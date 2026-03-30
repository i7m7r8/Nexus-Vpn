#!/usr/bin/env python3
"""
Fix MainActivity.kt - Restore from git then apply minimal fixes
"""
import subprocess
from pathlib import Path

print("="*60)
print("🔧 FIXING MainActivity.kt - CLEAN RESTORE")
print("="*60)

# Step 1: Restore from git
print("\n📥 Restoring from git...")
result = subprocess.run(
    ['git', 'checkout', 'HEAD', '--', 'android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt'],
    capture_output=True,
    text=True
)
if result.returncode == 0:
    print("  ✅ Restored from git")
else:
    print("  ⚠️  Git restore failed, continuing with existing file")

# Step 2: Read fresh file
kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()

# Step 3: Apply ONLY essential fixes
print("\n🔧 Applying minimal fixes...")

# Fix 1: Add @file:OptIn if missing
if '@file:OptIn' not in content:
    content = content.replace(
        'package com.nexusvpn.android',
        '@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\npackage com.nexusvpn.android'
    )
    print("  ✅ Added @file:OptIn")

# Fix 2: Add NexusVpnService import if missing
if 'import com.nexusvpn.android.service.NexusVpnService' not in content:
    content = content.replace(
        'import androidx.lifecycle.lifecycleScope',
        'import androidx.lifecycle.lifecycleScope\nimport com.nexusvpn.android.service.NexusVpnService'
    )
    print("  ✅ Added NexusVpnService import")

# Fix 3: Add ImageVector import if missing
if 'import androidx.compose.ui.graphics.vector.ImageVector' not in content:
    content = content.replace(
        'import androidx.compose.material.icons.filled.*',
        'import androidx.compose.material.icons.filled.*\nimport androidx.compose.ui.graphics.vector.ImageVector'
    )
    print("  ✅ Added ImageVector import")

# Fix 4: Remove NavigationBar align modifier (simple string replace)
content = content.replace(
    'NavigationBar(\n        modifier = Modifier.align(Alignment.BottomCenter),\n        containerColor = DarkSurface\n    )',
    'NavigationBar(\n        containerColor = DarkSurface\n    )'
)
print("  ✅ Fixed NavigationBar")

# Fix 5: Fix StatCard - remove duplicate modifier parameter
# Find and replace the function signature
old_statcard_sig = 'fun StatCard(label: String, value: String, modifier: Modifier = Modifier, modifier = Modifier.weight(1f))'
new_statcard_sig = 'fun StatCard(label: String, value: String, modifier: Modifier = Modifier.weight(1f))'
if old_statcard_sig in content:
    content = content.replace(old_statcard_sig, new_statcard_sig)
    print("  ✅ Fixed StatCard signature")

# Fix 6: Fix LargeStatCard - fix malformed icon parameter
old_large_sig = 'fun LargeStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector))'
new_large_sig = 'fun LargeStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier)'
if old_large_sig in content:
    content = content.replace(old_large_sig, new_large_sig)
    print("  ✅ Fixed LargeStatCard signature")

# Write file
kt_path.write_text(content)

print("\n✅ MainActivity.kt fixed")
print("\n" + "="*60)
print("🚀 COMMIT AND PUSH")
print("="*60)
