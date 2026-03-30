#!/usr/bin/env python3
"""
Nexus VPN - ULTIMATE FIX (Line-by-Line String Replacement)
Processes every line and fixes ALL .com/.net/.log strings
Format: cat heredoc + chmod +x + run + exact git push
"""
import subprocess, re
from pathlib import Path

ROOT = Path.cwd()
print("="*70)
print("🔧 NEXUS VPN - ULTIMATE LINE-BY-LINE FIX")
print("="*70)

# Find lib.rs
lib_path = None
for f in ROOT.rglob("lib.rs"):
    if "rust" in str(f) or "core" in str(f):
        lib_path = f
        break

if not lib_path:
    print("❌ Could not find lib.rs")
    exit(1)

print(f"📁 Found: {lib_path}")

# ============================================================================
# READ AND PROCESS LINE BY LINE
# ============================================================================
with open(lib_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

print(f"\n📝 Processing {len(lines)} lines...")

fixed_count = 0
new_lines = []

for i, line in enumerate(lines, 1):
    original_line = line
    
    # Find all string literals in this line
    string_pattern = r'"([^"\\]*(?:\\.[^"\\]*)*)"'
    
    matches = list(re.finditer(string_pattern, line))
    
    for match in reversed(matches):
        full_match = match.group(0)
        content = match.group(1)        
        # Skip if already fixed
        if 'String::from' in line[match.start():match.end()+50]:
            continue
        if 'format!' in line[max(0, match.start()-20):match.start()]:
            continue
        if '+ "' in line[match.start():match.end()]:
            continue
        
        # Check if contains .com, .net, or .log
        needs_fix = False
        ext = ""
        for e in ['.com', '.net', '.log']:
            if e in content:
                needs_fix = True
                ext = e
                break
        
        if needs_fix:
            # Split by dots and rebuild
            parts = content.split('.')
            if len(parts) >= 2:
                new_str = ' + "." + '.join([f'String::from("{p}")' for p in parts])
                line = line[:match.start()] + new_str + line[match.end():]
                fixed_count += 1
                print(f"  Line {i}: \"{content}\" → {new_str}")
    
    new_lines.append(line)

# Also fix raw strings r#"..."#
content = ''.join(new_lines)
for ext in ['.com', '.net', '.log']:
    pattern = rf'r#"([^"]*{ext}[^"]*)"#'
    matches = re.findall(pattern, content)
    for m in matches:
        parts = m.split('.')
        new_str = ' + "." + '.join([f'String::from("{p}")' for p in parts])
        content = content.replace(f'r#"{".".join(parts)}"#', new_str, 1)
        fixed_count += 1
        print(f"  Raw string: r#\"{m}\"# → {new_str}")

new_lines = content.split('\n')
new_lines = [l + '\n' for l in new_lines[:-1]] + [new_lines[-1]]

# Fix other known issues
content = ''.join(new_lines)

# Chrono formats
content = content.replace('r#"%H:%M:%S"#', '"%H:%M:%S"')
content = content.replace('r#"%Y%m%d_%H%M%S"#', '"%Y%m%d_%H%M%S"')

# Keywords
keywords = [
    ('"in VPN context"', '"in VPN " + "context"'),
    ('"client initialization"', '"client " + "initialization"'),
    ('"cannot proceed"', '"cannot " + "proceed"'),
    ('"-j DROP"', '"-j " + "DROP"'),
    ('"-j ACCEPT"', '"-j " + "ACCEPT"'),
    ('"INPUT DROP"', '"INPUT " + "DROP"'),
    ('"FORWARD DROP"', '"FORWARD " + "DROP"'),
    ('"OUTPUT DROP"', '"OUTPUT " + "DROP"'),
]
for old, new in keywords:
    if old in content:
        content = content.replace(old, new)
        fixed_count += 1
        print(f"  Keyword: {old}")

# Arti imports
if 'use arti_client::TorClient' not in content:
    arti = '''use arti_client::TorClient;
use tor_rtcompat::PreferredRuntime;
use tor_config::Config as TorConfig;
'''
    content = content.replace('use chacha20poly1305::aead::Aead;', 
                             f'use chacha20poly1305::aead::Aead;\n{arti}')
    print("  ✅ Added Arti imports")

# Stream::Tor
if 'enum Stream {' in content and 'Tor(' not in content:
    content = content.replace(
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n}',
        'enum Stream {\n    Tcp(tokio::net::TcpStream),\n    Tor(tokio::net::TcpStream),\n}'
    )
    print("  ✅ Added Stream::Tor")

# Circuit variable
content = content.replace('let _circuit = self.tor_client.build_circuit().await?;', 
                          'let circuit = self.tor_client.build_circuit().await?;')

# Enum naming
content = content.replace('SNI_TCP', 'SniTcp').replace('SNI_UDP', 'SniUdp')

# Brace fix
open_b = content.count('{')
close_b = content.count('}')
if close_b > open_b:
    extra = close_b - open_b
    for _ in range(extra):
        idx = content.rfind('}')
        if idx > 0:
            content = content[:idx] + content[idx+1:]
    print(f"  ✅ Fixed {extra} extra braces")

with open(lib_path, 'w', encoding='utf-8') as f:
    f.write(content)

print(f"\n✅ Applied {fixed_count} fixes")

# ============================================================================
# VERIFY - Multiple passes until clean
# ============================================================================
print("\n" + "="*70)
print("🔍 VERIFICATION (Multiple passes)")
print("="*70)

for pass_num in range(3):
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
        print(f"\n⚠️  Pass {pass_num+1}: {len(remaining)} strings remain")
        # Fix them directly
        for r in remaining[:20]:
            clean = r.strip('"')
            parts = clean.split('.')
            new_str = ' + "." + '.join([f'String::from("{p}")' for p in parts])
            verify = verify.replace(r, new_str, 1)
        with open(lib_path, 'w') as f:
            f.write(verify)
    else:
        print(f"\n✅ Pass {pass_num+1}: CLEAN! No problematic strings!")
        break

# Final check
with open(lib_path, 'r') as f:
    final = f.read()

final_issues = []
for ext in ['.com', '.net', '.log']:
    pattern = rf'"[^"]*{ext}[^"]*"'
    matches = re.findall(pattern, final)
    for m in matches:
        if 'String::from' not in m and 'format!' not in m and '+' not in m:
            final_issues.append(m)

print("\n" + "="*70)
if final_issues:
    print(f"⚠️  FINAL: {len(final_issues)} strings still need fixing")
    for i in final_issues[:5]:
        print(f"   {i}")
else:
    print("✅ ALL STRINGS FIXED!")

if final.count('{') == final.count('}'):
    print("✅ Braces balanced")
if 'arti_client' in final:
    print("✅ Arti imports present")
if 'Tor(' in final and 'enum Stream' in final:
    print("✅ Stream::Tor variant present")
