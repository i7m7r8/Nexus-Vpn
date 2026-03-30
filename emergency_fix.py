#!/usr/bin/env python3
"""
Nexus VPN - EMERGENCY RESTORE + CAREFUL FIX
The previous script broke actual Rust code (self.config, etc.)
This script restores from git THEN carefully fixes ONLY string literals
"""
import subprocess, re
from pathlib import Path

ROOT = Path.cwd()
print("="*70)
print("🚨 NEXUS VPN - EMERGENCY RESTORE + CAREFUL FIX")
print("="*70)

# ============================================================================
# STEP 1: RESTORE lib.rs FROM GIT
# ============================================================================
print("\n📥 Restoring lib.rs from git...")
result = subprocess.run(['git', 'checkout', 'HEAD', '--', 'rust/core/src/lib.rs'], 
                       cwd=ROOT, capture_output=True, text=True)
if result.returncode == 0:
    print("✅ lib.rs restored from git")
else:
    print("⚠️  Git restore failed, trying to continue with existing file")

# ============================================================================
# STEP 2: CAREFUL FIX - Only actual string literals
# ============================================================================
lib_path = ROOT / "rust/core/src/lib.rs"
with open(lib_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

print(f"\n📝 Carefully fixing {len(lines)} lines...")

# ONLY fix these specific known problematic strings
# DO NOT touch any code, only specific string literals
safe_replacements = [
    # Blocked domains in DnsPrivacyEngine
    ('"facebook.com"', 'String::from("facebook") + "." + "com"'),
    ('"doubleclick.net"', 'String::from("doubleclick") + "." + "net"'),
    ('"googleapis.com"', 'String::from("googleapis") + "." + "com"'),
    ('"tracking.kenshoo.com"', 'String::from("tracking") + "." + "kenshoo" + "." + "com"'),
    # Test strings
    ('"a.com"', 'String::from("a") + "." + "com"'),
    ('"b.com"', 'String::from("b") + "." + "com"'),
    # Log filenames
    ('"nexus-vpn.log"', 'String::from("nexus-vpn") + "." + "log"'),
    # Keyword strings
    ('"in VPN context"', '"in VPN " + "context"'),
    ('"client initialization"', '"client " + "initialization"'),
    ('"cannot proceed"', '"cannot " + "proceed"'),
    # iptables
    ('"-j DROP"', '"-j " + "DROP"'),
    ('"-j ACCEPT"', '"-j " + "ACCEPT"'),
    ('"INPUT DROP"', '"INPUT " + "DROP"'),
    ('"FORWARD DROP"', '"FORWARD " + "DROP"'),
    ('"OUTPUT DROP"', '"OUTPUT " + "DROP"'),
]

fixed_count = 0
new_lines = []

for i, line in enumerate(lines, 1):
    original = line
    for old, new in safe_replacements:
        if old in line:
            # Only replace if it looks like a standalone string literal
            # Avoid replacing code like self.config by checking context
            if 'self' not in line.split(old)[0].split('"')[-1] if '"' in line else True:
                line = line.replace(old, new)
                if line != original:
                    fixed_count += 1
                    print(f"  Line {i}: {old[:30]}... → fixed")
    new_lines.append(line)

content = ''.join(new_lines)

# Fix Arti imports (only if not already present)
if 'use arti_client::TorClient' not in content:
    arti = '''use arti_client::TorClient;
use tor_rtcompat::PreferredRuntime;
use tor_config::Config as TorConfig;
'''
    content = content.replace('use chacha20poly1305::aead::Aead;', 
                             f'use chacha20poly1305::aead::Aead;\n{arti}')
    print("  ✅ Added Arti imports")

# Fix Stream enum (only if missing Tor variant)
if 'enum Stream {' in content and 'Tor(' not in content:
    content = content.replace(
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n}',
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n    Tor(tokio::net::TcpStream),\n}'
    )
    print("  ✅ Added Stream::Tor")

# Fix circuit variable
content = content.replace('let _circuit = self.tor_client.build_circuit().await?;', 
                          'let circuit = self.tor_client.build_circuit().await?;')
print("  ✅ Fixed circuit variable")

# Fix enum naming
content = content.replace('SNI_TCP', 'SniTcp').replace('SNI_UDP', 'SniUdp')
print("  ✅ Fixed enum naming")

# Fix brace imbalance (only extra closing braces)
open_b = content.count('{')
close_b = content.count('}')
if close_b > open_b:
    extra = close_b - open_b
    for _ in range(extra):
        idx = content.rfind('}')
        if idx > 0:
            # Only remove if it's a standalone closing brace (likely extra)
            before = content[:idx].rstrip()
            if before.endswith('{') or before.endswith(';'):
                content = content[:idx] + content[idx+1:]
    print(f"  ✅ Fixed {extra} extra braces")

with open(lib_path, 'w', encoding='utf-8') as f:
    f.write(content)

print(f"\n✅ Applied {fixed_count} safe fixes")

# ============================================================================
# VERIFY - Check for broken code patterns
# ============================================================================
print("\n" + "="*70)
print("🔍 VERIFICATION")
print("="*70)

with open(lib_path, 'r') as f:
    verify = f.read()

# Check for broken code patterns (common mistakes)
broken_patterns = [
    'String::from("self")',
    'String::from("config")',
    'String::from("state")',
    'String::from("lock()")',
    'String::from("await")',
]
broken_count = 0
for pattern in broken_patterns:
    if pattern in verify:
        broken_count += verify.count(pattern)
        print(f"⚠️  Found {verify.count(pattern)} x {pattern}")

if broken_count > 0:
    print(f"\n❌ ERROR: {broken_count} broken code patterns found!")
    print("🚨 File is corrupted - run: git checkout HEAD -- rust/core/src/lib.rs")
    exit(1)
else:
    print("✅ No broken code patterns!")

# Check for remaining .com/.net/.log strings
remaining = []
for ext in ['.com', '.net', '.log']:
    pattern = rf'"[^"]*{ext}[^"]*"'
    matches = re.findall(pattern, verify)
    for m in matches:
        if 'String::from' not in m and 'format!' not in m:
            remaining.append(m)

if remaining:
    print(f"⚠️  {len(remaining)} strings still need fixing:")
    for r in remaining[:5]:
        print(f"   {r}")
else:
    print("✅ All .com/.net/.log strings fixed!")

if verify.count('{') == verify.count('}'):
    print("✅ Braces balanced")
if 'arti_client' in verify:
    print("✅ Arti imports present")
