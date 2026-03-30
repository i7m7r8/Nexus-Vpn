#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()
lines = content.split('\n')

print("="*60)
print("EXACT Missing Brace Location")
print("="*60)

# Track brace depth line by line
depth = 0
max_depth = 0
depth_at_line = []

for i, line in enumerate(lines):
    opens = line.count('{')
    closes = line.count('}')
    depth += opens - closes
    depth_at_line.append(depth)
    if depth > max_depth:
        max_depth = depth

print(f"\nMax brace depth: {max_depth}")
print(f"Final brace depth: {depth}")
print(f"Missing braces: {depth}")

# Find where depth should return to 0 but doesn't
print("\n" + "="*60)
print("Checking end of each major block...")
print("="*60)

# Find all major blocks and check if they close properly
blocks = []
current_block = None
block_start = 0
block_depth = 0

for i, line in enumerate(lines):
    # Detect block starts
    if any(x in line for x in ['impl ', 'pub struct ', 'pub enum ', 'pub fn ', 'mod ', 'fn ']):
        if '{' in line:
            if current_block:
                blocks.append((current_block, block_start, i-1, block_depth))
            current_block = line.strip()[:60]
            block_start = i
            block_depth = depth_at_line[i]

# Check last 100 lines for issues
print("\n" + "="*60)
print("Last 100 lines brace depth:")
print("="*60)

start = max(0, len(lines) - 100)
for i in range(start, len(lines)):
    if depth_at_line[i] != 0:
        print(f"Line {i+1}: depth={depth_at_line[i]} | {lines[i][:70]}")

# Find specific location where depth should be 0
print("\n" + "="*60)
print("Lines where depth != 0 (should all be 0 at end):")
print("="*60)

for i in range(len(lines)-1, -1, -1):
    if depth_at_line[i] > 0:
        print(f"\nLine {i+1}: depth={depth_at_line[i]}")
        print(f"Content: {lines[i]}")
        if i < len(lines) - 1:
            print(f"Next: {lines[i+1]}")
        break

print("\n" + "="*60)
print("SOLUTION:")
print("="*60)
print("Add } after the line shown above to close the open block")
