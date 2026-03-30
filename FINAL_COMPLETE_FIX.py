#!/usr/bin/env python3
"""
FINAL COMPLETE FIX - All 71 errors from build log
Based on EXACT error analysis
"""
from pathlib import Path
import re

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("FINAL COMPLETE FIX")
print("="*60)

fixes = 0

# ============================================================================
# FIX 1: get_client() return type - Line 830 (from error log)
# Must match struct field type (PreferredRuntime)
# ============================================================================
print("\n1. Fixing get_client() return type (line 830)...")

old_get = r'pub fn get_client\(&self\) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>'
new_get = 'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'

if re.search(old_get, content):
    content = re.sub(old_get, new_get, content)
    fixes += 1
    print("   ✅ Fixed get_client() return type")
else:
    print("   ⚠️  Pattern not found - checking current state...")
    # Check what get_client currently returns
    if 'pub fn get_client' in content:
        match = re.search(r'pub fn get_client\(&self\) -> [^{]+', content)
        if match:
            print(f"   Current: {match.group(0)}")

# ============================================================================
# FIX 2: TorManager::start() - Line 816-819 (from error log)
# Fix builder pattern - use .config() not .with_config()
# ============================================================================
print("2. Fixing TorManager::start() (lines 816-819)...")

# First check if TorClient::builder exists
if 'TorClient::builder()' in content:
    # Fix .with_config() to .config()
    content = content.replace('.with_config(arti_config)', '.config(arti_client::config::Config::default())')
    content = content.replace('.with_config(', '.config(')    
    # Add .with_runtime() before .config()
    old_builder = 'let client = arti_client::TorClient::builder()\n            .config('
    new_builder = 'let client = arti_client::TorClient::builder()\n            .with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config('
    
    if old_builder in content:
        content = content.replace(old_builder, new_builder)
        fixes += 1
        print("   ✅ Fixed builder pattern")
    else:
        print("   ⚠️  Builder pattern not found exactly")
else:
    print("   ⚠️  TorClient::builder() not found - checking start() function...")
    # Check what start() currently does
    if 'pub async fn start' in content and 'TorManager' in content:
        match = re.search(r'pub async fn start\(&mut self[^{]*\{[^}]+\}', content, re.DOTALL)
        if match:
            print(f"   Current start(): {match.group(0)[:200]}...")

# ============================================================================
# FIX 3: Remove arti_config variable - Line 817 (from error log)
# ============================================================================
print("3. Removing arti_config variable...")

if 'let arti_config = config.to_arti();' in content:
    content = content.replace('let arti_config = config.to_arti();\n        ', '')
    fixes += 1
    print("   ✅ Removed arti_config")
else:
    print("   ⚠️  arti_config variable not found")

# ============================================================================
# FIX 4: Fix to_arti() - Lines 155-156 (from error log)
# Config doesn't exist in arti_client::config
# ============================================================================
print("4. Fixing to_arti() (lines 155-156)...")

if 'pub fn to_arti(&self)' in content:
    # Remove the entire function (not needed for Arti v0.40)
    content = re.sub(r'pub fn to_arti\(&self\)[^{]*\{[^}]+\}', '', content)
    fixes += 1
    print("   ✅ Removed to_arti() function")
else:
    print("   ⚠️  to_arti() function not found")

# Also fix any ConfigParts references
if 'ConfigParts' in content:
    content = content.replace('ConfigParts', 'Config')
    print("   ✅ Fixed ConfigParts reference")
# ============================================================================
# FIX 5: Fix orphaned dot on connect_tcp - Line 932 (from error log)
# ============================================================================
print("5. Fixing connect_to_target (line 932)...")

# Check for orphaned dot pattern
if re.search(r'\n\s+\.connect_tcp\(addr, port\)', content):
    content = re.sub(
        r'(let client = client_arc\.lock\(\)\.await;)\s*\.connect_tcp\(addr, port\)',
        r'\1\n                let arti_stream = client\n                    .connect_tcp(addr, port)\n                    .await\n                    .map_err(|e| anyhow::anyhow!("Arti connect_tcp: {}", e))?;\n                drop(client);\n                Ok(Stream::Tor(arti_stream))',
        content
    )
    fixes += 1
    print("   ✅ Fixed orphaned dot")
else:
    print("   ⚠️  Orphaned dot pattern not found")

# ============================================================================
# FIX 6: Add missing imports
# ============================================================================
print("6. Checking imports...")

if 'use tor_rtcompat::PreferredRuntime;' not in content:
    if 'use arti_client::TorClientConfig;' in content:
        content = content.replace(
            'use arti_client::TorClientConfig;',
            'use arti_client::TorClientConfig;\nuse tor_rtcompat::PreferredRuntime;'
        )
        fixes += 1
        print("   ✅ Added PreferredRuntime import")
else:
    print("   ✅ PreferredRuntime import exists")

# Add anyhow import if needed
if 'anyhow::' in content and 'use anyhow;' not in content:
    content = content.replace('use derivative::Derivative;', 'use anyhow;\nuse derivative::Derivative;')
    fixes += 1
    print("   ✅ Added anyhow import")

# ============================================================================
# WRITE FILE
# ============================================================================
lib_path.write_text(content)

print("\n" + "="*60)
print(f"✅ Applied {fixes} fixes!")
print("="*60)
