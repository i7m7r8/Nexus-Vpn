#!/usr/bin/env python3
from pathlib import Path

cargo_toml = Path("rust/core/Cargo.toml")
content = cargo_toml.read_text()

# Ensure rusqlite uses bundled feature
if 'rusqlite' not in content:
    content += '\nrusqlite = { version = "0.38", features = ["bundled"] }\n'
else:
    # Replace or add bundled if missing
    if 'bundled' not in content:
        content = content.replace('rusqlite = { version = "0.38" }', 'rusqlite = { version = "0.38", features = ["bundled"] }')
        content = content.replace('rusqlite = { version = "0.38",', 'rusqlite = { version = "0.38", features = ["bundled"],')
        # If it's already there but no bundled, add it
        if 'rusqlite = { version = "0.38"' in content and 'bundled' not in content:
            content = content.replace('rusqlite = { version = "0.38" }', 'rusqlite = { version = "0.38", features = ["bundled"] }')
            content = content.replace('rusqlite = { version = "0.38", features = [] }', 'rusqlite = { version = "0.38", features = ["bundled"] }')

# Also ensure libsqlite3-sys uses bundled (optional, but safe)
if 'libsqlite3-sys' not in content:
    content += '\nlibsqlite3-sys = { version = "0.36", features = ["bundled"] }\n'
else:
    if 'bundled' not in content:
        content = content.replace('libsqlite3-sys = { version = "0.36" }', 'libsqlite3-sys = { version = "0.36", features = ["bundled"] }')

cargo_toml.write_text(content)
print("✅ Updated Cargo.toml with bundled sqlite.")
