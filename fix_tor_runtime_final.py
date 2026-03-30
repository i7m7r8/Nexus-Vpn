#!/usr/bin/env python3
"""
Fix TorManager - Use PreferredRuntime NOT TorClientConfig
"""
import re
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("FIXING TorManager Runtime Type")
print("="*60)

# Fix 1: TorManager struct - use PreferredRuntime
old_struct = r'client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>'
new_struct = 'client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'

if old_struct in content:
    content = content.replace(old_struct, new_struct)
    print("✅ Fixed TorManager struct type")
else:
    print("⚠️  Could not find old struct type")

# Fix 2: get_client return type
old_get = r'pub fn get_client\(&self\) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>'
new_get = 'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'

if old_get in content:
    content = content.replace(old_get, new_get)
    print("✅ Fixed get_client return type")
else:
    print("⚠️  Could not find old get_client type")

# Fix 3: TorManager::start() - use builder pattern correctly
old_start = '''let client = arti_client::TorClient::builder()
            .create_bootstrapped()
            .await'''

new_start = '''let client = arti_client::TorClient::builder()
            .with_config(arti_config)
            .create_bootstrapped()
            .await'''

if old_start in content:
    content = content.replace(old_start, new_start)
    print("✅ Fixed TorClient::builder() - added .with_config()")
else:
    print("⚠️  Could not find old start pattern")

# Fix 4: TorClientConfig::to_arti() return type
old_to_arti = '''pub fn to_arti(&self) -> arti_client::config::ConfigParts {
        arti_client::config::ConfigParts::default()
    }'''

new_to_arti = '''pub fn to_arti(&self) -> arti_client::config::Config {
        arti_client::config::Config::default()
    }'''

if old_to_arti in content:
    content = content.replace(old_to_arti, new_to_arti)
    print("✅ Fixed to_arti() return type")
else:
    print("⚠️  Could not find old to_arti pattern")

# Fix 5: Add PreferredRuntime import if missing
if 'use tor_rtcompat::PreferredRuntime;' not in content:
    content = content.replace(
        'use tokio::time::sleep;',
        'use tokio::time::sleep;\nuse tor_rtcompat::PreferredRuntime;'
    )
    print("✅ Added PreferredRuntime import")

lib_path.write_text(content)
print("\n✅ All fixes applied!")
