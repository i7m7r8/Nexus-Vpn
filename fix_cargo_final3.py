#!/usr/bin/env python3
import re
from pathlib import Path

cargo_toml = Path("rust/core/Cargo.toml")
content = cargo_toml.read_text()

# Remove any existing arti-client lines and patches
content = re.sub(r'^arti-client = .*\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^rusqlite = .*\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^libsqlite3-sys = .*\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^\[patch.crates-io\].*?\n(?:.*?\n)*?^rusqlite = .*\n', '', content, flags=re.MULTILINE)

# Add arti-client with all non-persistent features disabled
# We need to include the crate but disable any features that pull in sqlite
arti_line = 'arti-client = { version = "0.40.0", default-features = false, features = ["rustls", "tokio"] }'
content += f'\n{arti_line}\n'

# Also ensure tor-rtcompat is properly configured
tor_rtcompat_line = 'tor-rtcompat = { version = "0.40.0", features = ["tokio", "rustls"], default-features = false }'
if 'tor-rtcompat' in content:
    content = re.sub(r'^tor-rtcompat = .*\n', tor_rtcompat_line + '\n', content, flags=re.MULTILINE)
else:
    content += f'\n{tor_rtcompat_line}\n'

cargo_toml.write_text(content)
print("✅ Fixed Cargo.toml - added arti-client back without sqlite")
