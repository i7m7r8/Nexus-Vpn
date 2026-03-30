#!/bin/bash
echo "========================================"
echo "SESSION 1: Fix TorManager Struct"
echo "========================================"

LIB_PATH="rust/core/src/lib.rs"

echo "Current TorManager:"
grep -A10 "pub struct TorManager" $LIB_PATH

echo ""
echo "Replacing TorManager with real Arti client..."

# Use Python for reliable multi-line replacement
python3 << 'PYFIX'
with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

# Find and replace TorManager struct + impl
old_tor_manager = '''/// Manages the Arti Tor client lifecycle.
#[derive(Clone)] // Only one derive
pub struct TorManager {
    client: Option<()>,
}

impl TorManager {
    pub async fn start(&mut self, _config: TorClientConfig) -> Result<(), String> {
        // Stub: Tor integration disabled for this build
        // In production: initialize arti-client here
        Ok(())
    }

    pub async fn stop(&mut self) {
        self.client = None;
    }

    pub fn get_client(&self) -> Option<()> {
        None
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}'''

new_tor_manager = '''/// Manages the Arti Tor client lifecycle.
#[derive(Clone)]pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), String> {
        let arti_config = config.to_arti();
        
        let client = arti_client::TorClient::builder()
            .with_runtime(tor_rtcompat::PreferredRuntime::current())
            .with_config(arti_config)
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
        self.client.clone()
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}'''

if old_tor_manager in content:
    content = content.replace(old_tor_manager, new_tor_manager)
    with open("rust/core/src/lib.rs", "w") as f:
        f.write(content)
    print("✅ TorManager replaced with real Arti client")
else:
    print("⚠️  Could not find old TorManager - checking current state...")
    # Show what's actually there
    import re
    match = re.search(r'pub struct TorManager \{[^}]+\}', content)
    if match:
        print(f"Found: {match.group(0)}")
PYFIX

echo ""
echo "=== VERIFICATION ==="echo ""
echo "TorManager struct:"
grep -A3 "pub struct TorManager" $LIB_PATH

echo ""
echo "TorManager::start():"
grep -A5 "pub async fn start" $LIB_PATH | head -8

echo ""
echo "Done!"
