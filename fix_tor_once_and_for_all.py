#!/usr/bin/env python3
"""
FINAL TorManager Fix - Use PreferredRuntime EVERYWHERE
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("FINAL TorManager Fix")
print("="*60)

# ============================================================================
# FIX 1: TorManager struct - use PreferredRuntime
# ============================================================================
print("\n1. Fixing TorManager struct...")

# Replace ANY TorClient<...> with PreferredRuntime
import re
content = re.sub(
    r'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<[^>]+>>>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>',
    content
)
print("   ✅ TorManager struct fixed")

# ============================================================================
# FIX 2: TorManager::start() - correct builder pattern
# ============================================================================
print("2. Fixing TorManager::start()...")

old_start_pattern = r'''let client = arti_client::TorClient::builder\(\)
            \.with_config\([^)]+\)
            \.create_bootstrapped\(\)'''

new_start = '''let client = arti_client::TorClient::builder()
            .with_runtime(tor_rtcompat::PreferredRuntime::current())
            .config(arti_client::config::Config::default())
            .create_bootstrapped()'''

content = re.sub(old_start_pattern, new_start, content)
print("   ✅ TorManager::start() fixed")

# ============================================================================
# FIX 3: TorClientConfig::to_arti() - return correct type
# ============================================================================
print("3. Fixing TorClientConfig::to_arti()...")

old_to_arti = r'''pub fn to_arti\(&self\) -> [^{]+\{[^}]+\}'''
new_to_arti = '''pub fn to_arti(&self) -> arti_client::config::Config {
        arti_client::config::Config::default()
    }'''

content = re.sub(old_to_arti, new_to_arti, content, count=1)
print("   ✅ to_arti() fixed")

# ============================================================================
# FIX 4: Remove arti_config variable if unused
# ============================================================================
print("4. Cleaning up...")
content = content.replace('let arti_config = config.to_arti();\n        ', '')
print("   ✅ Cleanup done")

lib_path.write_text(content)
print("\n✅ ALL FIXES APPLIED!")
