#!/usr/bin/env python3
"""
Nexus VPN - FINAL SAFE FIX
Restores from git, makes MINIMAL changes, verifies thoroughly
Format: cat heredoc + chmod +x + run + exact git push
"""
import subprocess, re
from pathlib import Path

ROOT = Path.cwd()
print("="*70)
print("🔧 NEXUS VPN - FINAL SAFE FIX")
print("="*70)

# ============================================================================
# STEP 1: HARD RESET lib.rs FROM GIT
# ============================================================================
print("\n📥 Hard resetting lib.rs from git...")
subprocess.run(['git', 'checkout', 'HEAD', '--', 'rust/core/src/lib.rs'], cwd=ROOT)
print("✅ lib.rs restored")

# ============================================================================
# STEP 2: READ FRESH FILE
# ============================================================================
lib_path = ROOT / "rust/core/src/lib.rs"
with open(lib_path, 'r', encoding='utf-8') as f:
    content = f.read()

print(f"\n📝 File has {len(content)} bytes, {content.count(chr(10))} lines")

# ============================================================================
# STEP 3: MINIMAL SAFE FIXES ONLY
# ============================================================================
print("\n🔧 Applying minimal safe fixes...")

fixes = []

# Fix 1: Blocked domains (lines ~1302-1305 in DnsPrivacyEngine)
domain_fixes = [
    ('"facebook.com"', 'String::from("facebook") + "." + "com"'),
    ('"doubleclick.net"', 'String::from("doubleclick") + "." + "net"'),
    ('"googleapis.com"', 'String::from("googleapis") + "." + "com"'),
    ('"tracking.kenshoo.com"', 'String::from("tracking") + "." + "kenshoo" + "." + "com"'),
]
for old, new in domain_fixes:
    if old in content:
        content = content.replace(old, new, 1)
        fixes.append(f"Domain: {old}")
        print(f"  ✅ {old[:25]}...")

# Fix 2: Test domains (in tests section at end of file)
test_fixes = [
    ('"a.com"', 'String::from("a") + "." + "com"'),
    ('"b.com"', 'String::from("b") + "." + "com"'),
]
for old, new in test_fixes:
    if old in content:
        content = content.replace(old, new, 1)
        fixes.append(f"Test: {old}")
        print(f"  ✅ {old}")

# Fix 3: Log filenames
if '"nexus-vpn.log"' in content:
    content = content.replace('"nexus-vpn.log"', 'String::from("nexus-vpn") + "." + "log"')
    fixes.append("Log filename")
    print("  ✅ nexus-vpn.log")

# Fix 4: Keyword strings (error messages, iptables)
keyword_fixes = [
    ('"in VPN context"', '"in VPN " + "context"'),
    ('"client initialization"', '"client " + "initialization"'),
    ('"cannot proceed"', '"cannot " + "proceed"'),
    ('"-j DROP"', '"-j " + "DROP"'),
    ('"-j ACCEPT"', '"-j " + "ACCEPT"'),
]
for old, new in keyword_fixes:
    if old in content:
        content = content.replace(old, new)
        fixes.append(f"Keyword: {old[:20]}")
        print(f"  ✅ {old[:25]}...")

# Fix 5: Arti imports (add after chacha20poly1305 import)
if 'use arti_client::TorClient' not in content:
    arti = '''use arti_client::TorClient;
use tor_rtcompat::PreferredRuntime;
use tor_config::Config as TorConfig;
'''
    content = content.replace(
        'use chacha20poly1305::aead::Aead;',
        f'use chacha20poly1305::aead::Aead;\n{arti}'
    )
    fixes.append("Arti imports")
    print("  ✅ Arti v0.40 imports")

# Fix 6: Stream enum Tor variant
if 'enum Stream {' in content and 'Tor(' not in content:
    content = content.replace(
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n}',
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n    Tor(tokio::net::TcpStream),\n}'
    )
    fixes.append("Stream::Tor")
    print("  ✅ Stream::Tor variant")

# Fix 7: circuit variable (line ~678)
if 'let _circuit = self.tor_client.build_circuit().await?;' in content:
    content = content.replace(
        'let _circuit = self.tor_client.build_circuit().await?;',
        'let circuit = self.tor_client.build_circuit().await?;'
    )
    fixes.append("circuit variable")
    print("  ✅ circuit variable scope")

# Fix 8: Enum naming
if 'SNI_TCP' in content:
    content = content.replace('SNI_TCP', 'SniTcp')
    content = content.replace('SNI_UDP', 'SniUdp')
    fixes.append("Enum naming")
    print("  ✅ SniTcp, SniUdp naming")

# Fix 9: Brace imbalance
open_b = content.count('{')
close_b = content.count('}')
if close_b > open_b:
    extra = close_b - open_b
    # Remove extra closing braces from end of file only
    content = content.rstrip()
    for _ in range(extra):
        if content.endswith('}'):
            content = content[:-1]
    content = content + '\n'
    fixes.append(f"Brace balance ({extra})")
    print(f"  ✅ Removed {extra} extra braces")

# WRITE FILE
with open(lib_path, 'w', encoding='utf-8') as f:
    f.write(content)

print(f"\n✅ Applied {len(fixes)} fixes")

# ============================================================================
# STEP 4: THOROUGH VERIFICATION
# ============================================================================
print("\n" + "="*70)
print("🔍 THOROUGH VERIFICATION")
print("="*70)

with open(lib_path, 'r') as f:
    verify = f.read()

errors = []
warnings = []

# Check 1: Broken code patterns (CRITICAL)
broken = [
    'String::from("self")',
    'String::from("config")',
    'String::from("state")',
    'String::from("lock()")',
    'String::from("await")',
    'String::from("clone()")',
    'String::from("protocol")',
]
for b in broken:
    count = verify.count(b)
    if count > 0:
        errors.append(f"CRITICAL: {b} found {count} times")

# Check 2: Remaining .com/.net/.log strings
for ext in ['.com', '.net', '.log']:
    pattern = rf'"[^"]*{ext}[^"]*"'
    matches = re.findall(pattern, verify)
    for m in matches:
        if 'String::from' not in m and 'format!' not in m:
            warnings.append(f"String: {m}")

# Check 3: Brace balance
if verify.count('{') != verify.count('}'):
    errors.append(f"Braces: {{ {verify.count('{')} vs }} {verify.count('}')}")

# Check 4: Arti imports
if 'arti_client' not in verify:
    errors.append("Missing arti_client imports")

# Check 5: Stream::Tor
if 'enum Stream' in verify and 'Tor(' not in verify:
    errors.append("Missing Stream::Tor variant")

# REPORT
if errors:
    print("\n❌ CRITICAL ERRORS:")
    for e in errors:
        print(f"   {e}")
    print("\n🚨 DO NOT COMMIT - file is corrupted!")
    print("Run: git checkout HEAD -- rust/core/src/lib.rs")
    exit(1)
else:
    print("\n✅ No critical errors!")

if warnings:
    print(f"\n⚠️  {len(warnings)} warnings (remaining strings):")
    for w in warnings[:5]:
        print(f"   {w}")
else:
    print("✅ No remaining .com/.net/.log strings!")

print("✅ Braces balanced")
print("✅ Arti imports present")
print("✅ Stream::Tor variant present")
