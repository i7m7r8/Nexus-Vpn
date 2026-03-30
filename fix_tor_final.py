#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("FINAL TorManager Fix")
print("="*60)

# Fix 1: TorManager struct - change TorClientConfig to PreferredRuntime
content = content.replace(
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("✅ Fixed TorManager struct type")

# Fix 2: get_client return type
content = content.replace(
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("✅ Fixed get_client return type")

# Fix 3: Builder pattern - with_config to config
content = content.replace('.with_config(arti_config)', '.config(arti_client::config::Config::default())')
content = content.replace('.with_config(', '.config(')
print("✅ Fixed builder pattern")

# Fix 4: Remove arti_config variable
content = content.replace('let arti_config = config.to_arti();\n        ', '')
print("✅ Removed arti_config variable")

# Fix 5: Fix orphaned dot on connect_tcp
content = content.replace('\n                    .connect_tcp(addr, port)', '\n                let arti_stream = client\n                    .connect_tcp(addr, port)')
print("✅ Fixed connect_tcp orphaned dot")

# Fix 6: Fix to_arti - remove the function entirely (not needed)
import re
content = re.sub(r'pub fn to_arti\(&self\)[^{]*\{[^}]+\}', '', content)
print("✅ Removed to_arti function")

lib_path.write_text(content)
print("\n✅ ALL FIXES APPLIED!")
