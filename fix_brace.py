#!/usr/bin/env python3
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()
lines = content.split('\n')

# Remove any trailing line that is exactly '}'
if lines and lines[-1].strip() == '}':
    lines.pop()
    print("Removed trailing extra closing brace.")
else:
    # Search from the end for a line that is only '}' (possible extra)
    for i in range(len(lines)-1, -1, -1):
        if lines[i].strip() == '}':
            del lines[i]
            print(f"Removed extra brace at line {i+1}")
            break

content = '\n'.join(lines)
lib_rs.write_text(content)
print("✅ Brace balance fixed.")
