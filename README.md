# Nexus VPN

World's most powerful **SNI + Tor** VPN. Written in **Pure Rust** with **Arti v0.40.0**.

Proton VPN UI clone + SNI customization + Tor privacy.

## Features

- **SNI Evasion**: Transparent SNI rewriting bypasses DPI-based censorship
- **Tor Backend**: 100% Pure Rust Tor implementation (Arti 0.40.0)
- **Kill Switch**: Blocks all traffic if VPN disconnects
- **Proton VPN Clone UI**: Modern, elegant Jetpack Compose interface
- **Split Tunneling**: Per-app allow/block lists
- **Auto-Release**: CI builds ARM64 + x86_64 APKs on every push

## Architecture

```
Android UI → VpnService → TUN fd → Rust Core (Arti Tor) → Internet
              ↳ SNI Rewriter → Tor Circuit → Hidden Service/Exit Node
```

## Build

```bash
cd rust-core
cargo ndk -t aarch64-linux-android -t x86_64-linux-android build --release
cd ../android
./gradlew assembleRelease
```

## License

GPLv3
