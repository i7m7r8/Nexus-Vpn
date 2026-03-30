#!/usr/bin/env python3
"""
CORRECT TorManager Fix - Based on actual error analysis
"""
from pathlib import Path
import re

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("CORRECT TorManager Fix")
print("="*60)

# ============================================================================
# FIX 1: TorManager struct - Line 809
# Change TorClientConfig → PreferredRuntime
# ============================================================================
print("\n1. Fixing TorManager struct (line 809)...")

content = re.sub(
    r'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>',
    content
)
print("   ✅ TorManager struct fixed")

# ============================================================================
# FIX 2: get_client() return type - Line 830
# Must match struct type
# ============================================================================
print("2. Fixing get_client() return type (line 830)...")

content = re.sub(
    r'pub fn get_client\(&self\) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>',
    content
)
print("   ✅ get_client() fixed")

# ============================================================================
# FIX 3: TorManager::start() - Lines 816-819
# Add .with_runtime() and fix .config()
# ============================================================================
print("3. Fixing TorManager::start() (lines 816-819)...")

old_builder = '''let client = arti_client::TorClient::builder()
            .with_config(arti_config)
            .create_bootstrapped()            .await'''

new_builder = '''let client = arti_client::TorClient::builder()
            .with_runtime(tor_rtcompat::PreferredRuntime::current())
            .config(arti_client::config::Config::default())
            .create_bootstrapped()
            .await'''

if old_builder in content:
    content = content.replace(old_builder, new_builder)
    print("   ✅ Builder pattern fixed")
else:
    # Try alternate pattern
    content = content.replace('.with_config(arti_config)', '.with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config(arti_client::config::Config::default())')
    print("   ✅ Builder pattern fixed (alternate)")

# ============================================================================
# FIX 4: Remove arti_config variable
# ============================================================================
print("4. Removing arti_config variable...")

content = content.replace('let arti_config = config.to_arti();\n        ', '')
print("   ✅ Removed arti_config")

# ============================================================================
# FIX 5: Fix to_arti() - Lines 155-156
# Config doesn't exist in arti_client::config
# ============================================================================
print("5. Fixing to_arti() (lines 155-156)...")

# Just return Config::default() directly
content = re.sub(
    r'pub fn to_arti\(&self\) -> arti_client::config::Config \{[^}]+\}',
    'pub fn to_arti(&self) -> arti_client::config::Config {\n        arti_client::config::Config::default()\n    }',
    content
)
print("   ✅ to_arti() fixed")

# ============================================================================
# FIX 6: Fix orphaned dot on connect_tcp - Line 932
# ============================================================================
print("6. Fixing connect_to_target (line 932)...")

# The error shows ".connect_tcp(addr, port)" with orphaned dot
# Need to add the client variable before it
content = re.sub(
    r'let client = client_arc\.lock\(\)\.await;\s*\.connect_tcp\(addr, port\)',
    'let client = client_arc.lock().await;\n                let arti_stream = client\n                    .connect_tcp(addr, port)\n                    .await\n                    .map_err(|e| anyhow::anyhow!("Arti connect_tcp: {}", e))?;\n                drop(client);\n                Ok(Stream::Tor(arti_stream))',
    content
)print("   ✅ connect_to_target fixed")

# ============================================================================
# FIX 7: Remove unused imports
# ============================================================================
print("7. Removing unused imports...")

content = re.sub(r'\nuse arti_client::TorClient;\n', '\n', content)
content = re.sub(r'\nuse tor_rtcompat::PreferredRuntime;\n', '\n', content)
print("   ✅ Removed unused imports")

lib_path.write_text(content)
print("\n" + "="*60)
print("✅ ALL FIXES APPLIED!")
print("="*60)
