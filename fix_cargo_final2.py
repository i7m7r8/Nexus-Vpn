#!/usr/bin/env python3
import re
from pathlib import Path

cargo_toml = Path("rust/core/Cargo.toml")
content = cargo_toml.read_text()

# Remove any rusqlite or libsqlite3-sys entries completely
content = re.sub(r'^rusqlite = .*\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^libsqlite3-sys = .*\n', '', content, flags=re.MULTILINE)

# Remove any arti-client line
content = re.sub(r'^arti-client = .*\n', '', content, flags=re.MULTILINE)

# Add arti-client with no persistent storage features
arti_line = 'arti-client = { version = "0.40.0", default-features = false, features = ["rustls", "tokio"], optional = false }'
content += f'\n{arti_line}\n'

# Add explicit patch to disable rusqlite
patch_line = '\n[patch.crates-io]\nrusqlite = { git = "https://github.com/rusqlite/rusqlite", rev = "main", optional = true, features = ["bundled"] }\n'
content += patch_line

cargo_toml.write_text(content)
print("✅ Fixed Cargo.toml - disabled sqlite dependencies")
