#!/usr/bin/env python3
from pathlib import Path
import re

filepath = 'rust/core/src/lib.rs'
content = Path(filepath).read_text()
lines = content.split('\n')

print("=" * 70)
print("BRACE BALANCE CHECKER")
print("=" * 70)

total_open = content.count('{')
total_close = content.count('}')

print("\n[1] Total Brace Count")
print("-" * 70)
print("  Open braces:  " + str(total_open))
print("  Close braces: " + str(total_close))
print("  Balance: " + str(total_open - total_close))

depth = 0
depth_at_line = []
max_depth = 0
negative_lines = []

for i, line in enumerate(lines):
    depth += line.count('{') - line.count('}')
    depth_at_line.append(depth)
    if depth > max_depth:
        max_depth = depth
    if depth < 0:
        negative_lines.append(i + 1)

print("\n[2] Depth Analysis")
print("-" * 70)
print("  Max depth: " + str(max_depth))
print("  Final depth: " + str(depth))

if negative_lines:
    print("\n  Lines with negative depth:")
    for nl in negative_lines[:10]:
        idx = nl - 1
        print("    Line " + str(nl) + ": " + lines[idx][:60])

print("\n[3] Code Blocks")
print("-" * 70)

blocks = []stack = []

for i, line in enumerate(lines):
    match = re.search(r'(pub\s+)?(impl|struct|enum|fn|mod)\s+(\w+)', line)
    if match and '{' in line:
        btype = match.group(2)
        bname = match.group(3)
        stack.append((btype, bname, i + 1))
    
    if line.strip() == '}' and stack:
        btype, bname, start = stack.pop()
        blocks.append((btype, bname, start, i + 1))

print("  Found " + str(len(blocks)) + " code blocks")

print("\n[4] Section Analysis")
print("-" * 70)

sections = [
    ('EncryptionEngine', 'impl EncryptionEngine', '// === SNI HANDLER'),
    ('SniHandler', 'impl SniHandler', '// === TOR MANAGER'),
    ('TorManager', 'impl TorManager', '// === VPN CONNECTION'),
    ('VpnConnection', 'impl VpnConnection', '// === VPN ENGINE'),
    ('VpnEngine', 'impl VpnEngine', '// === CONNECTION POOL'),
    ('JNI', '// === JNI EXPORTS', '// === INITIALIZATION'),
    ('Tests', '#[cfg(test)]', 'END OF NEXUS'),
]

for name, start_marker, end_marker in sections:
    start_idx = content.find(start_marker)
    end_idx = content.find(end_marker, start_idx)
    
    if start_idx != -1:
        if end_idx == -1:
            end_idx = len(content)
        
        section = content[start_idx:end_idx]
        o = section.count('{')
        c = section.count('}')
        bal = o - c
        
        status = "OK" if bal == 0 else "ISSUE"
        print("  [" + status + "] " + name + ": {=" + str(o) + ", }=" + str(c) + ", bal=" + str(bal))

print("\n[5] Merged Lines")
print("-" * 70)

merged = []
for i, line in enumerate(lines):
    if '///' in line and 'pub ' in line:        parts = line.split('pub')
        if len(parts) > 1 and '///' in parts[0]:
            merged.append(i + 1)
            print("  Line " + str(i + 1) + ": " + line[:60])

if not merged:
    print("  None found")

print("\n[6] End of File")
print("-" * 70)

final = depth_at_line[-1] if depth_at_line else 0
print("  Final depth: " + str(final))

if final != 0:
    print("  ISSUE: File ends at depth " + str(final))
    print("\n  Last 15 lines:")
    for i in range(max(0, len(lines) - 15), len(lines)):
        marker = ">>> " if depth_at_line[i] != 0 else "    "
        print("  " + marker + "L" + str(i+1) + ":d" + str(depth_at_line[i]) + " " + lines[i][:50])
else:
    print("  OK")

print("\n" + "=" * 70)
print("SUMMARY")
print("=" * 70)

issues = 0
if total_open != total_close:
    issues += 1
    print("  [!] Brace mismatch: " + str(total_open - total_close))
if final != 0:
    issues += 1
    print("  [!] Final depth: " + str(final))
if negative_lines:
    issues += 1
    print("  [!] Negative depth: " + str(len(negative_lines)) + " lines")
if merged:
    issues += 1
    print("  [!] Merged lines: " + str(len(merged)))

if issues == 0:
    print("\n  VALID FILE")
else:
    print("\n  " + str(issues) + " ISSUE(S) FOUND")

print("=" * 70)

if issues > 0:
    print("\n[7] FIX COMMANDS")    print("-" * 70)
    
    if total_open > total_close:
        diff = total_open - total_close
        print("  echo '" + "}" * diff + "' >> rust/core/src/lib.rs")
    
    if merged:
        print("\n  # Fix merged lines:")
        for ml in merged:
            print("  sed -i '" + str(ml) + "s|.*|FIX LINE " + str(ml) + "|' rust/core/src/lib.rs")
    
    if negative_lines:
        print("\n  # Remove extra } (reverse order):")
        for nl in sorted(negative_lines, reverse=True)[:5]:
            print("  sed -i '" + str(nl) + "d' rust/core/src/lib.rs")
    
    if final > 0:
        print("\n  # Add closing braces:")
        for _ in range(final):
            print('  echo "}" >> rust/core/src/lib.rs')

print("\n  # Verify:")
print('  python3 -c "c=open(\'rust/core/src/lib.rs\').read(); print(c.count(\'{\')-c.count(\'}}\'))"')
