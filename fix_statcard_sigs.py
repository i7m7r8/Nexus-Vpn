#!/usr/bin/env python3
"""
Fix StatCard and LargeStatCard function signatures directly
"""
from pathlib import Path

kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()

print("="*60)
print("🔧 FIXING StatCard/LargeStatCard SIGNATURES")
print("="*60)

fixes = 0

# Fix 1: StatCard - remove duplicate modifier parameter
old_statcard = 'fun StatCard(label: String, value: String, modifier: Modifier = Modifier, modifier = Modifier.weight(1f))'
new_statcard = 'fun StatCard(label: String, value: String, modifier: Modifier = Modifier.weight(1f))'
if old_statcard in content:
    content = content.replace(old_statcard, new_statcard)
    fixes += 1
    print("  ✅ Fixed StatCard duplicate modifier")

# Fix 2: LargeStatCard - fix malformed icon parameter  
old_large = 'fun LargeStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector)) {'
new_large = 'fun LargeStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {'
if old_large in content:
    content = content.replace(old_large, new_large)
    fixes += 1
    print("  ✅ Fixed LargeStatCard icon parameter")

# Fix 3: Remove modifier from StatCard call sites
replacements = [
    ('StatCard("Speed", connectionSpeed, modifier = Modifier.weight(1f))', 'StatCard("Speed", connectionSpeed)'),
    ('StatCard("Latency", connectionLatency, modifier = Modifier.weight(1f))', 'StatCard("Latency", connectionLatency)'),
    ('StatCard("Data Used", dataUsed, modifier = Modifier.weight(1f))', 'StatCard("Data Used", dataUsed)'),
]
for old, new in replacements:
    if old in content:
        content = content.replace(old, new)
        fixes += 1
        print(f"  ✅ Fixed StatCard call: {old[:40]}...")

kt_path.write_text(content)
print(f"\n✅ Applied {fixes} fixes")
