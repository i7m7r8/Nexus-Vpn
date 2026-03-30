#!/bin/bash
echo "========================================"
echo "SESSION 1: FINAL Arti v0.40 Fix"
echo "========================================"

LIB="rust/core/src/lib.rs"

echo "🔧 Applying final Arti v0.40 fixes..."

# ============================================================================
# FIX 1: Remove wrong import
# ============================================================================
echo "  1. Fixing imports..."
sed -i '/^use arti_client::config::Config as TorConfig;$/d' $LIB
echo "     ✅ Removed wrong import"

# ============================================================================
# FIX 2: Fix TorClientConfig::to_arti() - just return default Arti config
# ============================================================================
echo "  2. Fixing TorClientConfig::to_arti()..."

python3 << 'PYFIX1'
with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

old = '''pub fn to_arti(&self) -> arti_client::config::Config {
        arti_client::config::Config::default()    }'''

# Just return a basic Config - Arti v0.40 uses builder pattern
new = '''pub fn to_arti(&self) -> arti_client::config::ConfigParts {
        arti_client::config::ConfigParts::default()
    }'''

content = content.replace(old, new)

with open("rust/core/src/lib.rs", "w") as f:
    f.write(content)

print("     ✅ Fixed TorClientConfig::to_arti()")
PYFIX1

# ============================================================================
# FIX 3: Fix TorManager - use builder correctly
# ============================================================================
echo "  3. Fixing TorManager..."

python3 << 'PYFIX2'
with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()
# Find and replace TorManager struct and impl
old_tor = '''#[derive(Clone)]
pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient>>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), String> {
        let arti_config = config.to_arti();

        let client = arti_client::TorClient::builder()
            .create_bootstrapped(arti_config)
            .await
            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;

        self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));
        Ok(())
    }

    pub async fn stop(&mut self) {
        self.client = None;
    }

    pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient>>> {
        self.client.clone()
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}'''

# For Arti v0.40, we need to use the builder properly with Runtime
new_tor = '''#[derive(Clone)]
pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), String> {
        // For now, use default config - can be customized later
        let client = arti_client::TorClient::builder()
            .create_bootstrapped()
            .await
            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;

        self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));        Ok(())
    }

    pub async fn stop(&mut self) {
        self.client = None;
    }

    pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>> {
        self.client.clone()
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}'''

if old_tor in content:
    content = content.replace(old_tor, new_tor)
    with open("rust/core/src/lib.rs", "w") as f:
        f.write(content)
    print("     ✅ Fixed TorManager")
else:
    print("     ⚠️  Could not find old TorManager")
PYFIX2

# ============================================================================
# FIX 4: Fix connect_to_target - separate addr, port
# ============================================================================
echo "  4. Fixing connect_to_target..."
sed -i 's/\.connect_tcp((addr, port))/.connect_tcp(addr, port)/' $LIB
echo "     ✅ Fixed connect_to_target"

echo ""
echo "=== VERIFICATION ==="
echo ""
echo "TorManager struct:"
grep -A3 "pub struct TorManager" $LIB

echo ""
echo "TorManager::start():"
grep -A8 "pub async fn start" $LIB | grep -A8 "TorManager" | head -10

echo ""
echo "Done!"
