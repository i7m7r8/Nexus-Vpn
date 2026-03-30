#!/usr/bin/env python3
"""
Fix MainActivity.kt - Direct surgical fixes for specific line errors
"""
from pathlib import Path

kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
lines = kt_path.read_text().split('\n')

print("="*60)
print("🔧 FIXING MainActivity.kt - LINE BY LINE")
print("="*60)

fixes = 0
new_lines = []

i = 0
while i < len(lines):
    line = lines[i]
    
    # Fix 1: StatCard duplicate modifier (around line 748)
    if 'fun StatCard(label: String, value: String, modifier: Modifier = Modifier, modifier = Modifier.weight(1f))' in line:
        new_lines.append('@Composable\nfun StatCard(label: String, value: String, modifier: Modifier = Modifier.weight(1f)) {')
        fixes += 1
        print(f"  ✅ Line {i+1}: Fixed StatCard signature")
        i += 1
        continue
    
    # Fix 2: LargeStatCard malformed (around line 767-768)
    if 'fun LargeStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector))' in line:
        new_lines.append('@Composable\nfun LargeStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {')
        fixes += 1
        print(f"  ✅ Line {i+1}: Fixed LargeStatCard signature")
        i += 1
        continue
    
    # Fix 3: Remove NavigationBar align modifier
    if 'modifier = Modifier.align(Alignment.BottomCenter),' in line and 'NavigationBar' in lines[i-1]:
        i += 1  # Skip this line
        fixes += 1
        print(f"  ✅ Line {i+1}: Removed NavigationBar align")
        continue
    
    # Fix 4: Add ImageVector import if on material-icons line
    if 'import androidx.compose.material.icons.filled.*' in line:
        new_lines.append(line)
        new_lines.append('import androidx.compose.ui.graphics.vector.ImageVector')
        fixes += 1
        print(f"  ✅ Line {i+1}: Added ImageVector import")
        i += 1
        continue
    
    # Fix 5: Add NexusVpnService import after lifecycleScope
    if 'import androidx.lifecycle.lifecycleScope' in line:
        new_lines.append(line)
        new_lines.append('import com.nexusvpn.android.service.NexusVpnService')
        fixes += 1
        print(f"  ✅ Line {i+1}: Added NexusVpnService import")
        i += 1
        continue
    
    # Fix 6: Add @file:OptIn before package
    if line.strip() == 'package com.nexusvpn.android':
        new_lines.append('@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)')
        fixes += 1
        print(f"  ✅ Line {i+1}: Added @file:OptIn")
    
    new_lines.append(line)
    i += 1

# Write fixed content
kt_path.write_text('\n'.join(new_lines))

print(f"\n✅ Applied {fixes} fixes")
print("\n" + "="*60)
print("🚀 COMMIT AND PUSH")
print("="*60)
