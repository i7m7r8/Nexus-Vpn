#!/usr/bin/env python3
import subprocess
from pathlib import Path

print("="*60)
print("MainActivity.kt - COMPLETE StatCard/LargeStatCard Fix")
print("="*60)

# Restore from clean commit
print("\nRestoring from d153010...")
subprocess.run(
    ['git', 'checkout', 'd153010', '--', 
     'android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt'],
    capture_output=True
)
print("  Restored")

kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()

# Remove duplicates
lines = content.split('\n')
new_lines = []
seen_optin = False
seen_imagevector = False
seen_nexus = False

for line in lines:
    if '@file:OptIn' in line:
        if seen_optin:
            continue
        seen_optin = True
    if 'ImageVector' in line and 'import' in line:
        if seen_imagevector:
            continue
        seen_imagevector = True
    if 'NexusVpnService' in line and 'import' in line:
        if seen_nexus:
            continue
        seen_nexus = True
    new_lines.append(line)

content = '\n'.join(new_lines)
print("  Removed duplicate imports/annotations")

# Remove NavigationBar align
content = content.replace(
    'NavigationBar(\n        modifier = Modifier.align(Alignment.BottomCenter),',
    'NavigationBar('
)
print("  Removed NavigationBar align")

# Remove modifier from StatCard calls
content = content.replace(', modifier = Modifier.weight(1f)', '')
print("  Removed modifier from StatCard calls")

kt_path.write_text(content)
print("\nMainActivity.kt fixed")
