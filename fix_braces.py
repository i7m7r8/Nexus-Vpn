#!/usr/bin/env python3
import sys
from pathlib import Path

path = Path("rust/core/src/lib.rs")
if not path.exists():
    print(f"Error: {path} not found", file=sys.stderr)
    sys.exit(1)

with open(path, "r") as f:
    content = f.read()

opens = content.count('{')
closes = content.count('}')
deficit = opens - closes

print(f"Open: {opens}, Close: {closes}, Deficit: {deficit}")

if deficit > 0:
    # Append missing closing braces at the end of the file
    content = content.rstrip()
    content += "\n" + ("}\n" * deficit)
    with open(path, "w") as f:
        f.write(content)
    print(f"✅ Fixed: appended {deficit} closing brace(s)")
elif deficit < 0:
    # Remove extra closing braces from the end (if they are alone on a line)
    extra = -deficit
    lines = content.splitlines()
    removed = 0
    while lines and lines[-1].strip() == '}':
        lines.pop()
        removed += 1
    if removed > 0:
        content = "\n".join(lines)
        with open(path, "w") as f:
            f.write(content)
        print(f"✅ Fixed: removed {removed} extra closing brace(s)")
    else:
        print("⚠️ Brace mismatch but cannot automatically fix extra braces in middle of file")
else:
    print("✅ Braces already balanced")

# Final verification
with open(path, "r") as f:
    fixed = f.read()
o2 = fixed.count('{')
c2 = fixed.count('}')
print(f"After fix -> Open: {o2}, Close: {c2}, Balance: {o2 - c2}")
