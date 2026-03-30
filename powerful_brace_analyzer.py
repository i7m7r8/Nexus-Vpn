#!/usr/bin/env python3
"""
POWERFUL Brace Balance Analyzer - Finds EXACT structural issues
"""
from pathlib import Path
import re

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()
lines = content.split('\n')

print("="*70)
print("POWERFUL BRACE BALANCE ANALYZER")
print("="*70)

# ============================================================================
# PART 1: Track brace depth line by line
# ============================================================================
print("\n" + "="*70)
print("PART 1: Brace Depth Analysis")
print("="*70)

depth = 0
max_depth = 0
depth_history = []

for i, line in enumerate(lines):
    opens = line.count('{')
    closes = line.count('}')
    depth += opens - closes
    depth_history.append(depth)
    if depth > max_depth:
        max_depth = depth

print(f"\nTotal lines: {len(lines)}")
print(f"Max brace depth: {max_depth}")
print(f"Final brace depth: {depth}")
print(f"Total {{ : {content.count('{')}")
print(f"Total }} : {content.count('}')}")

if depth != 0:
    print(f"\n❌ IMBALANCE: {depth} unclosed brace(s)")
else:
    print(f"\n✅ Brace COUNT is balanced")

# ============================================================================
# PART 2: Find ALL code blocks and their brace balance
# ============================================================================
print("\n" + "="*70)print("PART 2: All Code Blocks Analysis")
print("="*70)

blocks = []
stack = []

for i, line in enumerate(lines):
    # Detect block start (impl, struct, enum, fn, mod, etc.)
    block_match = re.search(r'(pub\s+)?(impl|struct|enum|fn|mod|trait)\s+(\w+)', line)
    if block_match and '{' in line:
        block_type = block_match.group(2)
        block_name = block_match.group(3)
        stack.append((block_type, block_name, i+1, depth_history[i]))
    
    # Detect block end
    if line.strip() == '}' and stack:
        block_type, block_name, start_line, start_depth = stack.pop()
        end_depth = depth_history[i]
        blocks.append({
            'type': block_type,
            'name': block_name,
            'start': start_line,
            'end': i+1,
            'start_depth': start_depth,
            'end_depth': end_depth
        })

# Show blocks with issues
print("\nBlocks with potential issues:")
print("-"*70)

issue_count = 0
for block in blocks[-30:]:  # Last 30 blocks
    # Check if block properly closed
    if block['end_depth'] != block['start_depth'] - 1:
        print(f"❌ {block['type']} {block['name']}: lines {block['start']}-{block['end']}")
        print(f"   Start depth: {block['start_depth']}, End depth: {block['end_depth']}")
        issue_count += 1

if issue_count == 0:
    print("✅ All blocks appear properly closed")

# ============================================================================
# PART 3: Check each major section
# ============================================================================
print("\n" + "="*70)
print("PART 3: Major Section Analysis")
print("="*70)

sections = [    ("EncryptionEngine", "impl EncryptionEngine", "// === SNI HANDLER"),
    ("SniHandler", "impl SniHandler", "// === TOR MANAGER"),
    ("TorManager", "impl TorManager", "// === VPN CONNECTION"),
    ("VpnConnection", "impl VpnConnection", "// === VPN ENGINE"),
    ("VpnEngine", "impl VpnEngine", "// === CONNECTION POOL"),
    ("ConnectionPool", "impl ConnectionPool", "// === DNS PRIVACY"),
    ("DnsPrivacyEngine", "impl DnsPrivacyEngine", "// === LEAK PREVENTION"),
    ("LeakPreventionEngine", "impl LeakPreventionEngine", "// === BATTERY"),
    ("BatteryOptimizer", "impl BatteryOptimizer", "// === NEXUS VPN ENGINE"),
    ("NexusVpnEngine", "impl NexusVpnEngine", "// === JNI EXPORTS"),
    ("JNI Exports", "// === JNI EXPORTS", "// === INITIALIZATION"),
    ("Tests", "#[cfg(test)]", "END OF NEXUS"),
]

for name, start_marker, end_marker in sections:
    start_idx = content.find(start_marker)
    end_idx = content.find(end_marker, start_idx)
    
    if start_idx != -1:
        if end_idx == -1:
            end_idx = len(content)
        
        section = content[start_idx:end_idx]
        open_b = section.count('{')
        close_b = section.count('}')
        balance = open_b - close_b
        
        status = "❌" if balance != 0 else "✅"
        print(f"{status} {name}: open={open_b}, close={close_b}, balance={balance}")

# ============================================================================
# PART 4: Find exact location of structural issue
# ============================================================================
print("\n" + "="*70)
print("PART 4: Finding Structural Issues")
print("="*70)

# Look for places where depth goes negative (extra closing brace)
print("\nChecking for extra closing braces (depth < 0):")
negative_lines = []
current_depth = 0
for i, line in enumerate(lines):
    current_depth += line.count('{') - line.count('}')
    if current_depth < 0:
        negative_lines.append((i+1, current_depth, line.strip()[:60]))
        if len(negative_lines) >= 5:
            break

if negative_lines:
    for line_num, depth, content in negative_lines:        print(f"  ❌ Line {line_num}: depth={depth} | {content}")
else:
    print("  ✅ No extra closing braces found")

# Look for places where depth stays positive at end
print("\nChecking for missing closing braces (depth > 0 at end):")
if depth > 0:
    # Find last lines where depth > 0
    for i in range(len(lines)-1, max(0, len(lines)-50), -1):
        if depth_history[i] > 0:
            print(f"  Line {i+1}: depth={depth_history[i]} | {lines[i][:60]}")
            if depth_history[i] == depth:
                print(f"  ⚠️  This is where the missing }} should be added")
                break

# ============================================================================
# PART 5: Check for common patterns that cause issues
# ============================================================================
print("\n" + "="*70)
print("PART 5: Common Pattern Issues")
print("="*70)

patterns = [
    ("Double closing brace", r'\n\}\s*\n\s*\}', 'Possible duplicate }'),
    ("Empty impl block", r'impl\s+\w+\s*\{\s*\}', 'Empty impl block'),
    ("Mismatched if/else", r'if\s*\(.*\)\s*\{[^}]*else', 'Check if/else braces'),
]

for name, pattern, description in patterns:
    matches = re.findall(pattern, content, re.MULTILINE)
    if matches:
        print(f"⚠️  {name}: {len(matches)} occurrence(s) - {description}")
    else:
        print(f"✅ {name}: No issues")

# ============================================================================
# PART 6: Recommendations
# ============================================================================
print("\n" + "="*70)
print("PART 6: Recommendations")
print("="*70)

if depth == 0:
    print("\n✅ Brace count is balanced!")
    print("If compiler still errors, check:")
    print("  1. Are all impl blocks properly closed?")
    print("  2. Are all match/if/for blocks properly closed?")
    print("  3. Is there a } closing the wrong block?")
else:
    print(f"\n❌ File has {depth} unclosed brace(s)")    if depth > 0:
        print(f"   Action: Add {depth} closing }} at appropriate location")
    else:
        print(f"   Action: Remove {abs(depth)} extra }} from file")

print("\n" + "="*70)
