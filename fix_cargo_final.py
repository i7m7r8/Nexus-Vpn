#!/usr/bin/env python3
import re
from pathlib import Path

cargo_toml = Path("rust/core/Cargo.toml")
content = cargo_toml.read_text()

# Remove any rusqlite or libsqlite3-sys entries completely
content = re.sub(r'^rusqlite = .*\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^libsqlite3-sys = .*\n', '', content, flags=re.MULTILINE)

# Ensure arti-client is configured without any persistent-state (no sqlite)
arti_line = 'arti-client = { version = "0.40.0", default-features = false, features = ["rustls", "tokio"] }'
if 'arti-client' in content:
    content = re.sub(r'^arti-client = .*\n', arti_line + '\n', content, flags=re.MULTILINE)
else:
    content += f'\n{arti_line}\n'

# Also ensure tor-rtcompat doesn't bring in sqlite
tor_rtcompat_line = 'tor-rtcompat = { version = "0.40.0", features = ["tokio", "rustls"], default-features = false }'
if 'tor-rtcompat' in content:
    content = re.sub(r'^tor-rtcompat = .*\n', tor_rtcompat_line + '\n', content, flags=re.MULTILINE)
else:
    content += f'\n{tor_rtcompat_line}\n'

cargo_toml.write_text(content)
print("✅ Fixed Cargo.toml - removed sqlite dependencies, arti without persistent-state")
