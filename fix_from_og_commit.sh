#!/bin/bash
echo "========================================"
echo "Finding ORIGINAL clean MainActivity.kt"
echo "========================================"

# Find commits that touched MainActivity.kt
git log --oneline --all -- android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt | head -20

echo ""
echo "Restoring from 559a1ec (before Kotlin fixes started)..."
git checkout 559a1ec -- android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

echo "Done! Check the file now."
