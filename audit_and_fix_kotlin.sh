#!/bin/bash
echo "========================================"
echo "KOTLIN CODE AUDIT + COMPLETE FIX"
echo "========================================"

# Restore from original v2 source
echo "Restoring from 3aecc82..."
git checkout 3aecc82 -- android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo "Applying ALL fixes..."

# Fix 1: Add NexusVpnService import (CRITICAL - lines 155, 164 errors)
echo "  1. Adding NexusVpnService import..."
sed -i '/import androidx.lifecycle.lifecycleScope/a import com.nexusvpn.android.service.NexusVpnService' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 2: Add ImageVector import
echo "  2. Adding ImageVector import..."
sed -i '/import androidx.compose.material.icons.filled.*/a import androidx.compose.ui.graphics.vector.ImageVector' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 3: Fix LargeStatCard icon type
echo "  3. Fixing LargeStatCard icon type..."
sed -i 's/icon: androidx.compose.material.icons.materialIcon/icon: ImageVector/' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 4: Fix StatCard - remove modifier parameter from SIGNATURE
echo "  4. Fixing StatCard signature..."
sed -i 's/fun StatCard(label: String, value: String, modifier: Modifier = Modifier.weight(1f))/fun StatCard(label: String, value: String)/' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 5: Remove modifier from StatCard CALLS
echo "  5. Removing modifier from StatCard calls..."
sed -i 's/, modifier = Modifier\.weight(1f)//g' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 6: Remove NavigationBar align
echo "  6. Removing NavigationBar align..."
sed -i '/modifier = Modifier\.align(Alignment\.BottomCenter)/d' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 7: Add OptIn for experimental material APIs
echo "  7. Adding @OptIn for experimental APIs..."
sed -i '1i @file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo ""
echo "Verifying fixes..."
echo ""
echo "=== Checking imports ==="
grep -n "import.*NexusVpnService" android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt
grep -n "import.*ImageVector" android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo ""
echo "=== Checking StatCard signature ==="
grep -n "fun StatCard" android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo ""
echo "=== Checking LargeStatCard signature ==="
grep -n "fun LargeStatCard" android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo ""
echo "=== Checking @file:OptIn ==="
head -3 android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo ""
echo "Done!"
