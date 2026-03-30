#!/usr/bin/env python3
"""
Fix by searching for ACTUAL patterns, not line numbers
"""
from pathlib import Path
import re

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("FIX BY PATTERN SEARCH")
print("="*60)

fixes = 0

# ============================================================================
# FIX 1: Find and fix TorManager struct (search for pattern)
# ============================================================================
print("\n1. Searching for TorManager struct...")

# Search for TorManager struct with TorClientConfig
if 'pub struct TorManager' in content:
    # Find the struct and fix the generic
    content = re.sub(
        r'(pub struct TorManager \{\s*client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<)arti_client::config::TorClientConfig(>\s*,?\s*\})',
        r'\1tor_rtcompat::PreferredRuntime\2',
        content
    )
    print("   ✅ Fixed TorManager struct generic")
    fixes += 1
else:
    print("   ⚠️  TorManager struct not found")

# ============================================================================
# FIX 2: Fix get_client() return type
# ============================================================================
print("2. Searching for get_client()...")

if 'pub fn get_client(&self)' in content:
    content = re.sub(
        r'(pub fn get_client\(&self\) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<)arti_client::config::TorClientConfig(>\s*\{)',
        r'\1tor_rtcompat::PreferredRuntime\2',
        content
    )
    print("   ✅ Fixed get_client() return type")
    fixes += 1
else:
    print("   ⚠️  get_client() not found")
# ============================================================================
# FIX 3: Fix builder pattern
# ============================================================================
print("3. Searching for TorClient::builder()...")

if 'TorClient::builder()' in content:
    # Fix .with_config() to .config()
    content = content.replace('.with_config(arti_config)', '.config(arti_client::config::Config::default())')
    content = content.replace('.with_config(', '.config(')
    
    # Add .with_runtime() before .config()
    content = re.sub(
        r'(let client = arti_client::TorClient::builder\(\))\s*\.config\(',
        r'\1\n            .with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config(',
        content
    )
    print("   ✅ Fixed builder pattern")
    fixes += 1
else:
    print("   ⚠️  TorClient::builder() not found")

# ============================================================================
# FIX 4: Remove arti_config variable
# ============================================================================
print("4. Searching for arti_config variable...")

if 'let arti_config = config.to_arti();' in content:
    content = content.replace('let arti_config = config.to_arti();\n        ', '')
    print("   ✅ Removed arti_config variable")
    fixes += 1
else:
    print("   ⚠️  arti_config variable not found")

# ============================================================================
# FIX 5: Fix to_arti() return type
# ============================================================================
print("5. Searching for to_arti()...")

if 'pub fn to_arti(&self)' in content:
    content = content.replace(
        'pub fn to_arti(&self) -> arti_client::config::ConfigParts',
        'pub fn to_arti(&self) -> arti_client::config::Config'
    )
    content = content.replace(
        'arti_client::config::ConfigParts::default()',
        'arti_client::config::Config::default()'
    )
    print("   ✅ Fixed to_arti() return type")
    fixes += 1else:
    print("   ⚠️  to_arti() not found")

# ============================================================================
# FIX 6: Fix orphaned dot on connect_tcp
# ============================================================================
print("6. Searching for orphaned .connect_tcp...")

# Search for pattern where .connect_tcp has orphaned dot
if re.search(r'\n\s+\.connect_tcp\(addr, port\)', content):
    content = re.sub(
        r'(let client = client_arc\.lock\(\)\.await;)\s*\.connect_tcp\(addr, port\)',
        r'\1\n                let arti_stream = client\n                    .connect_tcp(addr, port)',
        content
    )
    print("   ✅ Fixed orphaned .connect_tcp")
    fixes += 1
else:
    print("   ⚠️  Orphaned .connect_tcp not found")

# ============================================================================
# FIX 7: Add missing imports
# ============================================================================
print("7. Checking imports...")

if 'use tor_rtcompat::PreferredRuntime;' not in content:
    # Add after arti_client import
    if 'use arti_client::TorClientConfig;' in content:
        content = content.replace(
            'use arti_client::TorClientConfig;',
            'use arti_client::TorClientConfig;\nuse tor_rtcompat::PreferredRuntime;'
        )
        print("   ✅ Added PreferredRuntime import")
        fixes += 1
else:
    print("   ✅ PreferredRuntime import exists")

# ============================================================================
# WRITE FILE
# ============================================================================
lib_path.write_text(content)

print(f"\n{'='*60}")
print(f"✅ Applied {fixes} fixes!")
print(f"{'='*60}")
