#!/usr/bin/env python3
"""
GUARANTEED Fix - Reads file and shows what it finds
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("ANALYZING FILE CONTENT")
print("="*60)

# Show what we're looking for
print("\n1. Searching for TorClientConfig in TorManager...")
if "TorClientConfig" in content:
    print("   ✅ Found TorClientConfig")
    # Show context
    import re
    matches = re.findall(r'TorClient<[^>]+>', content)
    print(f"   Found: {matches}")
else:
    print("   ❌ TorClientConfig not found")

print("\n2. Searching for with_config...")
if ".with_config" in content:
    print("   ✅ Found .with_config")
else:
    print("   ❌ .with_config not found")

print("\n3. Searching for ConfigParts...")
if "ConfigParts" in content:
    print("   ✅ Found ConfigParts")
else:
    print("   ❌ ConfigParts not found")

print("\n4. Searching for orphaned .connect_tcp...")
if re.search(r'\n\s+\.connect_tcp', content):
    print("   ✅ Found orphaned .connect_tcp")
else:
    print("   ❌ Orphaned .connect_tcp not found")

print("\n" + "="*60)
print("NOW APPLYING FIXES...")
print("="*60)

# Apply fixes
fixes = 0

# Fix 1: TorClientConfig → PreferredRuntime
if "TorClientConfig" in content:
    content = content.replace("TorClientConfig", "tor_rtcompat::PreferredRuntime")
    print("✅ Fixed TorClientConfig → PreferredRuntime")
    fixes += 1

# Fix 2: with_config → config
if ".with_config" in content:
    content = content.replace(".with_config(", ".config(")
    print("✅ Fixed .with_config() → .config()")
    fixes += 1

# Fix 3: ConfigParts → Config
if "ConfigParts" in content:
    content = content.replace("ConfigParts", "Config")
    print("✅ Fixed ConfigParts → Config")
    fixes += 1

# Fix 4: Add with_runtime before .config(
if ".config(arti_client::config::Config::default())" in content:
    content = content.replace(
        ".config(arti_client::config::Config::default())",
        ".with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config(arti_client::config::Config::default())"
    )
    print("✅ Added .with_runtime()")
    fixes += 1

# Fix 5: Remove arti_config variable
if "let arti_config = config.to_arti();" in content:
    content = content.replace("let arti_config = config.to_arti();\n        ", "")
    print("✅ Removed arti_config variable")
    fixes += 1

# Fix 6: Fix orphaned .connect_tcp
content = content.replace(
    "\n                    .connect_tcp(addr, port)",
    "\n                let arti_stream = client\n                    .connect_tcp(addr, port)"
)
print("✅ Fixed orphaned .connect_tcp")
fixes += 1

# WRITE FILE
lib_path.write_text(content)

print(f"\n✅ Applied {fixes} fixes!")
print("="*60)
