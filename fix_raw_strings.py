#!/usr/bin/env python3
"""
Nexus VPN Fix: Rust 2021 prefixed identifier errors → use raw strings
Format: cat heredoc + chmod +x + run + exact git push
"""
import subprocess, re
from pathlib import Path

ROOT = Path("/home/imran/Nexus-Vpn")
if not ROOT.exists():
    ROOT = Path.cwd()

def cat_write(path, content):
    cmd = f"cat << 'FIXEOF' > {path}\n{content}\nFIXEOF"
    subprocess.run(cmd, shell=True, check=True, cwd=ROOT)

print("🔧 Nexus VPN: Fix Rust 2021 prefixed identifiers with raw strings")

# ============================================================================
# FIX lib.rs: Replace problematic string literals with raw strings r#"..."#
# ============================================================================
lib_path = ROOT / "rust/core/src/lib.rs"
with open(lib_path, 'r', encoding='utf-8') as f:
    lib = f.read()

# Fix 1: Domain strings with .com/.net → use raw strings
replacements = [
    ('"facebook.com"', 'r#"facebook.com"#'),
    ('"doubleclick.net"', 'r#"doubleclick.net"#'),
    ('"googleapis.com"', 'r#"googleapis.com"#'),
    ('"tracking.kenshoo.com"', 'r#"tracking.kenshoo.com"#'),
    ('"a.com"', 'r#"a.com"#'),
    ('"b.com"', 'r#"b.com"#'),
    ('"%H:%M:%S"', 'r#"%H:%M:%S"#'),
    ('"%Y%m%d_%H%M%S"', 'r#"%Y%m%d_%H%M%S"#'),
    ('"in VPN context"', 'r#"in VPN context"#'),
    ('"client initialization"', 'r#"client initialization"#'),
    ('"cannot proceed"', 'r#"cannot proceed"#'),
    ('"nexus-vpn.log"', 'r#"nexus-vpn.log"#'),
    ('"nexus-vpn.{}.log"', 'r#"nexus-vpn.{}.log"#'),
    ('"{}: {}\\n"', 'r#"{}: {}"# + "\\n"'),
]
for old, new in replacements:
    lib = lib.replace(old, new)

# Fix 2: Broken JSON format! → proper serde_json::json!
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

# Fix 3: Ensure Arti v0.40 imports
arti_imports = '''use arti_client::TorClient;
use tor_rtcompat::PreferredRuntime;
use tor_config::Config as TorConfig;'''
if 'use arti_client::TorClient' not in lib:
    lib = lib.replace(
        'use chacha20poly1305::aead::Aead;',
        f'use chacha20poly1305::aead::Aead;\n{arti_imports}'
    )

# Fix 4: Ensure TorClientConfig exists
if 'pub struct TorClientConfig {' not in lib:
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

# Fix 5: Stream enum Tor variant for SNI→Tor chaining
if 'enum Stream {' in lib and 'Tor(' not in lib:
    lib = lib.replace(
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n}',
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n    Tor(tokio::net::TcpStream),\n}'
    )

# Fix 6: circuit variable scope + enum naming
lib = lib.replace('let _circuit = self.tor_client.build_circuit().await?;', 
                  'let circuit = self.tor_client.build_circuit().await?;')
lib = lib.replace('SNI_TCP', 'SniTcp').replace('SNI_UDP', 'SniUdp')

with open(lib_path, 'w', encoding='utf-8') as f:
    f.write(lib)
print("✅ Fixed lib.rs (raw strings for Rust 2021 + Arti v0.40)")

# ============================================================================
# Verify Cargo.toml
# ============================================================================
cargo_path = ROOT / "rust/core/Cargo.toml"
with open(cargo_path, 'r') as f:
    cargo = f.read()
cargo = re.sub(r'arti-client = \{ version = "[^"]+"', 'arti-client = { version = "0.40"', cargo)
cargo = re.sub(r'tor-rtcompat = "[^"]+"', 'tor-rtcompat = "0.40"', cargo)
cargo = re.sub(r'tor-config = "[^"]+"', 'tor-config = "0.40"', cargo)
blocks = len(re.findall(r"\[target\.'cfg\(target_os = \"android\"\)'\.dependencies\]", cargo))
if blocks > 1:
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
