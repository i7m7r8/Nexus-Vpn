#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# Add Clone derive to AppConfig
if 'pub struct AppConfig' in content and '#[derive(Clone)]' not in content:
    content = content.replace(
        '#[derive(Serialize, Deserialize)]\npub struct AppConfig',
        '#[derive(Clone, Serialize, Deserialize)]\npub struct AppConfig',
        1
    )
    print("✅ Added Clone derive to AppConfig")

# Also add Clone to LeakTestResult (if needed)
if 'pub struct LeakTestResult' in content and '#[derive(Clone)]' not in content:
    content = content.replace(
        '#[derive(Debug, Clone)]\npub struct LeakTestResult',
        '#[derive(Clone, Debug)]\npub struct LeakTestResult',
        1
    )

# Fix unused variable warnings by prefixing with underscore
content = content.replace(
    'if let Some(tor_client) = self.tor_manager.get_client() {',
    'if let Some(_tor_client) = self.tor_manager.get_client() {'
)
content = content.replace(
    'let circuit_id: String = (0..16)',
    'let _circuit_id: String = (0..16)'
)
content = content.replace(
    'let restore = rule.replace(" -A ", " -D ");',
    'let _restore = rule.replace(" -A ", " -D ");'
)
content = content.replace(
    'pub async fn record_packet(&self, size: usize, protocol: &str) {',
    'pub async fn record_packet(&self, _size: usize, protocol: &str) {'
)
content = content.replace(
    'randomize: bool,',
    '_randomize: bool,'
)
content = content.replace(
    'pub async fn connect(&self, target_host: &str, target_port: u16) -> Result<(), String> {',
    'pub async fn connect(&self, target_host: &str, _target_port: u16) -> Result<(), String> {'
)
content = content.replace(
    'let sni_hello = self.sni_handler.build_client_hello(target_host, true).await?;',
    'let _sni_hello = self.sni_handler.build_client_hello(target_host, true).await?;'
)
content = content.replace(
    'let circuit = self.tor_client.build_circuit().await?;',
    'let _circuit = self.tor_client.build_circuit().await?;'
)
content = content.replace(
    'let now = tokio::time::Instant::now();',
    'let _now = tokio::time::Instant::now();'
)

lib_rs.write_text(content)
print("✅ Final fixes applied.")
