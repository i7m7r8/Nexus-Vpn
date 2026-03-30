#!/bin/bash
echo "========================================"
echo "SESSION 1: Fix TorManager"
echo "========================================"

python3 << 'PYFIX'
import re

with open("rust/core/src/lib.rs", "r") as f:
    content = f.read()

# Find and replace TorManager
old_pattern = r'#\[derive\(Clone\)\]\s*pub struct TorManager \{[^}]+\}[^}]*impl TorManager \{[^}]+impl Default for TorManager \{[^}]+\}'

new_tor_manager = '''#[derive(Clone)]
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

content = re.sub(old_pattern, new_tor_manager, content, flags=re.DOTALL)

with open("rust/core/src/lib.rs", "w") as f:
    f.write(content)

print("TorManager replaced")
PYFIX

echo "Done!"
