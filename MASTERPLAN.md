# Nexus VPN — Master Plan

> **World's Most Secure, Powerful, Ultra Feature-Packed SNI + Tor VPN**  
> **Pure Rust Core • Modern Compose UI • GitHub Auto-Release**

---

## 📋 Table of Contents

1. [Vision & Goals](#vision--goals)
2. [Architecture Overview](#architecture-overview)
3. [Core Features](#core-features)
4. [Security Features](#security-features)
5. [Network Features](#network-features)
6. [UI/UX Features](#uiux-features)
7. [Advanced Features](#advanced-features)
8. [Android Permissions & Services](#android-permissions--services)
9. [Build & CI/CD](#build--cicd)
10. [Project Structure](#project-structure)
11. [Implementation Phases](#implementation-phases)
12. [Technical Stack](#technical-stack)

---

## 🎯 Vision & Goals

### Vision
Create the **world's most powerful SNI + Tor VPN** that combines:
- **SNI Spoofing** (like Invisible Pro) — Decoy SNI in TLS ClientHello
- **Tor Anonymity** — All traffic routed through Tor network
- **Zero Leaks** — Kill switch, DNS leak protection, IPv6 leak protection
- **Pure Rust Core** — Memory-safe, high-performance networking
- **Modern UI** — Elegant Proton VPN clone with Jetpack Compose

### Goals
- ✅ **Privacy First** — No logs, no tracking, no analytics
- ✅ **Maximum Security** — Rust memory safety + Tor encryption
- ✅ **SNI Customization** — User-configurable decoy SNI hostnames
- ✅ **Bridge Support** — obfs4, meek, snowflake for censorship circumvention
- ✅ **Auto Build** — GitHub Actions builds & releases on every push
- ✅ **Lightweight** — Optimized for low-end devices (2GB RAM)
- ✅ **Open Source** — Fully transparent, community-driven

---

## 🏗️ Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Android App (Kotlin + Jetpack Compose)                                  │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  UI Layer (Compose)                                              │   │
│  │  ┌─────────────┐ ┌──────────────┐ ┌──────────────────────────┐  │   │
│  │  │ Home Screen │ │ Settings     │ │ Connection Status        │  │   │
│  │  │ Connect Btn │ │ SNI Config   │ │ Speed Monitor            │  │   │
│  │  │ Quick Stats │ │ Bridge Config│ │ Relay Info               │  │   │
│  │  └─────────────┘ └──────────────┘ └──────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  VPN Service Layer                                               │   │
│  │  ┌──────────────────┐  ┌───────────────────────────────────┐    │   │
│  │  │ VpnService.Builder│  │ Foreground Service (Persistent)   │    │   │
│  │  │ • TUN Interface   │  │ • Notification                    │    │   │
│  │  │ • Routes          │  │ • Auto-restart                    │    │   │
│  │  │ • DNS             │  │ • Boot receiver                   │    │   │
│  │  └────────┬─────────┘  └───────────────────────────────────┘    │   │
│  └───────────┼──────────────────────────────────────────────────────┘   │
│              │                                                           │
│  ┌───────────▼──────────────────────────────────────────────────────┐   │
│  │  JNI Bridge (libnexus_vpn.so)                                    │   │
│  │  • init_vpn(tun_fd, config)                                      │   │
│  │  • stop_vpn()                                                    │   │
│  │  • update_sni(decoy_host)                                        │   │   │
│  └───────────┬──────────────────────────────────────────────────────┘   │
└──────────────┼──────────────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────────────┐
│  Rust Core (nexus-vpn)                                                  │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  TUN Reader Thread                                              │   │
│  │  • Read raw IP packets from TUN fd                              │   │
│  │  • Feed to smoltcp virtual stack                                │   │
│  └──────────┬──────────────────────────────────────────────────────┘   │
│             │                                                           │
│  ┌──────────▼──────────────────────────────────────────────────────┐   │
│  │  smoltcp Virtual Stack (10.8.0.2/24)                            │   │
│  │  • TCP Socket Management                                        │   │
│  │  • UDP Socket (DNS)                                             │   │
│  │  • IP Fragment Reassembly                                       │   │
│  └──────────┬──────────────────────────────────────────────────────┘   │
│             │                                                           │
│  ┌──────────▼──────────────────────────────────────────────────────┐   │
│  │  SNI Interceptor (THE CORE FEATURE)                             │   │
│  │  ┌──────────────────────────────────────────────────────────┐  │   │
│  │  │ 1. Detect TLS ClientHello (content_type=0x16, HS=0x01)  │  │   │
│  │  │ 2. Parse SNI extension → Extract real hostname           │  │   │
│  │  │ 3. Rewrite SNI → User-configured decoy host             │  │   │
│  │  │ 4. Store real hostname for Tor routing                  │  │   │
│  │  │ 5. Modified packet → App sees decoy SNI                 │  │   │
│  │  └──────────────────────────────────────────────────────────┘  │   │
│  └──────────┬──────────────────────────────────────────────────────┘   │
│             │                                                           │
│  ┌──────────▼──────────────────────────────────────────────────────┐   │
│  │  DNS Forwarder                                                  │   │
│  │  • Intercept DNS queries (UDP 53)                               │   │
│  │  • Forward through Tor to 1.1.1.1 / 9.9.9.9                    │   │
│  │  • Return responses to app                                      │   │
│  └──────────┬──────────────────────────────────────────────────────┘   │
│             │                                                           │
│  ┌──────────▼──────────────────────────────────────────────────────┐   │
│  │  Tor Connector (Arti Client)                                    │   │
│  │  ┌──────────────────────────────────────────────────────────┐  │   │
│  │  │ • Bootstrap to Tor network                               │  │   │
│  │  │ • Connect to real hostname (from SNI interceptor)        │  │   │
│  │  │ • Bridge support (obfs4/meek/snowflake)                  │  │   │
│  │  │ • Circuit management                                     │  │   │
│  │  │ • Stream isolation                                       │  │   │
│  │  └──────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
App Request
    ↓
TUN Interface (10.8.0.2/24)
    ↓
smoltcp Virtual Stack
    ↓
SNI Interceptor
    ├─ Parse TLS ClientHello
    ├─ Extract real hostname (e.g., secret-site.onion)
    ├─ Rewrite SNI → decoy.example.com
    └─ Store mapping: socket → real_host
    ↓
Tor Connection
    ├─ Connect to real_host:port through Tor
    └─ Stream data bidirectionally
    ↓
Tor Network → Exit Node → Destination
```

---

## 🔥 Core Features

### 1. SNI Spoofing (Invisible Pro Style)
- **What**: User configures a decoy SNI hostname
- **How**: All TLS ClientHello packets have their SNI field rewritten to the decoy
- **Why**: Network observers see only the decoy hostname, not the real destination
- **Customization**: 
  - Preset decoy hosts (cloudflare.com, google.com, etc.)
  - Custom user-defined decoy hosts
  - Per-profile SNI settings

### 2. Tor Integration
- **Pure Rust**: Using `arti-client` (official Tor implementation in Rust)
- **Full Tor Support**: Complete Tor protocol support
- **Circuit Management**: Automatic circuit rotation
- **Stream Isolation**: Separate circuits for different connections

### 3. Bridge/Pluggable Transports
- **obfs4**: Obfuscation protocol 4
- **meek**: Domain fronting via CDNs
- **snowflake**: WebRTC-based bridges
- **Custom bridges**: User-provided bridge addresses

### 4. Kill Switch
- **System-level**: Android VpnService blocking
- **DNS Leak Protection**: All DNS through Tor
- **IPv6 Leak Protection**: IPv6 disabled in TUN
- **Auto-reconnect**: Service restarts on failure

### 5. DNS Over Tor
- **No Leaks**: All DNS queries through Tor
- **Resolvers**: Configurable (1.1.1.1, 9.9.9.9, custom)
- **DNSSEC**: Optional DNSSEC validation

---

## 🛡️ Security Features

### Memory Safety
- ✅ **100% Rust Core**: No C/C++ memory vulnerabilities
- ✅ **Bounds Checking**: All packet parsing validated
- ✅ **Zero Unsafe**: Minimal unsafe blocks (JNI only)

### Network Security
- ✅ **Tor Encryption**: Multi-layer Tor encryption
- ✅ **SNI Spoofing**: Decoy SNI in TLS handshakes
- ✅ **DNS Over Tor**: No DNS leaks
- ✅ **Kill Switch**: Blocks all traffic if VPN drops
- ✅ **No Logs**: Zero logging of user activity
- ✅ **No Analytics**: No tracking, no telemetry

### App Security
- ✅ **Foreground Service**: Persistent notification prevents Android killing
- ✅ **Boot Protection**: Auto-start on device boot
- ✅ **Reconnection**: Automatic reconnection on network changes
- ✅ **Certificate Pinning**: For Tor bootstrap (optional)

---

## 🌐 Network Features

### TUN Interface
- ✅ **Virtual Network**: 10.8.0.2/24 subnet
- ✅ **MTU Control**: Configurable MTU (default 1500)
- ✅ **Routing**: All traffic routed through TUN
- ✅ **Split Tunneling**: Per-app routing (future)

### Protocol Support
- ✅ **IPv4**: Full IPv4 support
- ✅ **TCP**: Full TCP support via smoltcp
- ✅ **UDP**: DNS over UDP (future: full UDP)
- ⚠️ **IPv6**: Planned (currently disabled)

### Performance
- ✅ **Async I/O**: Tokio-based async networking
- ✅ **Zero-copy**: Minimal data copying
- ✅ **Buffer Pooling**: Reusable buffers
- ✅ **Low RAM**: Optimized for 2GB devices

---

## 🎨 UI/UX Features

### Design Philosophy
- **Proton VPN Clone**: Modern, clean, intuitive
- **Material You**: Dynamic theming from wallpaper
- **Dark Mode**: AMOLED-friendly dark theme
- **Animations**: Smooth transitions & micro-interactions

### Screens

#### 1. Home Screen
```
┌────────────────────────────────────┐
│  ≡  Nexus VPN              ⚙️     │
├────────────────────────────────────┤
│                                    │
│         🔒                         │
│     DISCONNECTED                   │
│                                    │
│   ┌──────────────────────────┐    │
│   │                          │    │
│   │      CONNECT             │    │
│   │      [=====]             │    │
│   │                          │    │
│   └──────────────────────────┘    │
│                                    │
│  Quick Stats:                      │
│  ┌──────────┐  ┌──────────┐      │
│  │  ↑ 0 B/s │  │  ↓ 0 B/s │      │
│  └──────────┘  └──────────┘      │
│                                    │
│  Status: Ready to connect          │
│  SNI: decoy.example.com           │
│  Tor: Not connected                │
│                                    │
└────────────────────────────────────┘
```

#### 2. Connection Screen (Connected)
```
┌────────────────────────────────────┐
│  ≡  Nexus VPN              ⚙️     │
├────────────────────────────────────┤
│                                    │
│         🟢                         │
│       CONNECTED                    │
│     00:15:32                       │
│                                    │
│   ┌──────────────────────────┐    │
│   │       DISCONNECT         │    │
│   │                          │    │
│   └──────────────────────────┘    │
│                                    │
│  Live Speed:                       │
│  ┌──────────┐  ┌──────────┐      │
│  │  ↑ 2.3 MB│  │  ↓ 8.7 MB│      │
│  └──────────┘  └──────────┘      │
│                                    │
│  Connection Details:               │
│  ├─ SNI: decoy.example.com        │
│  ├─ Tor: Connected (3 hops)        │
│  ├─ Exit: Germany                  │
│  ├─ IP: 185.220.xx.xx             │
│  └─ Circuit: New in 2m 15s         │
│                                    │
│  Speed Graph:                      │
│  ┌──────────────────────────┐    │
│  │  ▁▂▃▅▇▆▅▃▂▁             │    │
│  └──────────────────────────┘    │
│                                    │
└────────────────────────────────────┘
```

#### 3. Settings Screen
```
┌────────────────────────────────────┐
│  ←  Settings                       │
├────────────────────────────────────┤
│                                    │
│  SNI Configuration                 │
│  ├─ Decoy SNI Hostname             │
│  │  ┌──────────────────────────┐  │
│  │  │ decoy.example.com        │  │
│  │  └──────────────────────────┘  │
│  ├─ Quick Presets                  │
│  │  ○ cloudflare.com              │
│  │  ○ google.com                  │
│  │  ○ microsoft.com               │
│  │  ○ Custom...                   │
│  └─────────────────────────────────│
│                                    │
│  Bridge Configuration              │
│  ├─ Use Bridges                    │
│  │  ○ None                        │
│  │  ○ obfs4                       │
│  │  ○ meek                        │
│  │  ○ snowflake                   │
│  ├─ Bridge Addresses               │
│  │  ┌──────────────────────────┐  │
│  │  │ obfs4 1.2.3.4:1234 ...   │  │
│  │  └──────────────────────────┘  │
│  └─────────────────────────────────│
│                                    │
│  DNS Configuration                 │
│  ├─ DNS Resolver                   │
│  │  ○ Cloudflare (1.1.1.1)        │
│  │  ○ Quad9 (9.9.9.9)             │
│  │  ○ Custom                      │
│  └─────────────────────────────────│
│                                    │
│  App Behavior                      │
│  ├─ Auto-connect on boot           │
│  ├─ Reconnect on network change    │
│  ├─ Kill switch                    │
│  ├─ Start minimized                │
│  └─────────────────────────────────│
│                                    │
│  Advanced                          │
│  ├─ Tor circuit settings           │
│  ├─ MTU size                       │
│  ├─ Log export                     │
│  └─────────────────────────────────│
│                                    │
│  About                             │
│  ├─ Version                        │
│  ├─ Licenses                       │
│  └─────────────────────────────────│
│                                    │
└────────────────────────────────────┘
```

### UI Components
- ✅ **Connection Button**: Large, prominent, animated
- ✅ **Speed Monitor**: Real-time upload/download
- ✅ **Connection Timer**: Duration counter
- ✅ **Status Indicators**: Color-coded (🔴🟡🟢)
- ✅ **Speed Graph**: Visual speed history
- ✅ **Toast Notifications**: Connection events
- ✅ **Snackbar Messages**: Error/info messages
- ✅ **Dialog Boxes**: Configuration dialogs

---

## 🚀 Advanced Features

### 1. Profiles
- **Multiple Configurations**: Save different SNI/bridge setups
- **Quick Switch**: One-tap profile switching
- **Auto-activate**: Profile based on network (WiFi vs Mobile)

### 2. Auto-start
- **Boot Receiver**: Start on device boot
- **Network Change**: Reconnect on network switch
- **App Crash Recovery**: Auto-restart on crash

### 3. Notifications
- **Persistent**: Foreground service notification
- **Quick Actions**: Disconnect from notification
- **Speed Display**: Live speed in notification
- **Minimal Mode**: Compact notification

### 4. Logging
- **Connection Log**: Timestamped events
- **Tor Log**: Tor network events
- **Export**: Share log files
- **Debug Mode**: Verbose logging option

### 5. Split Tunneling (Future)
- **Per-App Routing**: Select apps to bypass VPN
- **Whitelist Mode**: Only selected apps use VPN
- **Blacklist Mode**: Selected apps bypass VPN

### 6. Bandwidth Monitoring
- **Session Stats**: Data used in current session
- **Daily/Weekly/Monthly**: Historical usage
- **App-wise Breakdown**: Per-app usage (future)

### 7. Tor Circuit Info
- **Guard Node**: First hop info
- **Middle Node**: Second hop info
- **Exit Node**: Final hop info
- **Country Flags**: Node locations
- **New Circuit**: Manual circuit rotation

---

## 📱 Android Permissions & Services

### Required Permissions

#### Network Permissions
```xml
<!-- Full network access -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Network state monitoring -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- VPN Service -->
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />

<!-- Foreground service for VPN -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- Post notifications (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Boot completion for auto-start -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Wake lock for maintaining connection -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Foreground service type (Android 14+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

#### Optional Permissions
```xml
<!-- Query installed apps (for split tunneling) -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

<!-- Run on battery optimization whitelist -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### Foreground Service

#### Service Declaration
```xml
<service
    android:name=".NexusVpnService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="connectedDevice"
    android:permission="android.permission.BIND_VPN_SERVICE">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

#### Service Features
- ✅ **Persistent Notification**: Shows connection status
- ✅ **Auto-restart**: SERVICE_STICKY
- ✅ **Quick Actions**: Disconnect button in notification
- ✅ **Speed Display**: Live upload/download speeds
- ✅ **Connection Timer**: Session duration
- ✅ **Low Priority**: Minimal impact on battery

### Broadcast Receivers

#### Boot Receiver
```xml
<receiver
    android:name=".BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

#### Network Change Receiver
```kotlin
// Dynamically registered
IntentFilter().apply {
    addAction(ConnectivityManager.CONNECTIVITY_ACTION)
    addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
}
```

---

## 🔨 Build & CI/CD

### GitHub Actions Workflow

#### Triggers
- ✅ **Every Push**: Auto-build on `main` branch
- ✅ **Pull Requests**: Build verification
- ✅ **Manual Trigger**: `workflow_dispatch`
- ✅ **Tags**: Release builds on version tags

#### Build Matrix
```yaml
Architectures:
  - arm64-v8a (primary)
  - x86_64 (emulators)
  - armeabi-v7a (legacy, optional)
  - x86 (legacy, optional)

Build Types:
  - debug (testing)
  - release (production)
```

#### Build Steps
1. **Checkout**: Repository code
2. **Setup JDK 17**: Android compilation
3. **Setup Rust**: Native library build
4. **Setup NDK**: Rust → Android cross-compile
5. **Download Tor Bundles**: GeoIP data
6. **Build Rust Core**: cargo-ndk for each arch
7. **Build APKs**: ./gradlew assembleDebug/Release
8. **Upload Artifacts**: Store APKs
9. **Auto Release**: Create GitHub release

#### Auto Release
- **Tag Format**: `vYYYYMMDD.HHMMSS`
- **Release Notes**: Auto-generated from commits
- **Assets**: All APK variants
- **Latest Flag**: Mark as latest release

### Build Optimization for Low RAM
- ✅ **CI Runs on GitHub**: No local builds on device
- ✅ **Cached Dependencies**: Gradle & Cargo caches
- ✅ **Parallel Builds**: Multi-arch simultaneous
- ✅ **Incremental Builds**: Only changed modules
- ✅ **Memory Limits**: CI runner memory management

---

## 📁 Project Structure

```
Nexus-Vpn/
├── .github/
│   └── workflows/
│       └── build.yml                 # Auto build & release
│
├── android/                          # Android app module
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml   # Permissions, services
│   │   │   ├── java/com/nexusvpn/
│   │   │   │   ├── MainActivity.kt   # Entry point
│   │   │   │   ├── NexusVpnService.kt # VPN service
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/        # Compose theming
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   └── SettingsScreen.kt
│   │   │   │   ├── viewmodel/
│   │   │   │   │   └── VpnViewModel.kt
│   │   │   │   ├── receiver/
│   │   │   │   │   └── BootReceiver.kt
│   │   │   │   └── preferences/
│   │   │   │       └── AppPreferences.kt
│   │   │   ├── res/
│   │   │   │   ├── drawable/         # Icons, images
│   │   │   │   └── values/           # Strings, colors
│   │   │   └── assets/tor/           # Tor geoip files
│   │   └── build.gradle.kts          # App build config
│   ├── build.gradle.kts              # Project build config
│   ├── settings.gradle.kts           # Gradle settings
│   └── gradle.properties             # Gradle properties
│
├── rust-core/                        # Pure Rust VPN core
│   ├── src/
│   │   ├── lib.rs                    # JNI entry, main loop
│   │   ├── tun.rs                    # TUN interface
│   │   ├── sni/
│   │   │   ├── mod.rs                # SNI module
│   │   │   ├── parser.rs             # TLS ClientHello parser
│   │   │   └── rewriter.rs           # SNI rewriter
│   │   ├── tor.rs                    # Tor connection (Arti)
│   │   ├── dns.rs                    # DNS forwarder
│   │   ├── bridge.rs                 # smoltcp ↔ Tor bridge
│   │   └── dual_log.rs               # Logging utility
│   ├── Cargo.toml                    # Rust dependencies
│   └── Cargo.lock                    # Locked versions
│
├── MASTERPLAN.md                     # This file
├── README.md                         # Project overview
└── .gitignore                        # Git ignore rules
```

---

## 🚧 Implementation Phases

### Phase 1: Fix Build & Foundation ✅
- [x] Fix Gradle repository conflict
- [ ] Verify CI build passes
- [ ] Add all Android permissions
- [ ] Setup foreground service
- [ ] Implement boot receiver

**Status**: In Progress  
**ETA**: Next commits

---

### Phase 2: Core SNI Functionality 🔨
- [ ] Complete SNI parser (handle all TLS versions)
- [ ] Implement SNI injector (for ClientHello without SNI)
- [ ] Test SNI rewriting with real servers
- [ ] Add SNI validation & error handling

**Status**: Not Started  
**Priority**: Critical

---

### Phase 3: Tor Integration 🌐
- [ ] Integrate arti-client for Tor connections
- [ ] Implement bridge support (obfs4/meek/snowflake)
- [ ] Add circuit management
- [ ] Implement stream isolation

**Status**: Not Started  
**Priority**: High

---

### Phase 4: DNS & Leak Protection 🛡️
- [ ] Complete DNS forwarder through Tor
- [ ] Implement IPv6 leak protection
- [ ] Add DNS leak tests
- [ ] Implement comprehensive kill switch

**Status**: Not Started  
**Priority**: High

---

### Phase 5: UI Polish 🎨
- [ ] Implement all screens (Home, Settings, Status)
- [ ] Add Material You theming
- [ ] Implement animations & transitions
- [ ] Add speed graphs & charts
- [ ] Create notification UI

**Status**: Not Started  
**Priority**: Medium

---

### Phase 6: Advanced Features 🚀
- [ ] User profiles system
- [ ] Split tunneling
- [ ] Bandwidth monitoring
- [ ] Auto-reconnect logic
- [ ] Log export
- [ ] Debug mode

**Status**: Not Started  
**Priority**: Medium

---

### Phase 7: Testing & Optimization 🧪
- [ ] Unit tests for Rust core
- [ ] Integration tests
- [ ] Memory optimization
- [ ] Battery optimization
- [ ] Performance benchmarks

**Status**: Not Started  
**Priority**: Medium

---

### Phase 8: Release & Documentation 📚
- [ ] User documentation
- [ ] Setup guides
- [ ] FAQ
- [ ] Troubleshooting guide
- [ ] Contributor guidelines

**Status**: Not Started  
**Priority**: Low

---

## 🛠️ Technical Stack

### Android Layer
- **Language**: Kotlin 2.1.0
- **UI**: Jetpack Compose
- **Architecture**: MVVM (ViewModel + StateFlow)
- **Navigation**: Compose Navigation
- **Preferences**: DataStore
- **Build**: Gradle 8.10, AGP 8.7.3

### Rust Core
- **Language**: Rust 2021 Edition
- **Async Runtime**: Tokio
- **Networking**: smoltcp 0.12
- **Tor Client**: arti-client (pure Rust)
- **FFI**: jni crate 0.21
- **Logging**: log + dual_log
- **Error Handling**: thiserror + anyhow

### CI/CD
- **Platform**: GitHub Actions
- **Runner**: ubuntu-24.04
- **Build Tools**: cargo-ndk, Gradle wrapper
- **Artifact Storage**: GitHub Artifacts
- **Release**: GitHub Releases

### Dependencies

#### Rust Dependencies
```toml
smoltcp = "0.12"        # Virtual TCP/IP stack
tokio = "1.42"          # Async runtime
arti-client = "*"       # Tor client (pure Rust)
jni = "0.21"            # Android JNI bindings
log = "0.4"             # Logging
parking_lot = "0.12"    # Thread-safe data structures
serde = "1.0"           # Serialization
serde_json = "1.0"      # JSON parsing
```

#### Android Dependencies
```kotlin
// No major external dependencies!
// Pure Compose + Material3
// Minimal external libraries for small APK size
```

---

## 📊 APK Size Targets

| Component | Size (arm64) | Notes |
|-----------|--------------|-------|
| Rust Core | ~8-12 MB | Compiled libnexus_vpn.so |
| Android App | ~3-5 MB | Compose + resources |
| Tor GeoIP | ~25 MB | Optional, can be excluded |
| **Total** | **~15-20 MB** | Without GeoIP: ~12 MB |

---

## 🎯 Success Metrics

### Performance
- ✅ Connection time: < 10 seconds
- ✅ RAM usage: < 200 MB
- ✅ Battery drain: < 5% per hour
- ✅ APK size: < 25 MB

### Security
- ✅ Zero memory leaks (Rust guarantee)
- ✅ Zero DNS leaks
- ✅ Zero IP leaks
- ✅ Perfect SNI spoofing

### User Experience
- ✅ One-tap connect
- ✅ Clear status indicators
- ✅ Easy SNI configuration
- ✅ Reliable reconnection

---

## 🤝 Contributing

### For Developers
1. Fork the repository
2. Create a feature branch
3. Make changes
4. Submit a PR
5. GitHub Actions will auto-build

### Build Locally (Not Recommended for 2GB RAM)
```bash
# Only if you have 8GB+ RAM
git clone https://github.com/yourusername/Nexus-Vpn.git
cd Nexus-Vpn
./build.sh  # Wrapper for CI workflow
```

---

## 📝 License

**GPL-3.0** — Free software, freedom for users!

---

## 🙏 Acknowledgments

- **Invisible Pro** — Inspiration for SNI + Tor concept
- **Proton VPN** — UI/UX design inspiration
- **Arti Project** — Pure Rust Tor implementation
- **Tor Project** — The onion router itself
- **smoltcp** — Excellent Rust TCP/IP stack
- **Guardian Project** — Android privacy tools

---

## 📞 Support

- **Issues**: GitHub Issues
- **Discussions**: GitHub Discussions
- **Email**: (future)
- **Wiki**: (future)

---

**Last Updated**: April 4, 2026  
**Version**: 0.50.0  
**Status**: Active Development

---

> *"The right to privacy is the right to exist without being watched."*
