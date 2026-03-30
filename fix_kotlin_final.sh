#!/bin/bash
echo "========================================"
echo "MAINACTIVITY.KT - FINAL COMPLETE FIX"
echo "========================================"

# Restore from original v2 source
echo "Restoring from 3aecc82..."
git checkout 3aecc82 -- android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo "Applying ALL fixes..."

# 1. Add @file:OptIn at top
sed -i '1i @file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# 2. Add NexusVpnService import
sed -i '/import androidx.lifecycle.lifecycleScope/a import com.nexusvpn.android.service.NexusVpnService' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# 3. Add ImageVector import
sed -i '/import androidx.compose.material.icons.filled.*/a import androidx.compose.ui.graphics.vector.ImageVector' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# 4. Fix LargeStatCard icon type
sed -i 's/icon: androidx.compose.material.icons.materialIcon/icon: ImageVector/' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# 5. Fix StatCard function - remove modifier from signature
sed -i 's/fun StatCard(label: String, value: String, modifier: Modifier = Modifier.weight(1f))/fun StatCard(label: String, value: String)/' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# 6. Remove .weight(1f) from INSIDE StatCard Card modifier
sed -i '/^@Composable$/,/^}/ { /^fun StatCard/,/^}/ { s/\.weight(1f)//g } }' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# 7. Remove modifier from StatCard CALL sites
sed -i 's/, modifier = Modifier\.weight(1f)//g' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# 8. Remove NavigationBar align
sed -i '/modifier = Modifier\.align(Alignment\.BottomCenter)/d' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo ""
echo "=== VERIFICATION ==="
echo ""
echo "StatCard signature:"
grep -n "fun StatCard" android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt
echo ""
echo "LargeStatCard signature:"
grep -n "fun LargeStatCard" android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt
echo ""
echo "Imports:"
grep -n "import.*NexusVpnService\|import.*ImageVector" android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt
echo ""
echo "@file:OptIn:"
head -1 android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt
echo ""
echo "Done!"
