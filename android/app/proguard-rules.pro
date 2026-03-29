# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Rust FFI functions
-keepclassmembers class com.nexusvpn.android.service.NexusVpnService {
    private static native *** *;
}

# Keep serialization classes
-keep class com.nexusvpn.** { *; }-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep AndroidX Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
