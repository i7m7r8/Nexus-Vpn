#!/usr/bin/env python3
import sys
from pathlib import Path

path = Path("android/app/build.gradle")
if not path.exists():
    print(f"Error: {path} not found", file=sys.stderr)
    sys.exit(1)

content = path.read_text()

# Remove the kotlin android plugin line - AGP 9.0+ has it built-in
content = content.replace(
    "    id 'org.jetbrains.kotlin.android'\n",
    ""
)

path.write_text(content)
print("✅ Removed org.jetbrains.kotlin.android plugin")
print("Preview:")
print(content[:200])
