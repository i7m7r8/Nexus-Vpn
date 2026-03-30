#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()
lines = content.split('\n')

print("="*60)
print("Simple Brace Analyzer")
print("="*60)

# Count total braces
total_open = content.count('{')
total_close = content.count('}')
print(f"\nTotal {{ : {total_open}")
print(f"Total }} : {total_close}")
print(f"Balance: {total_open - total_close}")

# Track depth and find issues
depth = 0
issues = []

for i, line in enumerate(lines):
    depth += line.count('{') - line.count('}')
    if depth < 0:
        issues.append((i+1, "EXTRA }", depth, line.strip()[:50]))
    if depth > 10:
        issues.append((i+1, "DEEP NESTING", depth, line.strip()[:50]))

# Check major sections
print("\n" + "="*60)
print("Section Analysis:")
print("="*60)

sections = [
    ("EncryptionEngine", "impl EncryptionEngine", "// === SNI HANDLER"),
    ("SniHandler", "impl SniHandler", "// === TOR MANAGER"),
    ("TorManager", "impl TorManager", "// === VPN CONNECTION"),
    ("VpnEngine", "impl VpnEngine", "// === CONNECTION POOL"),
    ("JNI", "// === JNI EXPORTS", "// === INITIALIZATION"),
    ("Tests", "#[cfg(test)]", "END OF NEXUS"),
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
        status = "❌" if o != c else "✅"
        print(f"{status} {name}: {{={o}, }}={c}, balance={o-c}")

# Show last 50 lines with depth
print("\n" + "="*60)
print("Last 50 Lines (with depth):")
print("="*60)

depth = 0
depth_at_line = []
for line in lines:
    depth += line.count('{') - line.count('}')
    depth_at_line.append(depth)

start = max(0, len(lines) - 50)
for i in range(start, len(lines)):
    if depth_at_line[i] != 0:
        print(f"Line {i+1}: depth={depth_at_line[i]} | {lines[i][:60]}")

# Show any issues found
if issues:
    print("\n" + "="*60)
    print("Issues Found:")
    print("="*60)
    for line_num, issue_type, depth_val, line_content in issues[:10]:
        print(f"Line {line_num}: {issue_type} (depth={depth_val})")
        print(f"  {line_content}")
else:
    print("\n✅ No structural issues detected")

print("\n" + "="*60)
if total_open == total_close:
    print("✅ BRACE COUNT IS BALANCED")
else:
    print(f"❌ MISSING {total_open - total_close} closing brace(s)")
print("="*60)
