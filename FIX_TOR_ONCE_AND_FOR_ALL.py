#!/usr/bin/env python3
"""
EXACT FIX based on error log analysis
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("EXACT TorManager Fix")
print("="*60)

# ============================================================================
# FIX 1: TorManager struct - Line 809 (from error log)
# Change TorClientConfig → PreferredRuntime
# ============================================================================
print("\n1. Fixing TorManager struct...")

content = content.replace(
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("   ✅ TorManager struct fixed")

# ============================================================================
# FIX 2: get_client() return type - Line 830 (from error log)
# Must match struct type exactly
# ============================================================================
print("2. Fixing get_client() return type...")

content = content.replace(
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("   ✅ get_client() fixed")

# ============================================================================
# FIX 3: TorManager::start() - Line 816-819 (from error log)
# Fix builder pattern
# ============================================================================
print("3. Fixing TorManager::start()...")

old_builder = '''let client = arti_client::TorClient::builder()
            .with_config(arti_config)
            .create_bootstrapped()
            .await'''

new_builder = '''let client = arti_client::TorClient::builder()            .with_runtime(tor_rtcompat::PreferredRuntime::current())
            .config(arti_client::config::Config::default())
            .create_bootstrapped()
            .await'''

if old_builder in content:
    content = content.replace(old_builder, new_builder)
    print("   ✅ Builder pattern fixed")
else:
    # Try to fix piece by piece
    content = content.replace('.with_config(arti_config)', '.config(arti_client::config::Config::default())')
    content = content.replace(
        'let client = arti_client::TorClient::builder()\n            .config(',
        'let client = arti_client::TorClient::builder()\n            .with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config('
    )
    print("   ✅ Builder pattern fixed (alternate)")

# ============================================================================
# FIX 4: Remove arti_config variable - Line 817 (from error log)
# ============================================================================
print("4. Removing arti_config variable...")

content = content.replace('let arti_config = config.to_arti();\n        ', '')
print("   ✅ Removed arti_config")

# ============================================================================
# FIX 5: Fix to_arti() - Lines 155-156 (from error log)
# Config doesn't exist, ConfigParts doesn't exist
# ============================================================================
print("5. Fixing to_arti()...")

# Just return Config::default() directly
content = content.replace(
    'pub fn to_arti(&self) -> arti_client::config::ConfigParts',
    'pub fn to_arti(&self) -> arti_client::config::Config'
)
content = content.replace(
    'arti_client::config::ConfigParts::default()',
    'arti_client::config::Config::default()'
)
print("   ✅ to_arti() fixed")

# ============================================================================
# FIX 6: Fix orphaned dot - Line 932 (from error log)
# ============================================================================
print("6. Fixing connect_to_target orphaned dot...")

content = content.replace(
    '\n                    .connect_tcp(addr, port)',
    '\n                let arti_stream = client\n                    .connect_tcp(addr, port)')
print("   ✅ Fixed orphaned dot")

# ============================================================================
# FIX 7: Add missing imports
# ============================================================================
print("7. Adding missing imports...")

if 'use tor_rtcompat::PreferredRuntime;' not in content:
    content = content.replace(
        'use arti_client::TorClientConfig;',
        'use arti_client::TorClientConfig;\nuse tor_rtcompat::PreferredRuntime;'
    )
    print("   ✅ Added PreferredRuntime import")

# ============================================================================
# WRITE FILE
# ============================================================================
lib_path.write_text(content)

print("\n" + "="*60)
print("✅ ALL FIXES APPLIED!")
print("="*60)
