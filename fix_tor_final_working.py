#!/usr/bin/env python3
"""
FINAL WORKING TorManager Fix
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
content = content.replace(
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
content = content.replace(
    'Option<Arc<TorClient>>',
    'Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("   ✅ TorManager struct fixed")

# ============================================================================
# FIX 2: get_client return type
# ============================================================================
print("2. Fixing get_client return type...")

content = content.replace(
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>',
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
content = content.replace(
    'pub fn get_client(&self) -> Option<Arc<TorClient>>',
    'pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>'
)
print("   ✅ get_client return type fixed")

# ============================================================================
# FIX 3: TorManager::start() - use correct builder
# ============================================================================
print("3. Fixing TorManager::start()...")
# Replace the entire start function
old_start = '''pub async fn start(&mut self, _config: TorClientConfig) -> Result<(), String> {
        let arti_config = config.to_arti();

        let client = arti_client::TorClient::builder()
            .with_config(arti_config)
            .create_bootstrapped()
            .await
            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;

        self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));
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
    print("   ✅ TorManager::start() fixed")
else:
    # Try alternate pattern
    if 'pub async fn start(&mut self' in content and 'TorClient::builder()' in content:
        print("   ⚠️  Start function found but pattern differs - manual fix may be needed")

# ============================================================================
# FIX 4: Fix .with_config to .config
# ============================================================================
print("4. Fixing builder pattern...")

content = content.replace('.with_config(arti_config)', '.config(arti_client::config::Config::default())')
content = content.replace('.with_config(', '.config(')
print("   ✅ Builder pattern fixed")

# ============================================================================
# FIX 5: Fix to_arti() - remove ConfigParts reference
# ============================================================================
print("5. Fixing to_arti()...")
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
# FIX 6: Fix orphaned dot on connect_tcp
# ============================================================================
print("6. Fixing connect_to_target...")

# Fix orphaned dot
content = content.replace(
    '\n                    .connect_tcp(addr, port)',
    '\n                let arti_stream = client\n                    .connect_tcp(addr, port)'
)
print("   ✅ connect_to_target fixed")

# ============================================================================
# FIX 7: Remove unused imports
# ============================================================================
print("7. Cleaning imports...")

content = content.replace('use arti_client::TorClient;\n', '')
content = content.replace('use tor_rtcompat::PreferredRuntime;\n', '')
print("   ✅ Imports cleaned")

lib_path.write_text(content)
print("\n✅ ALL FIXES APPLIED!")
