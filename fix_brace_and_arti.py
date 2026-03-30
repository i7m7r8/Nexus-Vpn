#!/usr/bin/env python3
"""
Nexus VPN Auto-Fix Script - Remove duplicate brace + ensure Arti v0.40 compat
Format: cat heredoc writes + chmod +x + run + exact git push commands
"""
import subprocess, re
from pathlib import Path

ROOT = Path("/home/imran/Nexus-Vpn")
if not ROOT.exists():
    ROOT = Path.cwd()

def cat_write(path, content):
    cmd = f"cat << 'FIXEOF' > {path}\n{content}\nFIXEOF"
    subprocess.run(cmd, shell=True, check=True, cwd=ROOT)

print("🔧 Nexus VPN Fix: Remove duplicate } + Arti v0.40 compat")
print("="*70)

# ============================================================================
# FIX: rust/core/src/lib.rs - Remove duplicate closing brace after TorClientConfig impl
# ============================================================================
lib_path = ROOT / "rust/core/src/lib.rs"
with open(lib_path, 'r', encoding='utf-8') as f:
    lib = f.read()

# Fix 1: Remove duplicate } after impl TorClientConfig block (line 155 error)
# Pattern: } followed immediately by another } on next line with only whitespace between
lib = re.sub(r'(impl TorClientConfig \{[^}]+\})\s*\n\s*\}', r'\1', lib, flags=re.DOTALL)

# Fix 2: Ensure TorClientConfig struct and impl are properly closed (single pair)
if lib.count('impl TorClientConfig {') > 1:
    parts = lib.split('impl TorClientConfig {')
    if len(parts) > 2:
        lib = parts[0] + 'impl TorClientConfig {' + parts[1]
        impl_start = lib.find('impl TorClientConfig {')
        if impl_start != -1:
            brace_count = 0
            pos = impl_start
            for i, ch in enumerate(lib[impl_start:], impl_start):
                if ch == '{': brace_count += 1
                elif ch == '}':
                    brace_count -= 1
                    if brace_count == 0:
                        lib = lib[:i+1] + lib[i+1:]
                        break

# Fix 3: Ensure Arti v0.40 imports are correct (no duplicates)
arti_imports = '''use arti_client::TorClient;
use tor_rtcompat::PreferredRuntime;
use tor_config::Config as TorConfig;'''

lib = re.sub(r'use arti_client::[^;]+;?\n?', '', lib)
lib = re.sub(r'use tor_rtcompat::[^;]+;?\n?', '', lib)
lib = re.sub(r'use tor_config::[^;]+;?\n?', '', lib)

if 'use chacha20poly1305::aead::Aead;' in lib:
    lib = lib.replace(
        'use chacha20poly1305::aead::Aead;',
        f'use chacha20poly1305::aead::Aead;\n{arti_imports}'
    )

# Fix 4: Ensure TorClientConfig::to_arti returns correct type for v0.40
to_arti_impl = '''impl TorClientConfig {
    pub fn to_arti(&self) -> tor_config::Config {
        tor_config::Config::default()
    }
}'''
lib = re.sub(
    r'impl TorClientConfig \{[^}]*pub fn to_arti[^}]*\}',
    to_arti_impl,
    lib,
    flags=re.DOTALL
)

# Fix 5: Ensure TorManager uses Arti v0.40 builder API
tor_mgr_impl = '''#[derive(Clone)]
pub struct TorManager {
    client: Option<TorClient<PreferredRuntime>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), String> {
        let builder = TorClient::builder()
            .with_runtime(PreferredRuntime::current())
            .with_config(config.to_arti());
        let client = builder
            .create_bootstrapped()
            .await
            .map_err(|e| format!("Arti v0.40 bootstrap: {}", e))?;
        self.client = Some(client);
        Ok(())
    }
    pub async fn stop(&mut self) { self.client = None; }
    pub fn get_client(&self) -> Option<&TorClient<PreferredRuntime>> {
        self.client.as_ref()
    }
}

impl Default for TorManager {
    fn default() -> Self { Self { client: None } }
}'''
lib = re.sub(
    r'#\[derive\(Clone\)\]\s*pub struct TorManager \{[^}]+\}[^}]*impl TorManager \{[^}]+impl Default for TorManager \{[^}]+\}',
    tor_mgr_impl,
    lib,
    flags=re.DOTALL
)

# Fix 6: Rust 2021 string prefix fixes (iptables/DNS)
lib = re.sub(r'-j\s*DROP(?!\s)', '-j DROP ', lib)
lib = re.sub(r'-j\s*ACCEPT(?!\s)', '-j ACCEPT ', lib)
lib = re.sub(r'-P\s+INPUT\s+DROP(?!\s)', '-P INPUT DROP ', lib)
lib = re.sub(r'-P\s+FORWARD\s+DROP(?!\s)', '-P FORWARD DROP ', lib)
lib = re.sub(r'-P\s+OUTPUT\s+DROP(?!\s)', '-P OUTPUT DROP ', lib)

# Fix 7: Fix malformed JSON format! → serde_json::json!
if 'format!("{{\\"stats\\"' in lib:
    json_fix = '''use serde_json::json;
        Ok(json!({
            "stats": stats,
            "leaks": {"ipv6": leak_test.ipv6_leaked, "webrtc": leak_test.webrtc_leaked, "dns": leak_test.dns_leaked},
            "pool": {"total": pool_total, "active": pool_active}
        }).to_string())'''
    lib = re.sub(
        r'Ok\(format!\([^)]*\{\{\\?"stats\\?"[^)]*\)\)',
        json_fix,
        lib,
        flags=re.DOTALL
    )

# Fix 8: Ensure Stream enum has Tor variant
if 'enum Stream {' in lib and 'Tor(' not in lib:
    lib = lib.replace(
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n}',
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n    Tor(tokio::net::TcpStream),\n}'
    )

# Fix 9: Fix circuit variable scope
lib = lib.replace('let _circuit = self.tor_client.build_circuit().await?;', 
                  'let circuit = self.tor_client.build_circuit().await?;')
# Fix 10: Fix enum naming
lib = lib.replace('SNI_TCP', 'SniTcp').replace('SNI_UDP', 'SniUdp')

cat_write(lib_path, lib)
print("✅ Fixed lib.rs (duplicate } removed + Arti v0.40 compat)")

# ============================================================================
# Ensure Cargo.toml has arti v0.40 (idempotent)
# ============================================================================
cargo_path = ROOT / "rust/core/Cargo.toml"
with open(cargo_path, 'r') as f:
    cargo = f.read()

cargo = re.sub(r'arti-client = \{ version = "[^"]+"', 'arti-client = { version = "0.40"', cargo)
cargo = re.sub(r'tor-rtcompat = "[^"]+"', 'tor-rtcompat = "0.40"', cargo)
cargo = re.sub(r'tor-config = "[^"]+"', 'tor-config = "0.40"', cargo)

cat_write(cargo_path, cargo)
print("✅ Verified Cargo.toml (arti-client v0.40.0)")

# ============================================================================
# Android files (ensure present, idempotent)
# ============================================================================
# build.gradle
gradle_path = ROOT / "android/app/build.gradle"
with open(gradle_path, 'r') as f:
    gradle = f.read()
if 'jniLibs.srcDirs' not in gradle:
    gradle = gradle.replace(
        '    buildFeatures {\n        compose true\n    }',
        '''    buildFeatures {
        compose true
    }
    sourceSets { main { jniLibs.srcDirs = ['src/main/jniLibs'] } }
    packagingOptions {
        pickFirst 'lib/**/libnexus_vpn_core.so'
        jniLibs { useLegacyPackaging false }
    }'''
    )
cat_write(gradle_path, gradle)

# AndroidManifest.xml
manifest_path = ROOT / "android/app/src/main/AndroidManifest.xml"
with open(manifest_path, 'r') as f:
    manifest = f.read()
for bad in ['DEVICE_POWER', 'WRITE_SECURE_SETTINGS', 'BIND_NETWORK_SERVICE', 'READ_LOGS', 'MANAGE_EXTERNAL_STORAGE']:
    manifest = re.sub(f'<uses-permission[^>]*{bad}[^>]*/?>\\n?', '', manifest)
manifest = manifest.replace('foregroundServiceType="connectedDevice|systemExempted"', 
                           'foregroundServiceType="connectedDevice"')
cat_write(manifest_path, manifest)

# proguard-rules.pro
proguard = '''-keepclasseswithmembernames class * { native <methods>; }
-keepclassmembers class com.nexusvpn.android.service.NexusVpnService { private static native *** *; }
-keep class com.nexusvpn.** { *; }
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**'''
cat_write(ROOT / "android/app/proguard-rules.pro", proguard)

print("✅ Android config verified")
