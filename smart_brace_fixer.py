#!/usr/bin/env python3
"""
SMART Brace Balance Fixer - Analyzes and fixes ALL brace issues
"""
from pathlib import Path
import re

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()
lines = content.split('\n')

print("="*70)
print("SMART BRACE BALANCE FIXER")
print("="*70)

# ============================================================================
# PART 1: Track brace depth for every line
# ============================================================================
print("\n[1/6] Analyzing brace depth...")

depth = 0
depth_at_line = []
max_depth = 0

for line in lines:
    depth += line.count('{') - line.count('}')
    depth_at_line.append(depth)
    if depth > max_depth:
        max_depth = max_depth

final_depth = depth
print(f"  Total lines: {len(lines)}")
print(f"  Max depth: {max_depth}")
print(f"  Final depth: {final_depth}")

# ============================================================================
# PART 2: Find ALL impl/struct/enum/fn blocks and check balance
# ============================================================================
print("\n[2/6] Checking all code blocks...")

blocks = []
stack = []

for i, line in enumerate(lines):
    # Detect block start
    match = re.search(r'(pub\s+)?(impl|struct|enum|fn|mod|trait)\s+(\w+)', line)
    if match and '{' in line:
        block_type = match.group(2)
        block_name = match.group(3)        stack.append((block_type, block_name, i+1, depth_at_line[i]))
    
    # Detect block end
    if line.strip() == '}' and stack:
        block_type, block_name, start_line, start_depth = stack.pop()
        end_depth = depth_at_line[i]
        blocks.append({
            'type': block_type,
            'name': block_name,
            'start': start_line,
            'end': i+1,
            'start_depth': start_depth,
            'end_depth': end_depth
        })

# Find blocks with issues
issue_blocks = []
for block in blocks:
    expected_end_depth = block['start_depth'] - 1
    if block['end_depth'] != expected_end_depth:
        issue_blocks.append(block)
        print(f"  ❌ {block['type']} {block['name']}: lines {block['start']}-{block['end']}")
        print(f"      Start depth: {block['start_depth']}, End depth: {block['end_depth']}")

if not issue_blocks:
    print("  ✅ All blocks properly closed")

# ============================================================================
# PART 3: Check VpnConnection specifically (current error)
# ============================================================================
print("\n[3/6] Checking VpnConnection impl (lines 1323-1562)...")

vpn_start = 1322  # 0-indexed
vpn_end = 1561    # 0-indexed

vpn_lines = lines[vpn_start:vpn_end+1]
vpn_content = '\n'.join(vpn_lines)

vpn_open = vpn_content.count('{')
vpn_close = vpn_content.count('}')
vpn_balance = vpn_open - vpn_close

print(f"  Open braces: {vpn_open}")
print(f"  Close braces: {vpn_close}")
print(f"  Balance: {vpn_balance}")

if vpn_balance != 0:
    print(f"  ❌ VpnConnection has {vpn_balance} extra '{{' (missing {vpn_balance} '}}')")
else:
    print(f"  ✅ VpnConnection brace count is balanced")
# ============================================================================
# PART 4: Find methods inside VpnConnection and check each
# ============================================================================
print("\n[4/6] Checking methods inside VpnConnection...")

method_starts = []
for i, line in enumerate(vpn_lines):
    if 'pub async fn ' in line or '    async fn ' in line or '    fn ' in line:
        if '{' in line or (i+1 < len(vpn_lines) and '{' in vpn_lines[i+1]):
            method_starts.append(i)

print(f"  Found {len(method_starts)} methods")

method_issues = []
for idx in range(len(method_starts)):
    start = method_starts[idx]
    end = method_starts[idx + 1] if idx + 1 < len(method_starts) else len(vpn_lines)
    
    method_lines = vpn_lines[start:end]
    method_content = '\n'.join(method_lines)
    
    m_open = method_content.count('{')
    m_close = method_content.count('}')
    m_balance = m_open - m_close
    
    if m_balance != 0:
        method_name = vpn_lines[start].strip()[:50]
        method_issues.append((vpn_start + start + 1, method_name, m_balance))
        print(f"  ❌ Line {vpn_start + start + 1}: {method_name} (balance: {m_balance})")

if not method_issues:
    print("  ✅ All methods balanced")

# ============================================================================
# PART 5: Find and fix common issues
# ============================================================================
print("\n[5/6] Looking for common issues...")

fixes_applied = 0

# Issue 1: Two statements on same line (like we found before)
print("  Checking for merged lines...")
for i, line in enumerate(lines):
    if '///' in line and 'pub ' in line and line.count('pub') > 0:
        if '///' in line.split('pub')[0]:
            print(f"    Found merged line at {i+1}: {line[:60]}")
            # Fix it
            parts = line.split('///')
            if len(parts) > 1:                comment = '///' + parts[1].split('pub')[0]
                func = 'pub' + parts[1].split('pub')[1]
                lines[i] = comment
                lines.insert(i+1, func)
                fixes_applied += 1
                print(f"    ✅ Fixed line {i+1}")

# Issue 2: Extra } at end of impl blocks
print("  Checking for extra closing braces...")
for i in range(len(lines)-1, 0, -1):
    if lines[i].strip() == '}' and lines[i-1].strip() == '}':
        # Check if this might be duplicate
        if i < len(depth_at_line) and depth_at_line[i] < 0:
            print(f"    Found possible extra }} at line {i+1}")

# Issue 3: Missing newline before }
print("  Checking for missing newlines...")

# ============================================================================
# PART 6: Write fixes and verify
# ============================================================================
print("\n[6/6] Writing fixes and verifying...")

new_content = '\n'.join(lines)
lib_path.write_text(new_content)

final_open = new_content.count('{')
final_close = new_content.count('}')
final_balance = final_open - final_close

print(f"  Total {{ : {final_open}")
print(f"  Total }} : {final_close}")
print(f"  Balance: {final_balance}")

if final_balance == 0:
    print("\n" + "="*70)
    print("✅ FILE IS BALANCED!")
    print("="*70)
else:
    print(f"\n⚠️  Still imbalanced by {final_balance}")
    print("  Manual review needed")

# Show summary
print("\n" + "="*70)
print("SUMMARY")
print("="*70)
print(f"  Fixes applied: {fixes_applied}")
print(f"  Issue blocks found: {len(issue_blocks)}")
print(f"  Method issues found: {len(method_issues)}")
print(f"  Final balance: {final_balance}")print("="*70)
