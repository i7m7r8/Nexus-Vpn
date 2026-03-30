#!/usr/bin/env python3
"""
Fix MainActivity.kt - HARD RESET from git then apply SINGLE clean fix
"""
import subprocess
from pathlib import Path

print("="*60)
print("🔧 MainActivity.kt - HARD RESET + CLEAN FIX")
print("="*60)

# Step 1: Hard reset from git
print("\n📥 Hard resetting from git...")
subprocess.run(
    ['git', 'checkout', 'HEAD', '--', 'android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt'],
    capture_output=True
)
print("  ✅ Reset complete")

# Step 2: Read fresh file
kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()

print("\n🔧 Applying SINGLE clean fix block...")

# Replace entire StatCard function
old_statcard_start = content.find('@Composable\nfun StatCard(label: String, value: String')
if old_statcard_start != -1:
    # Find end of function (next @Composable or fun)
    old_statcard_end = content.find('@Composable', old_statcard_start + 50)
    if old_statcard_end == -1:
        old_statcard_end = content.find('fun LargeStatCard', old_statcard_start)
    
    new_statcard = '''@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier.weight(1f)) {
    Card(
        modifier = Modifier
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)        }
    }
}

'''
    content = content[:old_statcard_start] + new_statcard + content[old_statcard_end:]
    print("  ✅ Replaced StatCard function")

# Replace entire LargeStatCard function  
old_large_start = content.find('@Composable\nfun LargeStatCard(label: String, value: String')
if old_large_start != -1:
    # Find end (next fun or end of composable functions)
    old_large_end = content.find('@Composable', old_large_start + 50)
    if old_large_end == -1:
        old_large_end = content.find('fun FeatureRow', old_large_start)
    
    new_large = '''@Composable
fun LargeStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontSize = 12.sp, color = Color.Gray)
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)
            }
            Icon(icon, label, tint = ProtonAccent, modifier = Modifier.size(32.dp))
        }
    }
}

'''
    content = content[:old_large_start] + new_large + content[old_large_end:]
    print("  ✅ Replaced LargeStatCard function")

# Remove NavigationBar align modifier
content = content.replace(
    'NavigationBar(\n        modifier = Modifier.align(Alignment.BottomCenter),\n        containerColor = DarkSurface\n    )',
    'NavigationBar(\n        containerColor = DarkSurface\n    )'
)
print("  ✅ Fixed NavigationBar")
kt_path.write_text(content)
print("\n✅ MainActivity.kt fixed")
