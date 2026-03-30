#!/bin/bash
echo "========================================"
echo "MainActivity.kt - ORIGINAL + FIX"
echo "========================================"

# Restore from original v2 source
echo "Restoring from 3aecc82..."
git checkout 3aecc82 -- android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 1: Add ImageVector import after material-icons import
echo "Adding ImageVector import..."
sed -i '/import androidx.compose.material.icons.filled.*/a import androidx.compose.ui.graphics.vector.ImageVector' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 2: Fix LargeStatCard icon type
echo "Fixing LargeStatCard icon type..."
sed -i 's/icon: androidx.compose.material.icons.materialIcon/icon: ImageVector/' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 3: Remove modifier from StatCard calls
echo "Removing modifier from StatCard calls..."
sed -i 's/, modifier = Modifier\.weight(1f)//g' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

# Fix 4: Remove NavigationBar align
echo "Removing NavigationBar align..."
sed -i '/modifier = Modifier\.align(Alignment\.BottomCenter)/d' android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo "Done!"
