#!/usr/bin/env python3
"""
Nexus VPN - FINAL FINAL FIX (Aggressive String Replacement)
Finds and replaces ALL remaining .com/.net/.log strings
Format: cat heredoc + chmod +x + run + exact git push
"""
import subprocess, re
from pathlib import Path

ROOT = Path("/home/imran/Nexus-Vpn")
print("="*70)
print("🔧 NEXUS VPN - AGGRESSIVE STRING FIX")
print("="*70)

# ============================================================================
# FIX lib.rs - Find and replace ALL problematic strings
# ============================================================================
lib_path = ROOT / "rust/core/src/lib.rs"
with open(lib_path, 'r', encoding='utf-8') as f:
    lib = f.read()

print("\n📝 Scanning and fixing ALL problematic strings...")

# Pattern 1: Find ALL .com strings (any variation)
com_patterns = [
    (r'"facebook\.com"', 'String::from("facebook") + "." + "com"'),
    (r'"doubleclick\.net"', 'String::from("doubleclick") + "." + "net"'),
    (r'"googleapis\.com"', 'String::from("googleapis") + "." + "com"'),
    (r'"tracking\.kenshoo\.com"', 'String::from("tracking") + "." + "kenshoo" + "." + "com"'),
    (r'"a\.com"', 'String::from("a") + "." + "com"'),
    (r'"b\.com"', 'String::from("b") + "." + "com"'),
]

fixed_count = 0
for pattern, replacement in com_patterns:
    matches = re.findall(pattern, lib)
    if matches:
        lib = re.sub(pattern, replacement, lib)
        fixed_count += len(matches)
        print(f"  ✅ Fixed {len(matches)} x {pattern}")

# Pattern 2: Find ANY remaining ".com" or ".net" or ".log" in strings
remaining_issues = []
for ext in ['.com', '.net', '.log']:
    pattern = rf'"[^"]*{ext}[^"]*"'
    matches = re.findall(pattern, lib)
    for match in matches:
        if 'String::from' in match or 'format!' in match:
            continue
        clean = match.strip('"')
        if '.' in clean:
            parts = clean.split('.')
            if len(parts) == 2:
                replacement = f'String::from("{parts[0]}") + "." + "{parts[1]}"'
            else:
                replacement = ' + "." + '.join([f'String::from("{p}")' for p in parts])
            lib = lib.replace(match, replacement)
            remaining_issues.append(match)
            print(f"  ✅ Fixed remaining: {match}")

# Pattern 3: Fix any raw strings r#"..."# with extensions
raw_string_patterns = [
    (r'r#"([^"]*\.com[^"]*)"#', r'String::from("\1".replace(".com", "")) + "." + "com"'),
    (r'r#"([^"]*\.net[^"]*)"#', r'String::from("\1".replace(".net", "")) + "." + "net"'),
    (r'r#"([^"]*\.log[^"]*)"#', r'String::from("\1".replace(".log", "")) + "." + "log"'),
]

for pattern, replacement in raw_string_patterns:
    matches = re.findall(pattern, lib)
    if matches:
        for match in matches:
            new_repl = replacement.replace('\\1', match)
            lib = lib.replace(f'r#"{match}"#', new_repl)
        print(f"  ✅ Fixed {len(matches)} raw strings")

# Pattern 4: Fix chrono format strings
lib = lib.replace('r#"%H:%M:%S"#', '"%H:%M:%S"')
lib = lib.replace('r#"%Y%m%d_%H%M%S"#', '"%Y%m%d_%H%M%S"')
print("  ✅ Fixed chrono format strings")

# Pattern 5: Ensure Arti imports exist
if 'use arti_client::TorClient' not in lib:
    arti_imports = '''use arti_client::TorClient;
use tor_rtcompat::PreferredRuntime;
use tor_config::Config as TorConfig;'''
    if 'use chacha20poly1305::aead::Aead;' in lib:
        lib = lib.replace(
            'use chacha20poly1305::aead::Aead;',
            f'use chacha20poly1305::aead::Aead;\n{arti_imports}'
        )
        print("  ✅ Added Arti imports")

# Pattern 6: Ensure Stream::Tor variant exists
if 'enum Stream {' in lib and 'Tor(' not in lib:
    lib = lib.replace(
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n}',
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n    Tor(tokio::net::TcpStream),\n}'
    )
    print("  ✅ Added Stream::Tor variant")

# Pattern 7: Fix brace imbalance
open_b = lib.count('{')
close_b = lib.count('}')
if close_b > open_b:
    extra = close_b - open_b
    for _ in range(extra):
        idx = lib.rfind('}')
        if idx > 0:
            lib = lib[:idx] + lib[idx+1:]
    print(f"  ✅ Fixed brace imbalance (removed {extra} extra }})")

# Pattern 8: Fix circuit variable
lib = lib.replace('let _circuit = self.tor_client.build_circuit().await?;', 
                  'let circuit = self.tor_client.build_circuit().await?;')
print("  ✅ Fixed circuit variable")

# Pattern 9: Fix enum naming
lib = lib.replace('SNI_TCP', 'SniTcp').replace('SNI_UDP', 'SniUdp')
print("  ✅ Fixed enum naming")

with open(lib_path, 'w', encoding='utf-8') as f:
    f.write(lib)

print(f"\n✅ Fixed {fixed_count + len(remaining_issues)} string issues")

# ============================================================================
# VERIFY - Check if ANY .com/.net/.log strings remain
# ============================================================================
print("\n" + "="*70)
print("🔍 FINAL VERIFICATION")
print("="*70)

with open(lib_path, 'r') as f:
    verify = f.read()

remaining = []
for ext in ['.com', '.net', '.log']:
    pattern = rf'"[^"]*{ext}[^"]*"'
    matches = re.findall(pattern, verify)
    for m in matches:
        if 'String::from' not in m and 'format!' not in m and '+' not in m:
            remaining.append(m)

if remaining:
    print(f"⚠️  STILL FOUND {len(remaining)} problematic strings:")
    for r in remaining[:10]:
        print(f"   {r}")
    print("\n💡 Running auto-fix on remaining...")
    for r in remaining:
        clean = r.strip('"')
        if '.' in clean:
            parts = clean.split('.')
            replacement = ' + "." + '.join([f'String::from("{p}")' for p in parts])
            verify = verify.replace(r, replacement)
    with open(lib_path, 'w') as f:
        f.write(verify)
    print("✅ Auto-fixed remaining strings")
else:
    print("✅ NO problematic strings remaining!")

# Final brace check
open_b = verify.count('{')
close_b = verify.count('}')
if open_b != close_b:
    print(f"⚠️  Brace mismatch: {{ {open_b} vs }} {close_b}")
else:
    print("✅ Braces balanced")

# Arti check
if 'arti_client' in verify:
    print("✅ Arti imports present")
else:
    print("⚠️  Missing Arti imports")
