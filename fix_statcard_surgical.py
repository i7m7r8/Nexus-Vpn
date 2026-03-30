#!/usr/bin/env python3
import sys
from pathlib import Path

kt_path = Path("android/app/src/main/kotlin/com/nexusvpn/android/MainActivity.kt")
if not kt_path.exists():
    print(f"Error: {kt_path} not found", file=sys.stderr)
    sys.exit(1)

content = kt_path.read_text()
lines = content.splitlines(keepends=True)

print(f"Total lines: {len(lines)}")
# Show lines 745-775 so we know exact state
for i, l in enumerate(lines[743:778], start=744):
    print(f"{i}: {l}", end='')
print()
