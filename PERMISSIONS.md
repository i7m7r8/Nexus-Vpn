# Nexus VPN - Android Permissions Documentation

This document explains every permission used by Nexus VPN, why it's needed, and whether it's required or optional.

---

## 🔴 Required Permissions (Core Functionality)

### 1. INTERNET
```xml
<uses-permission android:name="android.permission.INTERNET" />
```
- **Purpose**: Allows the app to open network sockets
- **Why Needed**: VPN must connect to Tor network and relay traffic
- **Protection Level**: dangerous
- **Grant Behavior**: Granted at install time
- **Impact if Denied**: App cannot function at all

### 2. BIND_VPN_SERVICE
```xml
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />
```
- **Purpose**: Allows binding to the VPN service
- **Why Needed**: Required for any app implementing VpnService
- **Protection Level**: signature|privileged
- **Grant Behavior**: Automatically granted (system permission)
- **Impact if Denied**: Cannot create VPN tunnel

### 3. FOREGROUND_SERVICE
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```
- **Purpose**: Allows starting foreground services
- **Why Needed**: VPN runs as a foreground service to stay alive
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: VPN will be killed by Android when app closes

---

## 🟡 Foreground Service Type Permissions (Android 14+)

### 4. FOREGROUND_SERVICE_CONNECTED_DEVICE
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```
- **Purpose**: Declares the foreground service type for VPN
- **Why Needed**: Required on Android 14+ for services that connect devices
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: Service cannot start on Android 14+

### 5. FOREGROUND_SERVICE_SPECIAL_USE
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```
- **Purpose**: Special use foreground service type
- **Why Needed**: Backup service type for VPN functionality
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: Fallback for connectedDevice type

---

## 🟢 Network Monitoring Permissions

### 6. ACCESS_NETWORK_STATE
```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```
- **Purpose**: Allows accessing network state information
- **Why Needed**: Detect network changes, trigger reconnection
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: Cannot auto-reconnect on network change

### 7. ACCESS_WIFI_STATE
```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```
- **Purpose**: Allows accessing WiFi state information
- **Why Needed**: Detect WiFi connectivity changes
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: Limited network state awareness

### 8. CHANGE_NETWORK_STATE
```xml
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
```
- **Purpose**: Allows changing network connectivity state
- **Why Needed**: May need to trigger network reconnection
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: Cannot trigger network state changes

---

## 🔵 Notification & User Interaction Permissions

### 9. POST_NOTIFICATIONS
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
- **Purpose**: Allows posting notifications (Android 13+)
- **Why Needed**: Show VPN status notification
- **Protection Level**: dangerous
- **Grant Behavior**: Runtime permission (Android 13+)
- **Impact if Denied**: No notification shown, service may be killed
- **User Action**: User must grant in Settings or notification prompt

---

## 🟣 Boot & Power Management Permissions

### 10. RECEIVE_BOOT_COMPLETED
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```
- **Purpose**: Receive boot completed broadcast
- **Why Needed**: Auto-start VPN on device boot
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: Cannot auto-start on boot
- **User Control**: Can be disabled in app settings

### 11. RECEIVE_QUICKBOOT_POWERON
```xml
<uses-permission android:name="android.permission.RECEIVE_QUICKBOOT_POWERON" />
```
- **Purpose**: Receive quick boot broadcast (some devices)
- **Why Needed**: Auto-start after quick reboot
- **Protection Level**: signature|privileged
- **Grant Behavior**: System permission (may not be granted)
- **Impact if Denied**: No impact on most devices
- **Note**: Only works on manufacturer-signed builds

### 12. WAKE_LOCK
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```
- **Purpose**: Prevent device from sleeping
- **Why Needed**: Maintain VPN connection during screen off
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: VPN may disconnect when screen is off
- **Battery Impact**: Minimal (used sparingly)

### 13. REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```
- **Purpose**: Request exemption from battery optimizations
- **Why Needed**: Prevent Android from killing VPN service
- **Protection Level**: signature|privileged
- **Grant Behavior**: Requires user consent via intent
- **Impact if Denied**: VPN may be killed by battery saver
- **User Action**: User must manually exempt app in settings

---

## ⚪ Optional Permissions (Future Features)

### 14. QUERY_ALL_PACKAGES
```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```
- **Purpose**: Query all installed packages
- **Why Needed**: Split tunneling (select apps to bypass VPN)
- **Protection Level**: normal
- **Grant Behavior**: Automatically granted
- **Impact if Denied**: Split tunneling cannot function
- **Status**: Currently unused, for future feature
- **Privacy Note**: No data leaves the device

---

## 📊 Permission Summary Table

| Permission | Required | Auto-Granted | Runtime Prompt | User Can Deny |
|------------|----------|--------------|----------------|---------------|
| INTERNET | ✅ Yes | ❌ No | ❌ No | ❌ No |
| BIND_VPN_SERVICE | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| FOREGROUND_SERVICE | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| FOREGROUND_SERVICE_CONNECTED_DEVICE | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| FOREGROUND_SERVICE_SPECIAL_USE | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| ACCESS_NETWORK_STATE | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| ACCESS_WIFI_STATE | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| CHANGE_NETWORK_STATE | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| POST_NOTIFICATIONS | ✅ Yes | ❌ No | ✅ Yes | ✅ Yes |
| RECEIVE_BOOT_COMPLETED | ⚙️ Config | ✅ Yes | ❌ No | ❌ No |
| RECEIVE_QUICKBOOT_POWERON | ⚙️ Config | ✅ System | ❌ No | ❌ No |
| WAKE_LOCK | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | ⚙️ Config | ❌ User | ✅ Intent | ✅ Yes |
| QUERY_ALL_PACKAGES | ❌ Optional | ✅ Yes | ❌ No | ❌ No |

---

## 🔐 Foreground Service Configuration

### VPN Service Declaration
```xml
<service
    android:name=".service.NexusVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="connectedDevice"
    android:exported="false"
    android:enabled="true"
    android:process=":vpn_service">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

**Key Attributes:**
- `android:permission="android.permission.BIND_VPN_SERVICE"`: Only system can bind
- `android:foregroundServiceType="connectedDevice"`: VPN device connection type
- `android:exported="false"`: Not accessible to other apps
- `android:enabled="true"`: Service is enabled by default
- `android:process=":vpn_service"`: Runs in separate process for stability

---

## 📡 Broadcast Recevers

### Boot Receiver
```xml
<receiver
    android:name=".receiver.BootReceiver"
    android:enabled="true"
    android:exported="true"
    android:directBootAware="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```

**Triggers:**
- `BOOT_COMPLETED`: Full device boot
- `QUICKBOOT_POWERON`: Quick reboot (some devices)
- `com.htc.intent.action.QUICKBOOT_POWERON`: HTC-specific quick boot
- `MY_PACKAGE_REPLACED`: App updated/updated

**Behavior:**
- Checks user preference for auto-connect
- Starts VPN service if enabled
- Does nothing if user disabled auto-start

---

## 🛡️ Network Security Configuration

Located at: `res/xml/network_security_config.xml`

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

**Purpose:**
- Allows cleartext traffic for Tor bootstrap
- Trusts system and user certificates
- Debug overrides for development

---

## 🔍 Permission Request Flow

### Install Time (Auto-Granted)
Most permissions are granted automatically at install:
- All network permissions
- All foreground service permissions
- Boot completed receiver
- Wake lock

### Runtime (User Must Grant)
Only one permission requires runtime approval:
1. **POST_NOTIFICATIONS** (Android 13+)
   - Requested on first app launch
   - Explained with rationale dialog
   - User can deny (VPN still works, but no notification)

### User Action Required
One permission requires explicit user action:
1. **Battery Optimization Exemption**
   - Triggered by user in settings
   - Opens system battery optimization screen
   - User must manually exempt app

---

## 📱 Android Version Compatibility

### Android 9+ (API 28+)
- All permissions work as documented
- Foreground service restrictions apply

### Android 10+ (API 29+)
- `FOREGROUND_SERVICE` permission required
- Service type declaration needed

### Android 12+ (API 31+)
- `POST_NOTIFICATIONS` not yet required
- Boot receiver may need user interaction

### Android 13+ (API 33+)
- `POST_NOTIFICATIONS` is runtime permission
- Must request notification permission explicitly

### Android 14+ (API 34+)
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` required
- Service type must match foreground service type

---

## 🚫 What We DON'T Request

Nexus VPN explicitly does NOT request:
- ❌ Location permissions
- ❌ Camera/Microphone permissions
- ❌ Storage permissions (except app-private)
- ❌ Contacts/Phone permissions
- ❌ SMS permissions
- ❌ Account permissions
- ❌ Analytics/Tracking permissions

---

## 📝 User Privacy Statement

**Nexus VPN requests only the minimum permissions necessary to function:**

1. Network permissions to connect to Tor
2. Foreground service permissions to stay alive
3. Notification permission to show status
4. Boot permission for auto-start (optional)

**We will never:**
- Sell or share your data
- Track your online activity
- Request unnecessary permissions
- Include analytics or tracking

**Your privacy is our priority.**

---

## 🔧 Developer Notes

### Checking Permission Status
```kotlin
// Check if permission is granted
val hasNotificationPermission = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.POST_NOTIFICATIONS
) == PackageManager.PERMISSION_GRANTED
```

### Requesting Runtime Permission
```kotlin
// Request notification permission
ActivityCompat.requestPermissions(
    activity,
    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
    REQUEST_CODE_NOTIFICATION
)
```

### Battery Optimization Exemption
```kotlin
// Request battery optimization exemption
if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    intent.data = Uri.parse("package:$packageName")
    startActivity(intent)
}
```

---

**Last Updated**: April 4, 2026  
**Document Version**: 1.0  
**Minimum Android Version**: API 26 (Android 8.0)  
**Target Android Version**: API 34 (Android 14)
