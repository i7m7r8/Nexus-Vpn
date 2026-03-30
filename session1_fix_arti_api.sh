#!/bin/bash
echo "========================================"
echo "SESSION 1: Fix Arti v0.40 API"
echo "========================================"

LIB_PATH="rust/core/src/lib.rs"

echo "🔧 Fixing Arti v0.40 API errors..."

# ============================================================================
# FIX 1: Update Cargo.toml with correct Arti features
# ============================================================================
echo "  1. Updating Cargo.toml Arti features..."

sed -i 's/arti-client = { version = "0.40", default-features = false, features = \["tokio", "static-sqlite"\] }/arti-client = { version = "0.40", default-features = false, features = ["tokio", "static-sqlite", "rustls"] }/' rust/core/Cargo.toml

echo "     ✅ Added rustls feature to arti-client"

# ============================================================================
# FIX 2: Fix imports
# ============================================================================
echo "  2. Fixing imports..."

# Remove old imports
sed -i '/^use arti_client::TorClient;$/d' $LIB_PATH
sed -i '/^use tor_rtcompat::PreferredRuntime;$/d' $LIB_PATH
sed -i '/^use tor_config::Config as TorConfig;$/d' $LIB_PATH

# Add correct imports after chacha20poly1305
sed -i '/use chacha20poly1305::aead::Aead;/a use arti_client::TorClient;\nuse arti_client::config::Config as TorConfig;' $LIB_PATH

echo "     ✅ Fixed imports"

# ============================================================================
# FIX 3: Fix TorClientConfig::to_arti()
# ============================================================================
echo "  3. Fixing TorClientConfig::to_arti()..."

python3 << 'PYFIX'
with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

# Fix return type and implementation
old = '''pub fn to_arti(&self) -> tor_config::Config {
        tor_config::Config::default()
    }'''

new = '''pub fn to_arti(&self) -> arti_client::config::Config {
        arti_client::config::Config::default()    }'''

content = content.replace(old, new)

with open("rust/core/src/lib.rs", "w") as f:
    f.write(content)

print("     ✅ Fixed TorClientConfig::to_arti()")
PYFIX

# ============================================================================
# FIX 4: Fix TorManager with correct Arti v0.40 API
# ============================================================================
echo "  4. Fixing TorManager with Arti v0.40 API..."

python3 << 'PYFIX2'
with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

# Old TorManager
old_tor = '''/// Manages the Arti Tor client lifecycle.
#[derive(Clone)]
pub struct TorManager {
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

# New TorManager with correct Arti v0.40 API
new_tor = '''/// Manages the Arti Tor client lifecycle.
#[derive(Clone)]
pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient>>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), String> {
        let arti_config = config.to_arti();
        
        let client = arti_client::TorClient::create_bootstrapped(arti_config)
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

if old_tor in content:
    content = content.replace(old_tor, new_tor)
    with open("rust/core/src/lib.rs", "w") as f:
        f.write(content)
    print("     ✅ Fixed TorManager for Arti v0.40")
else:
    print("     ⚠️  Could not find old TorManager")
PYFIX2

# ============================================================================# FIX 5: Fix connect_to_target
# ============================================================================
echo "  5. Fixing connect_to_target..."

python3 << 'PYFIX3'
with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

old_connect = '''if let Some(client_arc) = self.tor_manager.get_client() {
                let client = client_arc.lock().await;
                // Arti handles the Tor circuit internally
                let arti_stream = client
                    .connect_tcp((addr, port))
                    .await
                    .map_err(|e| anyhow::anyhow!("Arti connect_tcp: {}", e))?;
                drop(client);
                Ok(Stream::Tor(arti_stream))
            } else {
                Err(anyhow::anyhow!("Tor not initialized"))
            }'''

new_connect = '''if let Some(client_arc) = self.tor_manager.get_client() {
                let client = client_arc.lock().await;
                // Arti handles the Tor circuit internally
                let arti_stream = client
                    .connect_tcp(addr, port)
                    .await
                    .map_err(|e| anyhow::anyhow!("Arti connect_tcp: {}", e))?;
                drop(client);
                Ok(Stream::Tor(arti_stream))
            } else {
                Err(anyhow::anyhow!("Tor not initialized"))
            }'''

if old_connect in content:
    content = content.replace(old_connect, new_connect)
    with open("rust/core/src/lib.rs", "w") as f:
        f.write(content)
    print("     ✅ Fixed connect_to_target (addr, port separated)")
PYFIX3

echo ""
echo "=== VERIFICATION ==="
echo ""
echo "Arti imports:"
grep -n "use arti_client" rust/core/src/lib.rs | head -3

echo ""
echo "Cargo.toml arti-client:"
grep "arti-client" rust/core/Cargo.toml
echo ""
echo "TorManager struct:"
grep -A2 "pub struct TorManager" rust/core/src/lib.rs

echo ""
echo "Done!"
