# Keep JNI native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep NexusVpnService and its companion object
-keep class com.nexusvpn.android.service.NexusVpnService { *; }
-keep class com.nexusvpn.android.service.NexusVpnService$Companion { *; }

# Keep Prefs
-keep class com.nexusvpn.android.data.Prefs { *; }

# Keep NexusVpnApplication
-keep class com.nexusvpn.android.NexusVpnApplication { *; }

# Keep MainActivity
-keep class com.nexusvpn.android.MainActivity { *; }

# Keep BootReceiver
-keep class com.nexusvpn.android.receiver.BootReceiver { *; }

# Keep Rust native library references
-keep class com.nexusvpn.android.** { *; }
