#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()
lines = content.split('\n')

print("="*60)
print("Smart Brace Fixer")
print("="*60)

# Count braces
total_open = content.count('{')
total_close = content.count('}')
print(f"\nTotal {{ : {total_open}")
print(f"Total }} : {total_close}")
print(f"Balance: {total_open - total_close}")

# Check VpnConnection section (lines 1323-1562)
print("\nChecking VpnConnection impl (1323-1562)...")
vpn_start = 1322
vpn_end = 1561
vpn_lines = lines[vpn_start:vpn_end+1]
vpn_content = '\n'.join(vpn_lines)

vpn_open = vpn_content.count('{')
vpn_close = vpn_content.count('}')
print(f"  Open: {vpn_open}, Close: {vpn_close}, Balance: {vpn_open - vpn_close}")

# Find methods with issues
print("\nChecking methods...")
for i, line in enumerate(vpn_lines):
    if 'pub async fn' in line or 'async fn' in line:
        # Find end of this method
        method_start = i
        depth = 0
        method_end = i
        for j in range(i, min(i+100, len(vpn_lines))):
            depth += vpn_lines[j].count('{') - vpn_lines[j].count('}')
            if depth == 0 and '{' in '\n'.join(vpn_lines[i:j+1]):
                method_end = j
                break
        
        method_content = '\n'.join(vpn_lines[method_start:method_end+1])
        m_open = method_content.count('{')
        m_close = method_content.count('}')
        
        if m_open != m_close:
            print(f"  Line {vpn_start + i + 1}: {line.strip()[:50]} (balance: {m_open - m_close})")

# Check for merged lines
print("\nChecking for merged lines...")
for i, line in enumerate(lines):
    if '///' in line and 'pub ' in line:
        if '///' in line.split('pub')[0]:
            print(f"  Line {i+1}: {line[:60]}")

# Check last 100 lines
print("\nLast 100 lines depth:")
depth = 0
depth_at_line = []
for line in lines:
    depth += line.count('{') - line.count('}')
    depth_at_line.append(depth)

for i in range(max(0, len(lines)-100), len(lines)):
    if depth_at_line[i] != 0:
        print(f"  Line {i+1}: depth={depth_at_line[i]} | {lines[i][:50]}")

print("\n" + "="*60)
if total_open == total_close:
    print("✅ BRACE COUNT BALANCED")
else:
    print(f"❌ MISSING {total_open - total_close} closing brace(s)")
print("="*60)
