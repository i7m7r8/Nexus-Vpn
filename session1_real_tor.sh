#!/bin/bash
echo "========================================"
echo "SESSION 1: REAL TOR INTEGRATION"
echo "========================================"

LIB_PATH="rust/core/src/lib.rs"

echo ""
echo "📝 Current TorManager status:"
grep -A5 "pub struct TorManager" $LIB_PATH | head -10

echo ""
echo "🔧 Applying Tor Integration fixes..."

# ============================================================================
# FIX 1: Uncomment Arti imports
# ============================================================================
echo "  1. Uncommenting Arti imports..."

# Remove commented Arti imports
sed -i '/\/\/ use arti_client::TorClient/d' $LIB_PATH
sed -i '/\/\/ tor_rtcompat::PreferredRuntime/d' $LIB_PATH
sed -i '/\/\/ tor_config::Config alias/d' $LIB_PATH

# Add real Arti imports after chacha20poly1305
sed -i '/use chacha20poly1305::aead::Aead;/a use arti_client::TorClient;\nuse tor_rtcompat::PreferredRuntime;\nuse tor_config::Config as TorConfig;' $LIB_PATH

echo "     ✅ Arti imports added"

# ============================================================================
# FIX 2: Fix TorClientConfig::to_arti() return type
# ============================================================================
echo "  2. Fixing TorClientConfig::to_arti()..."

# Replace stub return type with real tor_config::Config
sed -i 's/pub fn to_arti(\&self) -> String {/pub fn to_arti(\&self) -> tor_config::Config {/' $LIB_PATH
sed -i 's/String::from("tor_config_stub")/tor_config::Config::default()/' $LIB_PATH

echo "     ✅ TorClientConfig::to_arti() fixed"

# ============================================================================
# FIX 3: Implement real TorManager with Arti client
# ============================================================================
echo "  3. Implementing real TorManager..."

# Create the new TorManager implementation
cat > /tmp/tor_manager_fix.txt << 'TORFIX'
/// Manages the Arti Tor client lifecycle.
#[derive(Clone)]pub struct TorManager {
    client: Option<Arc<Mutex<TorClient<PreferredRuntime>>>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), String> {
        use arti_client::TorClient;
        use tor_rtcompat::PreferredRuntime;
        
        let arti_config = config.to_arti();
        
        let client = TorClient::builder()
            .with_runtime(PreferredRuntime::current())
            .with_config(arti_config)
            .create_bootstrapped()
            .await
            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;
        
        self.client = Some(Arc::new(Mutex::new(client)));
        Ok(())
    }

    pub async fn stop(&mut self) {
        self.client = None;
    }

    pub fn get_client(&self) -> Option<Arc<Mutex<TorClient<PreferredRuntime>>>> {
        self.client.clone()
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}
TORFIX

# Replace the old TorManager (find and replace)
python3 << 'PYFIX'
import re

with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

# Find old TorManager and replace
old_pattern = r'/// Manages the Arti Tor client lifecycle\.\n\[derive\(Clone\)\]\s*\npub struct TorManager \{[^}]+\}[^}]*impl TorManager \{[^}]+impl Default for TorManager \{[^}]+\}'

with open("/tmp/tor_manager_fix.txt", "r") as f:
    new_tor_manager = f.read()
# Simple replacement
if "pub struct TorManager {" in content:
    # Find start and end of TorManager + impl blocks
    start = content.find("/// Manages the Arti Tor client lifecycle")
    if start == -1:
        start = content.find("pub struct TorManager {")
    
    if start != -1:
        # Find end (next pub struct or end of impl Default)
        end = content.find("pub struct VpnEngine {", start)
        if end != -1:
            content = content[:start] + new_tor_manager + "\n\n" + content[end:]
            
            with open("rust/core/src/lib.rs", "w") as f:
                f.write(content)
            print("     ✅ TorManager replaced")
PYFIX

echo "  4. Fixing connect_to_target for real Tor..."

# Fix connect_to_target to use real Arti client
python3 << 'PYFIX2'
import re

with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

# Find and replace connect_to_target
old_connect = '''async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Stream, anyhow::Error> {
        if self.tor_enabled {
            // SNI→Tor chaining: route through Arti after SNI handshake
            if let Some(_client) = self.tor_manager.get_client() {
                // Route through Tor SOCKS5 proxy on 127.0.0.1:9050
                let tcp = tokio::net::TcpStream::connect("127.0.0.1:9050").await
                    .map_err(|e| anyhow::anyhow!("Tor SOCKS5: {}", e))?;
                Ok(Stream::Tor(tcp))
            } else { Err(anyhow::anyhow!("Tor not initialized")) }
        } else {
            let tcp = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Stream::Tcp(tcp))
        }
    }'''

new_connect = '''async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Stream, anyhow::Error> {
        if self.tor_enabled {
            // SNI→Tor chaining: route through Arti after SNI handshake
            if let Some(client_arc) = self.tor_manager.get_client() {
                let client = client_arc.lock().await;
                // Arti handles the Tor circuit internally                let arti_stream = client
                    .connect_tcp((addr, port))
                    .await
                    .map_err(|e| anyhow::anyhow!("Arti connect_tcp: {}", e))?;
                drop(client);
                Ok(Stream::Tor(arti_stream))
            } else {
                Err(anyhow::anyhow!("Tor not initialized"))
            }
        } else {
            let tcp = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Stream::Tcp(tcp))
        }
    }'''

if old_connect in content:
    content = content.replace(old_connect, new_connect)
    with open("rust/core/src/lib.rs", "w") as f:
        f.write(content)
    print("     ✅ connect_to_target fixed for real Arti")
PYFIX2

echo ""
echo "=== VERIFICATION ==="
echo ""
echo "Arti imports:"
grep -n "use arti_client\|use tor_rtcompat\|use tor_config" rust/core/src/lib.rs | head -5

echo ""
echo "TorManager struct:"
grep -A3 "pub struct TorManager" rust/core/src/lib.rs

echo ""
echo "TorClientConfig::to_arti():"
grep -A2 "pub fn to_arti" rust/core/src/lib.rs

echo ""
echo "Done!"
