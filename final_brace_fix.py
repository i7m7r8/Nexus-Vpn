#!/usr/bin/env python3
from pathlib import Path

file_path = Path("rust/core/src/lib.rs")
with open(file_path, 'r') as f:
    lines = f.read().splitlines()

# Count braces
brace_count = 0
for line in lines:
    brace_count += line.count('{') - line.count('}')

print(f"Current brace count: {brace_count}")

if brace_count > 0:
    # Add missing closing braces
    for _ in range(brace_count):
        lines.append('}')
    print(f"Added {brace_count} closing brace(s).")
elif brace_count < 0:
    # Remove extra closing braces from the end
    for _ in range(-brace_count):
        if lines and lines[-1].strip() == '}':
            lines.pop()
    print(f"Removed {-brace_count} extra closing brace(s).")
else:
    print("Brace balance is already correct.")

# Write back
with open(file_path, 'w') as f:
    f.write('\n'.join(lines))
print("Done.")
