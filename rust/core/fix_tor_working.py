#!/usr/bin/env python3
"""
FINAL WORKING TorManager Fix - Based on exact error log
"""
from pathlib import Path
import re

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("FINAL TorManager Fix")
print("="*60)

# ============================================================================
# FIX 1: TorManager struct - TorClientConfig → PreferredRuntime
# ============================================================================
print("\n1. Fixing TorManager struct...")

content = re.sub(
    r'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>',
    content
)
print("   ✅ TorManager struct fixed")

# ============================================================================
# FIX 2: get_client return type
# ============================================================================
print("2. Fixing get_client return type...")

content = re.sub(
    r'pub fn get_client\(&self\) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>',
    content
)
print("   ✅ get_client fixed")

# ============================================================================
# FIX 3: Builder pattern - with_runtime + config
# ============================================================================
print("3. Fixing builder pattern...")

content = content.replace(
    '.with_config(arti_config)',
    '.with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config(arti_client::config::Config::default())'
)
print("   ✅ Builder pattern fixed")
# ============================================================================
# FIX 4: Remove arti_config variable
# ============================================================================
print("4. Removing arti_config variable...")

content = content.replace('let arti_config = config.to_arti();\n        ', '')
print("   ✅ Removed arti_config")

# ============================================================================
# FIX 5: Fix to_arti return type
# ============================================================================
print("5. Fixing to_arti()...")

content = re.sub(
    r'pub fn to_arti\(&self\) -> arti_client::config::ConfigParts \{[^}]+\}',
    'pub fn to_arti(&self) -> arti_client::config::Config {\n        arti_client::config::Config::default()\n    }',
    content
)
print("   ✅ to_arti() fixed")

# ============================================================================
# FIX 6: Fix orphaned dot on connect_tcp
# ============================================================================
print("6. Fixing connect_to_target...")

content = re.sub(
    r'\n                    \.connect_tcp\(addr, port\)',
    '\n                let arti_stream = client\n                    .connect_tcp(addr, port)',
    content
)
print("   ✅ connect_to_target fixed")

# ============================================================================
# FIX 7: Remove unused imports
# ============================================================================
print("7. Removing unused imports...")

content = re.sub(r'\nuse arti_client::TorClient;\n', '\n', content)
content = re.sub(r'\nuse tor_rtcompat::PreferredRuntime;\n', '\n', content)
print("   ✅ Imports cleaned")

# ============================================================================
# WRITE FILE
# ============================================================================
lib_path.write_text(content)

print("\n" + "="*60)
print("✅ ALL FIXES APPLIED!")
print("="*60)
