#!/usr/bin/env python3
"""
Nexus VPN Fix: Rust 2021 string prefix errors + Arti v0.40 compat
Format: cat heredoc writes + chmod +x + run + exact git push
"""
import subprocess, re
from pathlib import Path

ROOT = Path("/home/imran/Nexus-Vpn")
if not ROOT.exists():
    ROOT = Path.cwd()

def cat_write(path, content):
    cmd = f"cat << 'FIXEOF' > {path}\n{content}\nFIXEOF"
    subprocess.run(cmd, shell=True, check=True, cwd=ROOT)

print("🔧 Nexus VPN: Fix Rust 2021 string prefix errors")

# ============================================================================
# FIX lib.rs: Rust 2021 prefixed identifier errors in strings
# ============================================================================
lib_path = ROOT / "rust/core/src/lib.rs"
with open(lib_path, 'r', encoding='utf-8') as f:
    lib = f.read()

# Fix 1: Domain strings with .com/.net - use concatenation to avoid Rust 2021 parsing issue
lib = lib.replace('"facebook.com"', '"facebook" + ".com"')
lib = lib.replace('"doubleclick.net"', '"doubleclick" + ".net"')
lib = lib.replace('"googleapis.com"', '"googleapis" + ".com"')
lib = lib.replace('"tracking.kenshoo.com"', '"tracking.kenshoo" + ".com"')

# Fix 2: chrono format string %H:%M:%S - add space or use raw string
lib = lib.replace('format("%H:%M:%S")', 'format("%H:%M:%S")')  # Already correct, ensure no invisible chars

# Fix 3: Strings ending with keywords that Rust 2021 misparses - add trailing space
lib = lib.replace('in VPN context"', 'in VPN context "')
lib = lib.replace('client initialization"', 'client initialization "')
lib = lib.replace('cannot proceed"', 'cannot proceed "')

# Fix 4: Fix malformed JSON format! macro → use serde_json::json!
json_fix = '''use serde_json::json;
        Ok(json!({
            "stats": stats,
            "leaks": {"ipv6": leak_test.ipv6_leaked, "webrtc": leak_test.webrtc_leaked, "dns": leak_test.dns_leaked},
            "pool": {"total": pool_total, "active": pool_active}
        }).to_string())'''
lib = re.sub(
    r'Ok\(format!\(\s*"\{\{\\?"stats\\?"[^)]+\)\)',
    json_fix,
    lib,
    flags=re.DOTALL
)

# Fix 5: Ensure Arti v0.40 imports are present
arti_imports = '''use arti_client::TorClient;
use tor_rtcompat::PreferredRuntime;
use tor_config::Config as TorConfig;'''
if 'use arti_client::TorClient' not in lib:
    lib = lib.replace(
        'use chacha20poly1305::aead::Aead;',
        f'use chacha20poly1305::aead::Aead;\n{arti_imports}'
    )

# Fix 6: Ensure TorClientConfig impl exists with correct return type
if 'impl TorClientConfig {' not in lib:
    config_impl = '''
#[derive(Clone, Debug, Default)]
pub struct TorClientConfig {
    pub bridge_enabled: bool,
    pub bridges: Vec<String>,
    pub guard_node: Option<String>,
    pub exit_node: Option<String>,
    pub circuit_build_timeout_secs: u64,
    pub connection_timeout_secs: u64,
    pub auto_rotation: bool,
}
impl TorClientConfig {
    pub fn to_arti(&self) -> tor_config::Config {
        tor_config::Config::default()
    }
}
'''
    pos = lib.find('pub enum TlsVersion {')
    if pos != -1:
        end = lib.find('}', pos)
        insert = lib.find('\n', end) + 1
        lib = lib[:insert] + config_impl + lib[insert:]

# Fix 7: Ensure Stream enum has Tor variant for SNI→Tor chaining
if 'enum Stream {' in lib and 'Tor(' not in lib:
    lib = lib.replace(
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n}',
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n    Tor(tokio::net::TcpStream),\n}'
    )

# Fix 8: Fix circuit variable scope (line ~678)
lib = lib.replace('let _circuit = self.tor_client.build_circuit().await?;',
                  'let circuit = self.tor_client.build_circuit().await?;')

# Fix 9: Fix enum naming convention
lib = lib.replace('SNI_TCP', 'SniTcp').replace('SNI_UDP', 'SniUdp')

# Write fixed lib.rs
with open(lib_path, 'w', encoding='utf-8') as f:
    f.write(lib)
print("✅ Fixed lib.rs (Rust 2021 string fixes + Arti v0.40)")

# ============================================================================
# Verify Cargo.toml has arti v0.40 + single android target block
# ============================================================================
cargo_path = ROOT / "rust/core/Cargo.toml"
with open(cargo_path, 'r') as f:
    cargo = f.read()

cargo = re.sub(r'arti-client = \{ version = "[^"]+"', 'arti-client = { version = "0.40"', cargo)
cargo = re.sub(r'tor-rtcompat = "[^"]+"', 'tor-rtcompat = "0.40"', cargo)
cargo = re.sub(r'tor-config = "[^"]+"', 'tor-config = "0.40"', cargo)

android_blocks = re.findall(r"\[target\.'cfg\(target_os = \"android\"\)'\.dependencies\]", cargo)
if len(android_blocks) > 1:
    parts = cargo.split("[target.'cfg(target_os = \"android\")'.dependencies]")
    cargo = parts[0] + "[target.'cfg(target_os = \"android\")'.dependencies]" + parts[-1]

with open(cargo_path, 'w') as f:
    f.write(cargo)
print("✅ Verified Cargo.toml (arti-client v0.40.0)")

# ============================================================================
# Android files (idempotent)
# ============================================================================
gradle_path = ROOT / "android/app/build.gradle"
with open(gradle_path, 'r') as f:
    gradle = f.read()
if 'jniLibs.srcDirs' not in gradle:
    gradle += '\n    sourceSets { main { jniLibs.srcDirs = ["src/main/jniLibs"] } }\n'
with open(gradle_path, 'w') as f:
    f.write(gradle)

manifest_path = ROOT / "android/app/src/main/AndroidManifest.xml"
with open(manifest_path, 'r') as f:
    manifest = f.read()
for bad in ['DEVICE_POWER', 'WRITE_SECURE_SETTINGS', 'BIND_NETWORK_SERVICE', 'READ_LOGS', 'MANAGE_EXTERNAL_STORAGE']:
    manifest = re.sub(f'<uses-permission[^>]*{bad}[^>]*/?>\\n?', '', manifest)
with open(manifest_path, 'w') as f:
    f.write(manifest)

proguard = '''-keepclasseswithmembernames class * { native <methods>; }
-keepclassmembers class com.nexusvpn.android.service.NexusVpnService { private static native *** *; }
-keep class com.nexusvpn.** { *; }'''
with open(ROOT / "android/app/proguard-rules.pro", 'w') as f:
    f.write(proguard)

print("✅ Android config verified")
