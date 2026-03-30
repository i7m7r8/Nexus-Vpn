#!/usr/bin/env python3
"""
Fix MainActivity.kt - Restore from EARLY clean commit, then minimal fixes
"""
import subprocess
from pathlib import Path

print("="*60)
print("🔧 MainActivity.kt - ULTIMATE CLEAN FIX")
print("="*60)

# Step 1: Find a clean commit before our fixes
print("\n📥 Finding clean commit...")
result = subprocess.run(
    ['git', 'log', '--oneline', '--all', '-20'],
    capture_output=True,
    text=True,
    cwd=Path.cwd()
)
print("Recent commits:")
print(result.stdout)

# Step 2: Restore MainActivity.kt from original (before our fixes)
# Use git show to get original file content
print("\n📥 Restoring original MainActivity.kt...")
result = subprocess.run(
    ['git', 'show', 'origin/main:android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt'],
    capture_output=True,
    text=True,
    cwd=Path.cwd()
)

if result.returncode == 0:
    original_content = result.stdout
    kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
    kt_path.write_text(original_content)
    print("  ✅ Restored from origin/main")
else:
    # Fallback: checkout from HEAD~5
    print("  ⚠️  Trying HEAD~5...")
    subprocess.run(
        ['git', 'checkout', 'HEAD~5', '--', 'android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt'],
        capture_output=True
    )

# Step 3: Read and apply ONLY essential fixes
kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()
lines = content.split('\n')
print("\n🔧 Applying minimal fixes...")
fixes = 0
new_lines = []

skip_next_line = False
for i, line in enumerate(lines):
    # Skip duplicate @file:OptIn
    if '@file:OptIn' in line and i > 0 and '@file:OptIn' in lines[i-1]:
        print(f"  ✅ Line {i+1}: Skipped duplicate @file:OptIn")
        fixes += 1
        continue
    
    # Skip duplicate ImageVector import
    if 'import androidx.compose.ui.graphics.vector.ImageVector' in line:
        if i > 0 and 'ImageVector' in lines[i-1]:
            print(f"  ✅ Line {i+1}: Skipped duplicate ImageVector")
            fixes += 1
            continue
    
    # Skip duplicate NexusVpnService import
    if 'import com.nexusvpn.android.service.NexusVpnService' in line:
        if i > 0 and 'NexusVpnService' in lines[i-1]:
            print(f"  ✅ Line {i+1}: Skipped duplicate NexusVpnService")
            fixes += 1
            continue
    
    # Fix StatCard - replace entire signature
    if 'fun StatCard(label: String, value: String' in line and 'modifier' in line:
        new_lines.append('@Composable')
        new_lines.append('fun StatCard(label: String, value: String) {')
        print(f"  ✅ Line {i+1}: Fixed StatCard signature")
        fixes += 1
        continue
    
    # Fix LargeStatCard - replace entire signature
    if 'fun LargeStatCard(label: String, value: String, icon:' in line:
        new_lines.append('@Composable')
        new_lines.append('fun LargeStatCard(label: String, value: String, icon: ImageVector) {')
        print(f"  ✅ Line {i+1}: Fixed LargeStatCard signature")
        fixes += 1
        continue
    
    # Remove NavigationBar align modifier
    if 'modifier = Modifier.align(Alignment.BottomCenter)' in line:
        print(f"  ✅ Line {i+1}: Removed NavigationBar align")
        fixes += 1
        continue
    
    new_lines.append(line)
# Write fixed content
kt_path.write_text('\n'.join(new_lines))

print(f"\n✅ Applied {fixes} fixes")
print("\n" + "="*60)
print("🚀 COMMIT AND PUSH")
print("="*60)
