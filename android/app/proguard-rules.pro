# JNI native methods
-keepclasseswithmembernames class * { native <methods>; }
# Rust FFI
-keepclassmembers class com.nexusvpn.android.service.NexusVpnService { private static native *** *; }
# Serialization
-keep class com.nexusvpn.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

