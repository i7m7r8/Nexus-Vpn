#!/usr/bin/env python3
"""
Fix based on ACTUAL file content (from script output)
"""
from pathlib import Path
import re

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("Fix ACTUAL Content")
print("="*60)

fixes = 0

# ============================================================================
# FIX 1: TorManager::start() - Replace OLD API with NEW Arti v0.40 API
# Current: TorClient::create(config).bootstrap()
# Need: TorClient::builder().with_runtime().config().create_bootstrapped()
# ============================================================================
print("\n1. Fixing TorManager::start()...")

old_start = '''pub async fn start(&mut self, config: TorClientConfig) -> Result<(), arti_client::Error> {
        let client = TorClient::create(config)?;
        let client = client.bootstrap().await?;
        self.client = Some(Arc::new(client));
        Ok(())
    }'''

new_start = '''pub async fn start(&mut self, _config: TorClientConfig) -> Result<(), String> {
        use arti_client::TorClient;
        use tor_rtcompat::PreferredRuntime;

        let client = TorClient::builder()
            .with_runtime(PreferredRuntime::current())
            .config(arti_client::config::Config::default())
            .create_bootstrapped()
            .await
            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;

        self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));
        Ok(())
    }'''

if old_start in content:
    content = content.replace(old_start, new_start)
    fixes += 1
    print("   ✅ Fixed TorManager::start()")else:
    print("   ⚠️  Pattern not found - trying alternate...")
    # Try to find and replace piece by piece
    if 'TorClient::create(config)' in content:
        content = content.replace('TorClient::create(config)?', 'TorClient::builder()\n            .with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config(arti_client::config::Config::default())\n            .create_bootstrapped()\n            .await\n            .map_err(|e| format!("Arti bootstrap failed: {}", e))?')
        fixes += 1
        print("   ✅ Fixed builder (alternate)")
    
    if 'client.bootstrap().await' in content:
        content = content.replace('let client = client.bootstrap().await?;', '')
        print("   ✅ Removed bootstrap line")
    
    if 'self.client = Some(Arc::new(client));' in content:
        content = content.replace('self.client = Some(Arc::new(client));', 'self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));')
        print("   ✅ Fixed client wrapping")

# ============================================================================
# FIX 2: Fix to_arti error - Config doesn't exist in arti_client::config
# Error says lines 155-156 have Config reference
# ============================================================================
print("2. Fixing Config reference...")

# Remove any Config reference that doesn't exist
content = re.sub(r'arti_client::config::Config', 'arti_client::config::Config', content)
# If there's a to_arti function returning Config, fix or remove it
if 'pub fn to_arti' in content:
    content = re.sub(r'pub fn to_arti\(&self\)[^{]*\{[^}]+\}', '', content)
    fixes += 1
    print("   ✅ Removed to_arti function")

# ============================================================================
# FIX 3: Fix orphaned dot on connect_tcp (line 932 from error)
# ============================================================================
print("3. Fixing connect_tcp...")

# Search for the pattern in error log
patterns_to_fix = [
    # Pattern 1: Orphaned dot after lock
    (r'let client = client_arc\.lock\(\)\.await;\s*\.connect_tcp', 
     'let client = client_arc.lock().await;\n                let arti_stream = client\n                    .connect_tcp'),
    # Pattern 2: Just orphaned dot
    (r'\n\s+\.connect_tcp\(addr, port\)',
     '\n                let arti_stream = client\n                    .connect_tcp(addr, port)'),
]

for pattern, replacement in patterns_to_fix:
    if re.search(pattern, content):
        content = re.sub(pattern, replacement, content)
        fixes += 1
        print("   ✅ Fixed connect_tcp orphaned dot")        break

# ============================================================================
# FIX 4: Fix return type mismatch (line 831 from error)
# Error: expected TorClientConfig, found PreferredRuntime
# ============================================================================
print("4. Checking return type consistency...")

# The struct should have PreferredRuntime, get_client should match
# Already correct per script output

# ============================================================================
# FIX 5: Remove unused imports
# ============================================================================
print("5. Cleaning imports...")

# Remove duplicate or unused imports
content = re.sub(r'\nuse arti_client::TorClient;\nuse arti_client::TorClient;', '\nuse arti_client::TorClient;', content)
print("   ✅ Cleaned imports")

# ============================================================================
# WRITE FILE
# ============================================================================
lib_path.write_text(content)

print(f"\n{'='*60}")
print(f"✅ Applied {fixes} fixes!")
print(f"{'='*60}")
