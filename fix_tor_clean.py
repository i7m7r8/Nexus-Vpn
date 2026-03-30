#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("CLEAN TorManager Fix")
print("="*60)

# FIX 1: TorManager struct - TorClientConfig → PreferredRuntime
content = content.replace(
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("1. ✅ Fixed TorManager struct")

# FIX 2: get_client return type
content = content.replace(
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("2. ✅ Fixed get_client return type")

# FIX 3: Builder - with_config → config
content = content.replace('.with_config(arti_config)', '.config(arti_client::config::Config::default())')
print("3. ✅ Fixed builder pattern")

# FIX 4: Add with_runtime
content = content.replace(
    'let client = arti_client::TorClient::builder()',
    'let client = arti_client::TorClient::builder()\n            .with_runtime(tor_rtcompat::PreferredRuntime::current())'
)
print("4. ✅ Added with_runtime")

# FIX 5: Remove arti_config variable
content = content.replace('let arti_config = config.to_arti();\n        ', '')
print("5. ✅ Removed arti_config")

# FIX 6: Fix to_arti
content = content.replace(
    'pub fn to_arti(&self) -> arti_client::config::ConfigParts',
    'pub fn to_arti(&self) -> arti_client::config::Config'
)
content = content.replace(
    'arti_client::config::ConfigParts::default()',
    'arti_client::config::Config::default()'
)
print("6. ✅ Fixed to_arti")

# FIX 7: Fix orphaned dot
content = content.replace(
    '\n                    .connect_tcp(addr, port)',
    '\n                let arti_stream = client\n                    .connect_tcp(addr, port)'
)
print("7. ✅ Fixed connect_tcp")

lib_path.write_text(content)
print("\n" + "="*60)
print("✅ ALL FIXES APPLIED!")
print("="*60)
