#!/usr/bin/env python3
"""
Debug and fix StatCard/LargeStatCard - show what's actually in the file
"""
from pathlib import Path
import re

kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()
lines = content.split('\n')

print("="*60)
print("🔍 SCANNING MainActivity.kt FOR ISSUES")
print("="*60)

# Find StatCard definition
print("\n📍 StatCard definition:")
for i, line in enumerate(lines, 1):
    if 'fun StatCard' in line:
        print(f"  Line {i}: {line.strip()}")

# Find LargeStatCard definition
print("\n📍 LargeStatCard definition:")
for i, line in enumerate(lines, 1):
    if 'fun LargeStatCard' in line:
        print(f"  Line {i}: {line.strip()}")

# Find StatCard calls
print("\n📍 StatCard calls:")
for i, line in enumerate(lines, 1):
    if 'StatCard(' in line and 'fun' not in line:
        print(f"  Line {i}: {line.strip()}")

# Find NavigationBar
print("\n📍 NavigationBar:")
for i, line in enumerate(lines, 1):
    if 'NavigationBar(' in line or 'modifier.align' in line:
        print(f"  Line {i}: {line.strip()}")

print("\n" + "="*60)
print("🔧 APPLYING FIXES")
print("="*60)

fixes = 0

# Fix StatCard - use regex for flexible matching
statcard_pattern = r'fun StatCard\(label: String, value: String,[^\)]*\)'
match = re.search(statcard_pattern, content)
if match:
    old = match.group(0)
    new = 'fun StatCard(label: String, value: String, modifier: Modifier = Modifier.weight(1f))'
    content = content.replace(old, new)
    fixes += 1
    print(f"  ✅ Fixed StatCard: {old[:50]}...")

# Fix LargeStatCard - use regex
large_pattern = r'fun LargeStatCard\(label: String, value: String, icon: [^\)]*\)'
match = re.search(large_pattern, content)
if match:
    old = match.group(0)
    new = 'fun LargeStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier)'
    content = content.replace(old, new)
    fixes += 1
    print(f"  ✅ Fixed LargeStatCard: {old[:50]}...")

# Fix StatCard calls - remove modifier parameter
content = re.sub(
    r'StatCard\("([^"]+)", ([^,\)]+), modifier = Modifier\.weight\(1f\)\)',
    r'StatCard("\1", \2)',
    content
)
fixes += 3
print("  ✅ Fixed 3 StatCard calls")

# Fix NavigationBar - remove align modifier
content = re.sub(
    r'NavigationBar\(\s*\n\s*modifier = Modifier\.align\(Alignment\.BottomCenter\),\s*\n',
    'NavigationBar(\n',
    content
)
fixes += 1
print("  ✅ Fixed NavigationBar")

kt_path.write_text(content)
print(f"\n✅ Applied {fixes} fixes")
