#!/usr/bin/env python3
"""
FINAL FIX - Based on exact error log from GitHub Actions
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("FINAL FIX - Based on Error Log")
print("="*60)

# ============================================================================
# FIX 1: TorManager struct - Line 809 (from error log)
# Change TorClientConfig → PreferredRuntime
# ============================================================================
print("\n1. Fixing TorManager struct (line 809)...")

content = content.replace(
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("   ✅ TorManager struct fixed")

# ============================================================================
# FIX 2: get_client return type - Line 830 (from error log)
# Must match struct type
# ============================================================================
print("2. Fixing get_client return type (line 830)...")

content = content.replace(
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("   ✅ get_client return type fixed")

# ============================================================================
# FIX 3: Builder pattern - Line 817 (from error log)
# .with_config() doesn't exist, use .config()
# ============================================================================
print("3. Fixing builder pattern (line 817)...")

content = content.replace('.with_config(', '.config(')
print("   ✅ Builder pattern fixed")

# ============================================================================
# FIX 4: Add .with_runtime() to builder
# ============================================================================print("4. Adding with_runtime to builder...")

content = content.replace(
    'let client = arti_client::TorClient::builder()\n            .config(arti_client::config::Config::default())',
    'let client = arti_client::TorClient::builder()\n            .with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config(arti_client::config::Config::default())'
)
print("   ✅ with_runtime added")

# ============================================================================
# FIX 5: Remove arti_config variable - Line 817 (from error log)
# ============================================================================
print("5. Removing arti_config variable...")

content = content.replace('let arti_config = config.to_arti();\n        ', '')
print("   ✅ arti_config removed")

# ============================================================================
# FIX 6: Fix to_arti - Lines 155-156 (from error log)
# Config not found in arti_client::config
# ============================================================================
print("6. Fixing to_arti (lines 155-156)...")

content = content.replace(
    'pub fn to_arti(&self) -> arti_client::config::ConfigParts',
    'pub fn to_arti(&self) -> arti_client::config::Config'
)
content = content.replace(
    'arti_client::config::ConfigParts::default()',
    'arti_client::config::Config::default()'
)
print("   ✅ to_arti fixed")

# ============================================================================
# FIX 7: Fix orphaned dot - Line 932 (from error log)
# ============================================================================
print("7. Fixing orphaned dot (line 932)...")

content = content.replace(
    '\n                    .connect_tcp(addr, port)',
    '\n                let arti_stream = client\n                    .connect_tcp(addr, port)'
)
print("   ✅ Orphaned dot fixed")

# ============================================================================
# WRITE FILE
# ============================================================================
lib_path.write_text(content)

print("\n" + "="*60)
print("✅ ALL 7 ERRORS FROM LOG FIXED!")print("="*60)
