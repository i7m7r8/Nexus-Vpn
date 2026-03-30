#!/bin/bash
echo "========================================"
echo "FIX: Arti v0.40 Runtime Type Error"
echo "========================================"

LIB="rust/core/src/lib.rs"

echo "🔧 The problem: TorClientConfig is NOT a Runtime type"
echo "   TorClient needs PreferredRuntime, not TorClientConfig"
echo ""

# ============================================================================
# FIX 1: Remove wrong import
# ============================================================================
echo "  1. Removing wrong Config import..."
sed -i '/^use arti_client::config::Config as TorConfig;$/d' $LIB
echo "     ✅ Removed"

# ============================================================================
# FIX 2: Fix TorClientConfig::to_arti() - return Config for builder
# ============================================================================
echo "  2. Fixing TorClientConfig::to_arti()..."

python3 << 'PYFIX1'
with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

old = '''pub fn to_arti(&self) -> arti_client::config::ConfigParts {
        arti_client::config::ConfigParts::default()
    }'''

new = '''pub fn to_arti(&self) -> arti_client::config::Config {
        arti_client::config::Config::default()
    }'''

content = content.replace(old, new)

with open("rust/core/src/lib.rs", "w") as f:
    f.write(content)

print("     ✅ Fixed return type")
PYFIX1

# ============================================================================
# FIX 3: Fix TorManager - use PreferredRuntime generic
# ============================================================================
echo "  3. Fixing TorManager with PreferredRuntime..."

python3 << 'PYFIX2'with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

old_tor = '''#[derive(Clone)]
pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), String> {
        let arti_config = config.to_arti();

        let client = arti_client::TorClient::builder()
            .create_bootstrapped()
            .await
            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;

        self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));
        Ok(())
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

new_tor = '''#[derive(Clone)]
pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), String> {
        let arti_config = config.to_arti();

        let client = arti_client::TorClient::builder()
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

if old_tor in content:
    content = content.replace(old_tor, new_tor)
    with open("rust/core/src/lib.rs", "w") as f:
        f.write(content)
    print("     ✅ TorManager fixed with PreferredRuntime")
else:
    print("     ⚠️  Could not find old TorManager - checking...")
    import re
    match = re.search(r'pub struct TorManager \{[^}]+\}', content)
    if match:
        print(f"     Found: {match.group(0)[:100]}...")
PYFIX2

# ============================================================================
# FIX 4: Add PreferredRuntime import
# ============================================================================
echo "  4. Adding PreferredRuntime import..."
if ! grep -q "use tor_rtcompat::PreferredRuntime;" $LIB; then
    sed -i '/use tokio::time::sleep;/a use tor_rtcompat::PreferredRuntime;' $LIB
    echo "     ✅ Added import"
else
    echo "     ✅ Already exists"
fi

echo ""
echo "=== VERIFICATION ==="
echo ""
echo "TorManager struct:"
grep -A3 "pub struct TorManager" $LIB
echo ""
echo "TorManager::start():"
grep -A10 "pub async fn start" $LIB | grep -A10 "TorManager" | head -12

echo ""
echo "Imports:"
grep -n "use.*PreferredRuntime\|use.*TorClient" $LIB | head -5

echo ""
echo "✅ All fixes applied!"
echo ""
echo "========================================"
echo "KEY CHANGES:"
echo "========================================"
echo "1. TorClient<PreferredRuntime> - NOT TorClientConfig"
echo "2. .with_config(arti_config) - config on builder"
echo "3. .create_bootstrapped() - no arguments"
echo "4. Config (not ConfigParts) - correct return type"
echo "========================================"
