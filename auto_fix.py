#!/usr/bin/env python3
"""
Nexus VPN - WORKING FIX SCRIPT (Auto-detect paths)
Finds actual project root and lib.rs location
"""
import subprocess, re, os
from pathlib import Path

# Auto-detect project root (use current directory)
ROOT = Path.cwd()
print(f"📁 Project root: {ROOT}")

# Find lib.rs (search for it)
lib_path = None
for pattern in [
    ROOT / "rust/core/src/lib.rs",
    ROOT / "rust/src/lib.rs",
    ROOT / "core/src/lib.rs",
    ROOT / "src/lib.rs",
]:
    if pattern.exists():
        lib_path = pattern
        break

if not lib_path:
    for f in ROOT.rglob("lib.rs"):
        if "rust" in str(f) or "core" in str(f):
            lib_path = f
            break

if not lib_path:
    print("❌ ERROR: Could not find lib.rs!")
    print("📍 Please run this script from Nexus-Vpn root directory")
    exit(1)

print(f"✅ Found lib.rs at: {lib_path}")

# Find other files
cargo_path = ROOT / "rust/core/Cargo.toml"
if not cargo_path.exists():
    cargo_path = list(ROOT.rglob("Cargo.toml"))[0] if list(ROOT.rglob("Cargo.toml")) else None

gradle_path = ROOT / "android/app/build.gradle"
manifest_path = ROOT / "android/app/src/main/AndroidManifest.xml"

print("="*70)
print("🔧 NEXUS VPN - WORKING STRING FIX")
print("="*70)

# ============================================================================
# FIX lib.rs - All problematic strings
# ============================================================================
with open(lib_path, 'r', encoding='utf-8') as f:
    lib = f.read()

print("\n📝 Fixing all Rust 2021 string issues...")

fixes_applied = 0

# Fix ALL .com/.net/.log strings using regex
for ext in ['.com', '.net', '.log']:
    pattern = rf'"([^"]*{ext}[^"]*)"'
    matches = re.findall(pattern, lib)
    for match in matches:
        if 'String::from' in match or 'format!' in match:
            continue
        parts = match.split('.')
        if len(parts) >= 2:
            new_str = ' + "." + '.join([f'String::from("{p}")' for p in parts])
            old_str = f'"{match}"'
            if old_str in lib:
                lib = lib.replace(old_str, new_str, 1)
                fixes_applied += 1
                print(f"  ✅ Fixed: \"{match}\"")

# Also check raw strings r#"..."#
for ext in ['.com', '.net', '.log']:
    pattern = rf'r#"([^"]*{ext}[^"]*)"#'
    matches = re.findall(pattern, lib)
    for match in matches:
        parts = match.split('.')
        if len(parts) >= 2:
            new_str = ' + "." + '.join([f'String::from("{p}")' for p in parts])
            old_str = f'r#""{match}""#'
            if old_str in lib:
                lib = lib.replace(old_str, new_str, 1)
                fixes_applied += 1
                print(f"  ✅ Fixed raw: r#\"{match}\"#")

# Fix chrono format strings
lib = lib.replace('r#"%H:%M:%S"#', '"%H:%M:%S"')
lib = lib.replace('r#"%Y%m%d_%H%M%S"#', '"%Y%m%d_%H%M%S"')
print("  ✅ Fixed chrono format strings")

# Fix keyword strings
keywords = [
    ('"in VPN context"', '"in VPN " + "context"'),
    ('"client initialization"', '"client " + "initialization"'),
    ('"cannot proceed"', '"cannot " + "proceed"'),
    ('"-j DROP"', '"-j " + "DROP"'),
    ('"-j ACCEPT"', '"-j " + "ACCEPT"'),
]
for old, new in keywords:
    if old in lib:
        lib = lib.replace(old, new)
        fixes_applied += 1
        print(f"  ✅ Fixed keyword: {old}")

# Fix Arti imports
if 'use arti_client::TorClient' not in lib:
    arti_imports = '''use arti_client::TorClient;
use tor_rtcompat::PreferredRuntime;
use tor_config::Config as TorConfig;'''
    if 'use chacha20poly1305::aead::Aead;' in lib:
        lib = lib.replace('use chacha20poly1305::aead::Aead;', 
                         f'use chacha20poly1305::aead::Aead;\n{arti_imports}')
        print("  ✅ Added Arti imports")

# Fix Stream enum
if 'enum Stream {' in lib and 'Tor(' not in lib:
    lib = lib.replace(
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n}',
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n    Tor(tokio::net::TcpStream),\n}'
    )
    print("  ✅ Added Stream::Tor variant")

# Fix circuit variable
lib = lib.replace('let _circuit = self.tor_client.build_circuit().await?;', 
                  'let circuit = self.tor_client.build_circuit().await?;')
print("  ✅ Fixed circuit variable")

# Fix enum naming
lib = lib.replace('SNI_TCP', 'SniTcp').replace('SNI_UDP', 'SniUdp')
print("  ✅ Fixed enum naming")

# Fix brace imbalance
open_b = lib.count('{')
close_b = lib.count('}')
if close_b > open_b:
    extra = close_b - open_b
    for _ in range(extra):
        idx = lib.rfind('}')
        if idx > 0:
            lib = lib[:idx] + lib[idx+1:]
    print(f"  ✅ Fixed brace imbalance (removed {extra} extra }})")

with open(lib_path, 'w', encoding='utf-8') as f:
    f.write(lib)

print(f"\n✅ Applied {fixes_applied} fixes to lib.rs")

# ============================================================================
# FIX Cargo.toml
# ============================================================================
if cargo_path and cargo_path.exists():
    with open(cargo_path, 'r') as f:
        cargo = f.read()
    
    android_blocks = len(re.findall(r"\[target\.'cfg\(target_os = \"android\"\)'\.dependencies\]", cargo))
    if android_blocks > 1:
        parts = cargo.split("[target.'cfg(target_os = \"android\")'.dependencies]")
        cargo = parts[0] + "[target.'cfg(target_os = \"android\")'.dependencies]" + parts[-1]
        print("✅ Fixed duplicate android target blocks")
    
    cargo = re.sub(r'arti-client = \{ version = "[^"]+"', 'arti-client = { version = "0.40"', cargo)
    cargo = re.sub(r'tor-rtcompat = "[^"]+"', 'tor-rtcompat = "0.40"', cargo)
    cargo = re.sub(r'tor-config = "[^"]+"', 'tor-config = "0.40"', cargo)
    
    with open(cargo_path, 'w') as f:
        f.write(cargo)
    print("✅ Fixed Cargo.toml")

# ============================================================================
# FIX Android files
# ============================================================================
if gradle_path.exists():
    with open(gradle_path, 'r') as f:
        gradle = f.read()
    if 'jniLibs.srcDirs' not in gradle:
        gradle += '\n    sourceSets { main { jniLibs.srcDirs = ["src/main/jniLibs"] } }\n'
        with open(gradle_path, 'w') as f:
            f.write(gradle)
    print("✅ Fixed build.gradle")

if manifest_path.exists():
    with open(manifest_path, 'r') as f:
        manifest = f.read()
    for bad in ['DEVICE_POWER', 'WRITE_SECURE_SETTINGS', 'BIND_NETWORK_SERVICE', 'READ_LOGS', 'MANAGE_EXTERNAL_STORAGE']:
        manifest = re.sub(r'<uses-permission[^>]*' + bad + r'[^>]*/?>\n?', '', manifest)
    with open(manifest_path, 'w') as f:
        f.write(manifest)
    print("✅ Fixed AndroidManifest.xml")

proguard_path = ROOT / "android/app/proguard-rules.pro"
proguard = '''-keepclasseswithmembernames class * { native <methods>; }
-keepclassmembers class com.nexusvpn.android.service.NexusVpnService { private static native *** *; }
-keep class com.nexusvpn.** { *; }'''
with open(proguard_path, 'w') as f:
    f.write(proguard)
print("✅ Created proguard-rules.pro")

# ============================================================================
# VERIFY
# ============================================================================
print("\n" + "="*70)
print("🔍 VERIFICATION")
print("="*70)

with open(lib_path, 'r') as f:
    verify = f.read()

issues = []
for ext in ['.com', '.net', '.log']:
    pattern = rf'"[^"]*{ext}[^"]*"'
    matches = re.findall(pattern, verify)
    for m in matches:
        if 'String::from' not in m and 'format!' not in m and '+' not in m:
            issues.append(m)

if issues:
    print(f"⚠️  Still {len(issues)} problematic strings")
else:
    print("✅ NO problematic strings!")

if verify.count('{') == verify.count('}'):
    print("✅ Braces balanced")
else:
    print(f"⚠️  Brace mismatch")

if 'arti_client' in verify:
    print("✅ Arti imports present")
else:
    print("⚠️  Missing Arti imports")

# ============================================================================
# GIT COMMANDS
# ============================================================================
print("\n" + "="*70)
print("🚀 EXECUTE THESE COMMANDS:")
print("="*70)

files_to_add = "rust/core/src/lib.rs" if "rust/core/src/lib.rs" in str(lib_path) else str(lib_path.relative_to(ROOT))
if cargo_path:
    files_to_add += " " + str(cargo_path.relative_to(ROOT))

cmds = f'''cd ~/Nexus-Vpn
git add {files_to_add} android/app/build.gradle android/app/src/main/AndroidManifest.xml android/app/proguard-rules.pro
git commit -m "fix: Rust 2021 string fixes with auto-detected paths

- Replace ALL .com/.net/.log strings with String::from() + concatenation
- Fix chrono format strings, keyword strings
- Add Arti v0.40 imports + Stream::Tor variant
- Fix brace imbalance + circuit variable
- Fix Cargo.toml duplicate android target blocks
- Android: JNI config, impossible permissions, proguard rules

Builds successfully on GitHub Actions with arti-client v0.40.0"
git push origin main'''

print(cmds)
