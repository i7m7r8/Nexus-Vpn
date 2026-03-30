#!/usr/bin/env python3
"""
Fix MainActivity.kt - Complete rewrite of broken Compose functions
"""
import re
from pathlib import Path

kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
content = kt_path.read_text()

print("="*60)
print("🔧 Fixing MainActivity.kt Compose functions...")
print("="*60)

# Fix 1: StatCard - complete rewrite with proper signature
statcard_pattern = r'@Composable\s*\nfun StatCard\(label: String, value: String[^\{]*\{[^\}]*\}'
statcard_match = re.search(statcard_pattern, content, re.DOTALL)

if statcard_match:
    new_statcard = '''@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .weight(1f)
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
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProtonLightPrimary)
        }
    }
}'''
    content = content.replace(statcard_match.group(0), new_statcard)
    print("  ✅ Fixed StatCard")

# Fix 2: LargeStatCard - complete rewrite
large_pattern = r'@Composable\s*\nfun LargeStatCard\(label: String, value: String, icon: [^\{]*'
large_match = re.search(large_pattern, content, re.DOTALL)

if large_match:
    large_start = large_match.start()
    # Initialize large_end before using    large_end = -1
    # Find end - look for next @Composable or function
    large_end = content.find('@Composable', large_start + 100)
    if large_end == -1:
        large_end = content.find('fun FeatureRow', large_start)
    if large_end == -1:
        large_end = len(content)
    
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
    content = content[:large_start] + new_large + content[large_end:]
    print("  ✅ Fixed LargeStatCard")

# Fix 3: Remove modifier from StatCard calls
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
print("  ✅ Fixed StatCard calls")
# Fix 4: Ensure ImageVector import
if 'import androidx.compose.ui.graphics.vector.ImageVector' not in content:
    content = content.replace(
        'import androidx.compose.material.icons.filled.*',
        'import androidx.compose.material.icons.filled.*\nimport androidx.compose.ui.graphics.vector.ImageVector'
    )
    print("  ✅ Added ImageVector import")

# Fix 5: Remove NavigationBar align modifier
old_nav = 'NavigationBar(\n        modifier = Modifier.align(Alignment.BottomCenter),\n        containerColor = DarkSurface\n    )'
new_nav = 'NavigationBar(\n        containerColor = DarkSurface\n    )'
content = content.replace(old_nav, new_nav)
print("  ✅ Fixed NavigationBar")

kt_path.write_text(content)
print("\n✅ MainActivity.kt fixed")
