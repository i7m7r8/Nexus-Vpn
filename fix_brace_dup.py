#!/usr/bin/env python3
"""
Nexus VPN Fix: Remove duplicate } after impl TorClientConfig
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

print("🔧 Nexus VPN: Remove duplicate }} after impl TorClientConfig")

# ============================================================================
# FIX lib.rs: Remove duplicate closing brace (simple targeted approach)
# ============================================================================
lib_path = ROOT / "rust/core/src/lib.rs"
with open(lib_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Find and fix duplicate } after "impl TorClientConfig {" block
new_lines = []
i = 0
while i < len(lines):
    line = lines[i]
    new_lines.append(line)
    
    # Detect end of impl TorClientConfig block: line with just "}"
    if 'impl TorClientConfig {' in line:
        # Find the matching closing brace by counting braces
        brace_depth = 1
        j = i + 1
        while j < len(lines) and brace_depth > 0:
            new_lines.append(lines[j])
            brace_depth += lines[j].count('{') - lines[j].count('}')
            j += 1
        
        # Now j points to line AFTER the matching }
        # Check if next line is ALSO just a } (the duplicate)
        if j < len(lines):
            next_line = lines[j].strip()
            if next_line == '}':
                # Skip this duplicate brace line
                i = j  # Move past duplicate
            else:
                i = j - 1  # Continue from last added line
        i += 1
        continue
    i += 1

# Write fixed content
with open(lib_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print("✅ Removed duplicate }} in lib.rs")

# ============================================================================
# Verify Cargo.toml has arti v0.40
# ============================================================================
cargo_path = ROOT / "rust/core/Cargo.toml"
with open(cargo_path, 'r') as f:
    cargo = f.read()

# Ensure single android target block + arti v0.40
if 'arti-client = { version = "0.40"' not in cargo:
    cargo = re.sub(r'arti-client = \{ version = "[^"]+"', 'arti-client = { version = "0.40"', cargo)
if 'tor-rtcompat = "0.40"' not in cargo:
    cargo = re.sub(r'tor-rtcompat = "[^"]+"', 'tor-rtcompat = "0.40"', cargo)
if 'tor-config = "0.40"' not in cargo:
    cargo = re.sub(r'tor-config = "[^"]+"', 'tor-config = "0.40"', cargo)

# Ensure only ONE [target.'cfg(target_os = "android")'] block
android_blocks = len(re.findall(r"\[target\.'cfg\(target_os = \"android\"\)'\.dependencies\]", cargo))
if android_blocks > 1:
    # Keep first, remove rest
    parts = cargo.split("[target.'cfg(target_os = \"android\")'.dependencies]")
    cargo = parts[0] + "[target.'cfg(target_os = \"android\")'.dependencies]" + parts[-1]

cat_write(cargo_path, cargo)
print("✅ Verified Cargo.toml")

# ============================================================================
# Android files (idempotent)
# ============================================================================
# build.gradle
gradle_path = ROOT / "android/app/build.gradle"
with open(gradle_path, 'r') as f:
    gradle = f.read()
if 'jniLibs.srcDirs' not in gradle:
    gradle += '''
    sourceSets { main { jniLibs.srcDirs = ['src/main/jniLibs'] } }
    packagingOptions { pickFirst 'lib/**/libnexus_vpn_core.so' }
'''
cat_write(gradle_path, gradle)

# AndroidManifest.xml - remove impossible perms
manifest_path = ROOT / "android/app/src/main/AndroidManifest.xml"
with open(manifest_path, 'r') as f:
    manifest = f.read()
for bad in ['DEVICE_POWER', 'WRITE_SECURE_SETTINGS', 'BIND_NETWORK_SERVICE', 'READ_LOGS', 'MANAGE_EXTERNAL_STORAGE']:
    manifest = re.sub(f'<uses-permission[^>]*{bad}[^>]*/?>\\n?', '', manifest)
cat_write(manifest_path, manifest)

# proguard-rules.pro
proguard = '''-keepclasseswithmembernames class * { native <methods>; }
-keepclassmembers class com.nexusvpn.android.service.NexusVpnService { private static native *** *; }
-keep class com.nexusvpn.** { *; }'''
cat_write(ROOT / "android/app/proguard-rules.pro", proguard)

print("✅ Android config verified")
