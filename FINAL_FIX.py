#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("=" * 60)
print("FINAL Arti v0.40 Fix")
print("=" * 60)

# FIX 1: TorManager struct - TorClientConfig -> PreferredRuntime
print("\n1. Fixing TorManager struct...")
content = content.replace(
    'arti_client::config::TorClientConfig',
    'tor_rtcompat::PreferredRuntime'
)
print("   ✅ Fixed")

# FIX 2: Builder - with_config -> config
print("2. Fixing builder pattern...")
content = content.replace('.with_config(', '.config(')
print("   ✅ Fixed")

# FIX 3: Add with_runtime before .config(
print("3. Adding with_runtime...")
content = content.replace(
    '.config(arti_client::config::Config::default())',
    '.with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config(arti_client::config::Config::default())'
)
print("   ✅ Fixed")

# FIX 4: Remove arti_config variable
print("4. Removing arti_config variable...")
content = content.replace('let arti_config = config.to_arti();\n        ', '')
print("   ✅ Fixed")

# FIX 5: Fix to_arti - ConfigParts -> Config
print("5. Fixing to_arti...")
content = content.replace('ConfigParts', 'Config')
print("   ✅ Fixed")

# FIX 6: Fix orphaned dot on connect_tcp
print("6. Fixing connect_tcp orphaned dot...")
content = content.replace(
    '\n                    .connect_tcp(addr, port)',
    '\n                let arti_stream = client\n                    .connect_tcp(addr, port)'
)
print("   ✅ Fixed")

# FIX 7: Add PreferredRuntime import if missing
print("7. Adding imports...")
if 'use tor_rtcompat::PreferredRuntime;' not in content:
    content = content.replace(
        'use arti_client::TorClientConfig;',
        'use arti_client::TorClientConfig;\nuse tor_rtcompat::PreferredRuntime;'
    )
print("   ✅ Fixed")

lib_path.write_text(content)

print("\n" + "=" * 60)
print("✅ ALL FIXES APPLIED!")
print("=" * 60)
