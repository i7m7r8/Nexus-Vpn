# ⚡ MASTERPLAN V2: NEXUS VPN - World's Most Secure SNI+Tor Rust VPN

**Vision**: Ultra-fast, zero-log, Proton-VPN-inspired SNI-customizable Tor VPN written 100% in pure Rust for Android, with military-grade encryption and invizible-pro-like chaining (SNI → Tor → Exit).

**Core Stack**: Rust 100% + Kotlin JNI glue. Tokio async runtime. Rustls TLS 1.3. ChaCha20-Poly1305 + AES-256-GCM dual-cipher. Arti pure-Rust Tor client. Encrypted SQLite storage (ChaCha20-Poly1305). Custom low-level socket handling + iptables Android integration.

**Architecture**: Three feature-packed 3000+ line Rust modules: (1) **VpnCore** — TUN/TAP interface, packet filtering, connection pooling, per-app routing, real-time stats, auto-reconnect, kill-switch. (2) **SniEngine** — TLS Client Hello injection, hostname randomization, multi-SNI server rotation, custom cipher suites, TLS 1.2/1.3 spoofing, fingerprint evasion, ESNI/ECH support. (3) **TorChain** — Pure Arti client, bridge auto-detect, guard/exit rotation, chained mode (SNI→Tor), failover, bandwidth optimization. Mode: SNI-only, Tor-only, or Tor-over-SNI (invizible-pro style).

**Android UI**: Proton VPN Material Design 3 clone (already done) with added SNI field. Live connection status with lottie animations. Server picker with country flags, latency badges, load % real-time. SNI panel: custom hostname, randomization toggle, rotation interval, cipher selector. Tor panel: bridge toggle, guard/exit pickers, bandwidth monitor. Split tunneling: per-app inclusion/exclusion. Protocol selector: UDP/TCP/Tor. Settings: DNS (DoH/DoT/Tor), leak prevention (IPv6 block, WebRTC kill, DNS leak test), kill-switch toggle, battery profile. Stats dashboard: speed, bandwidth, IP geolocation, connection duration, packet loss, latency histogram. Dark/Light theme. Connection logs exportable as JSON/CSV.

**Android Manifest v2**: All VPN + Network + Foreground Service perms. New: `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` for background persistence. `QUERY_ALL_PACKAGES` for split tunneling. `POST_NOTIFICATIONS` for connection alerts. No `usesCleartextTraffic` (TLS-only). `allowBackup=false` for security.

**Security Specs**: ChaCha20-Poly1305 primary cipher (IETF variant). AES-256-GCM fallback. TLS 1.3 minimum, with 1.2 fallback for legacy servers. Perfect Forward Secrecy (ephemeral ECDH). HMAC-SHA256 integrity. Getrandom for CSPRNG. SNI randomization prevents server fingerprinting. DNS over Tor blocks ISP visibility. IPv6 leak prevention (disable IPv6 by iptables). WebRTC leak block (socket filter). Kill-switch: hard iptables rule, VPN traffic only until reconnect or manual toggle.

**Performance Targets**: Connection < 2s. Throughput overhead < 5% vs raw. Idle CPU < 2%. Memory < 80MB. Battery drain (VPN+Tor combined) < 15%/hr. TLS handshake < 500ms. Tor circuit build < 3s. Max 1000 concurrent connections pooled.

**Build & Release**: Cargo + NDK cross-compile arm64-v8a + x86_64. GitHub Actions auto-build on push: triggers `cargo ndk build --release`, signs APK, uploads to Releases tab with checksums. Build time ~10min. Output: `nexus-vpn-armv8a-release.apk` + `nexus-vpn-x64-release.apk`. F-Droid compatible. In-app delta updates via custom proto.

**Feature Set Complete**: (1) VPN core engine with real-time speed/stats. (2) SNI spoof + randomization + multi-server rotation. (3) Tor + bridge support + chaining. (4) Per-app VPN routing (split tunneling). (5) DNS over HTTPS/Tor. (6) Kill-switch with iptables. (7) Battery optimization profiles. (8) Connection persistence across reboots. (9) Network state monitoring + auto-reconnect. (10) Zero-log guaranteed. (11) Proton VPN UI clone. (12) Export logs/stats. (13) IPv6 leak prevention. (14) WebRTC leak block.

**Implementation Guarantee**: All existing code preserved. Only additions + security patches. Rust lib.rs expanded 3000+ lines with full-featured VpnCore, SniEngine, TorChain modules. Android Kotlin doubled with modern Material 3 UI, all screens, settings, real-time dashboards. Each module production-ready, tested, zero unsafe blocks unless justified. Build succeeds on Termux. APKs ready to install.

---

**Status**: ✅ Ready to implement | **Est. Code**: 8000+ Rust + 2000+ Kotlin = 10,000+ LOC | **Build Time**: 10 min | **Release**: Auto on push | **Security Audit**: Included
