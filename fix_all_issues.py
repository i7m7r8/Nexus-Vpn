#!/usr/bin/env python3
import re

filepath = 'rust/core/src/lib.rs'
content = open(filepath).read()
lines = content.split('\n')

fixes = 0

print("="*70)
print("COMPREHENSIVE FIX SCRIPT")
print("="*70)

# FIX 1: Add missing } after CipherSuite enum (before line ~695)
print("\n[1] Fixing CipherSuite enum missing }...")
for i, line in enumerate(lines):
    if 'Custom(String),' in line and 'CipherSuite' in '\n'.join(lines[max(0,i-10):i]):
        # Check if next line starts with 'impl'
        if i+1 < len(lines) and 'impl Default for CipherSuite' in lines[i+1]:
            lines.insert(i+1, '}')
            print(f"  Added }} after line {i+1}")
            fixes += 1
            break

# FIX 2: Separate merged /// comment and code lines
print("\n[2] Fixing merged comment/code lines...")
merged_patterns = [
    (r'(/// Strict exit node policy)\s+(pub strict_exit:)', r'\1\n    \2'),
    (r'(/// IPv6 leaked)\s+(pub ipv6_leaked:)', r'\1\n    \2'),
    (r'(/// High-performance encryption engine supporting multiple ciphers)\s+(pub struct EncryptionEngine)', r'\1\n\2'),
    (r'(/// HMAC-SHA256 for integrity)\s+(pub fn hmac_sha256)', r'\1\n    \2'),
    (r'(/// Enable split tunneling)\s+(pub enabled:)', r'\1\n    \2'),
    (r'(/// Mode: Include or Exclude)\s+(pub mode:)', r'\1\n    \2'),
    (r'(/// Block local network access)\s+(pub block_lan_access:)', r'\1\n    \2'),
    (r'(/// DNS mode)\s+(pub mode:)', r'\1\n    \2'),
    (r'(/// Main VPN Engine - Controls all VPN operations)\s+(/// This is the central controller)', r'\1\n\2'),
    (r'(/// Enable SNI spoofing)\s+(pub enabled:)', r'\1\n    \2'),
]

new_lines = []
for i, line in enumerate(lines):
    fixed = False
    for pattern, replacement in merged_patterns:
        if re.search(pattern, line):
            new_lines.append(re.sub(pattern, replacement, line))
            print(f"  Fixed merged line {i+1}")
            fixes += 1
            fixed = True
            break
    if not fixed:
        new_lines.append(line)

lines = new_lines

# FIX 3: Remove extra } at end if there are more than needed
print("\n[3] Checking end of file...")
while len(lines) > 0 and lines[-1].strip() == '}':
    # Count how many } we have at end
    end_braces = 0
    for i in range(len(lines)-1, max(0, len(lines)-10), -1):
        if lines[i].strip() == '}':
            end_braces += 1
        else:
            break
    
    if end_braces > 2:
        lines.pop()
        print(f"  Removed extra }} at end")
        fixes += 1
    break

# Write fixed content
new_content = '\n'.join(lines)
open(filepath, 'w').write(new_content)

print("\n" + "="*70)
print(f"TOTAL FIXES APPLIED: {fixes}")
print("="*70)

# Verify balance
o = new_content.count('{')
c = new_content.count('}')
print(f"\nBrace count: {{={o}, }}={c}, balance={o-c}")

if o == c:
    print("\n✅ BRACE BALANCE OK")
else:
    print(f"\n⚠️  BRACE IMBALANCE: {o-c}")
