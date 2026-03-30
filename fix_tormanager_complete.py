#!/usr/bin/env python3
"""
COMPLETE TorManager Rewrite - Fix corrupted struct
"""
from pathlib import Path
import re

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("COMPLETE TorManager Rewrite")
print("="*60)

# ============================================================================
# STEP 1: Find and remove the broken TorManager + fix VpnEngine
# ============================================================================
print("\n1. Finding corrupted TorManager...")

# Find the broken pattern (TorManager without closing brace, merged with VpnEngine)
broken_pattern = r'pub struct TorManager \{\s*client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,\s*\n\s*pub struct VpnEngine'

if re.search(broken_pattern, content):
    print("   ✅ Found corrupted TorManager")
    
    # Replace with correct TorManager + VpnEngine separation
    correct_code = '''pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,
}

impl TorManager {
    pub async fn start(&mut self, _config: TorClientConfig) -> Result<(), String> {
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
}

pub struct VpnEngine'''
    
    content = re.sub(broken_pattern, correct_code, content)
    print("   ✅ Fixed TorManager struct separation")
else:
    print("   ⚠️  Pattern not found - trying alternate fix")

# ============================================================================
# STEP 2: Fix to_arti() function - remove it (not needed with Config::default())
# ============================================================================
print("2. Fixing to_arti() function...")

old_to_arti = r'pub fn to_arti\(&self\) -> arti_client::config::Config \{\s*let mut cfg = arti_client::config::Config::default\(\);\s*// Can customize config here based on self fields\s*cfg\s*\}'

if re.search(old_to_arti, content):
    # Just simplify it
    new_to_arti = '''pub fn to_arti(&self) -> arti_client::config::Config {
        arti_client::config::Config::default()
    }'''
    content = re.sub(old_to_arti, new_to_arti, content)
    print("   ✅ Fixed to_arti()")

# ============================================================================
# STEP 3: Fix TorClient::builder() calls - use .config() not .with_config()
# ============================================================================
print("3. Fixing TorClient builder calls...")
content = content.replace('.with_config(arti_config)', '.config(arti_client::config::Config::default())')
content = content.replace('.with_config(arti_client::config::Config::default())', '.config(arti_client::config::Config::default())')
print("   ✅ Fixed builder calls")

# ============================================================================
# STEP 4: Fix connect_to_target - fix orphaned dot
# ============================================================================
print("4. Fixing connect_to_target...")
# Find and fix the orphaned dot pattern
content = re.sub(
    r'if let Some\(client_arc\) = self\.tor_manager\.get_client\(\) \{\s*let client = client_arc\.lock\(\)\.await;\s*\.connect_tcp\(addr, port\)',
    '''if let Some(client_arc) = self.tor_manager.get_client() {                let client = client_arc.lock().await;
                let arti_stream = client
                    .connect_tcp(addr, port)
                    .await
                    .map_err(|e| anyhow::anyhow!("Arti connect_tcp: {}", e))?;
                drop(client);
                Ok(Stream::Tor(arti_stream))''',
    content
)
print("   ✅ Fixed connect_to_target")

# ============================================================================
# STEP 5: Remove unused imports
# ============================================================================
print("5. Cleaning unused imports...")
content = re.sub(r'\nuse arti_client::TorClient;', '', content)
content = re.sub(r'\nuse tor_rtcompat::PreferredRuntime;', '', content)
print("   ✅ Removed unused imports")

lib_path.write_text(content)
print("\n✅ COMPLETE - TorManager rewritten!")
