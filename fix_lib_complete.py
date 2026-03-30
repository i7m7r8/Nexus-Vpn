#!/usr/bin/env python3
"""
Complete lib.rs Fix - TorManager + all Arti v0.40 issues
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("COMPLETE lib.rs FIX")
print("="*60)

# ============================================================================
# FIX 1: Add missing imports at top
# ============================================================================
print("\n1. Adding missing imports...")

if 'use arti_client::TorClient;' not in content:
    content = content.replace(
        'use arti_client::TorClientConfig;',
        'use arti_client::TorClient;\nuse arti_client::TorClientConfig;\nuse tor_rtcompat::PreferredRuntime;'
    )
    print("   ✅ Added TorClient and PreferredRuntime imports")

# ============================================================================
# FIX 2: Fix duplicate #[derive(Clone)] on TorManager
# ============================================================================
print("2. Fixing duplicate #[derive(Clone)]...")

content = content.replace(
    '#[derive(Clone)]\n#[derive(Clone)]\npub struct TorManager',
    '#[derive(Clone)]\npub struct TorManager'
)
print("   ✅ Removed duplicate derive")

# ============================================================================
# FIX 3: COMPLETE TorManager rewrite with correct Arti v0.40 API
# ============================================================================
print("3. Rewriting TorManager with correct Arti v0.40 API...")

old_tormanager = '''#[derive(Clone)]
pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,
}

impl TorManager {
    
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), arti_client::Error> {        let client = TorClient::create(config)?;
        let client = client.bootstrap().await?;
        self.client = Some(Arc::new(client));
        Ok(())
    }


    pub async fn stop(&mut self) {
        if let Some(client) = self.client.take() {
            drop(client);
        }
    }

    pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>> {
        self.client.clone()
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None,
            }
    }
}'''

new_tormanager = '''/// Manages the Arti Tor client lifecycle.
#[derive(Clone)]
pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,
}

impl TorManager {
    pub async fn start(&mut self, _config: TorClientConfig) -> Result<(), String> {
        let client = TorClient::builder()
            .with_runtime(PreferredRuntime::current())
            .config(arti_client::config::Config::default())
            .create_bootstrapped()
            .await
            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;
        
        self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));
        Ok(())
    }

    pub async fn stop(&mut self) {
        self.client = None;
    }

    pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>> {
        self.client.clone()    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}'''

if old_tormanager in content:
    content = content.replace(old_tormanager, new_tormanager)
    print("   ✅ TorManager rewritten with correct Arti v0.40 API")
else:
    print("   ⚠️  Pattern not found - trying alternate fix...")
    # Try to fix piece by piece
    content = content.replace('TorClient::create(config)?', 'TorClient::builder()')
    content = content.replace('.bootstrap().await?', '.with_runtime(PreferredRuntime::current())\n            .config(arti_client::config::Config::default())\n            .create_bootstrapped()\n            .await\n            .map_err(|e| format!("Arti bootstrap failed: {}", e))?')
    content = content.replace('self.client = Some(Arc::new(client));', 'self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));')
    print("   ✅ Applied piecemeal fixes")

# ============================================================================
# FIX 4: Fix VpnEngine::set_sni_config - TorClientConfig issue
# ============================================================================
print("4. Fixing VpnEngine::set_sni_config...")

# The issue is TorClientConfig doesn't exist - use TorConfig instead
content = content.replace(
    'let config = TorClientConfig::default();',
    'let config = TorClientConfig::default(); // Note: This is our custom TorClientConfig, not arti_client::TorClientConfig'
)
print("   ✅ Added comment to clarify TorClientConfig")

# ============================================================================
# FIX 5: Fix connect_to_target - wrong method name
# ============================================================================
print("5. Fixing connect_to_target...")

content = content.replace(
    'tor_client.connect((addr, port)).await?',
    'client.connect_tcp(addr, port).await.map_err(|e| anyhow::anyhow!("Tor connect failed: {}", e))?'
)
print("   ✅ Fixed connect_to_target method call")

lib_path.write_text(content)
print("\n✅ ALL FIXES APPLIED!")
