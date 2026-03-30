#!/bin/bash
echo "========================================"
echo "FORCE CLEAN BUILD"
echo "========================================"

# Add a comment to force git change
echo "// Force rebuild $(date)" >> android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt

git add android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt
git commit -m "fix: MainActivity.kt force rebuild trigger"
git push origin main

echo "Done!"
