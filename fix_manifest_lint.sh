#!/bin/bash
echo "========================================"
echo "FIXING AndroidManifest.xml Lint Error"
echo "========================================"

# Remove android:debuggable attribute
echo "Removing android:debuggable from AndroidManifest.xml..."
sed -i '/android:debuggable="false"/d' android/app/src/main/AndroidManifest.xml

# Remove package attribute (warning)
echo "Removing package attribute from AndroidManifest.xml..."
sed -i 's/ package="com.nexusvpn.android"//' android/app/src/main/AndroidManifest.xml

echo ""
echo "=== VERIFICATION ==="
echo ""
echo "First 10 lines of AndroidManifest.xml:"
head -10 android/app/src/main/AndroidManifest.xml

echo ""
echo "Done!"
