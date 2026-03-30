#!/usr/bin/env python3
import subprocess
from pathlib import Path

print("="*60)
print("MainActivity.kt - NUCLEAR FIX")
print("="*60)

print("\nRestoring from clean commit...")
subprocess.run(
    ['git', 'checkout', 'd153010', '--', 
     'android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt'],
    capture_output=True
)
print("  Restored from d153010")

kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()

print("\nApplying fixes...")

# Remove duplicate @file:OptIn lines
lines = content.split('\n')
new_lines = []
optin_count = 0
for line in lines:
    if '@file:OptIn' in line:
        optin_count += 1
        if optin_count > 1:
            continue
    new_lines.append(line)
content = '\n'.join(new_lines)
print("  Removed duplicate @file:OptIn")

# Remove duplicate ImageVector import
lines = content.split('\n')
new_lines = []
img_count = 0
for line in lines:
    if 'ImageVector' in line and 'import' in line:
        img_count += 1
        if img_count > 1:
            continue
    new_lines.append(line)
content = '\n'.join(new_lines)
print("  Removed duplicate ImageVector import")

# Remove duplicate NexusVpnService import
lines = content.split('\n')
new_lines = []
nexus_count = 0
for line in lines:
    if 'NexusVpnService' in line and 'import' in line:
        nexus_count += 1
        if nexus_count > 1:
            continue
    new_lines.append(line)
content = '\n'.join(new_lines)
print("  Removed duplicate NexusVpnService import")

# Remove NavigationBar align
content = content.replace(
    'NavigationBar(\n        modifier = Modifier.align(Alignment.BottomCenter),',
    'NavigationBar('
)
print("  Removed NavigationBar align")

kt_path.write_text(content)
print("\nMainActivity.kt fixed")
