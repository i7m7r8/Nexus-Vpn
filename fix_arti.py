#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Rename custom TorClient to SimulatedTorClient (to avoid conflict with arti client)
content = content.replace("pub struct TorClient {", "pub struct SimulatedTorClient {")
content = content.replace("impl TorClient {", "impl SimulatedTorClient {")
content = content.replace("pub fn new(config: TorConfig) -> Self {", "pub fn new(config: TorConfig) -> Self {")  # no change
content = content.replace("Arc<TorClient>", "Arc<SimulatedTorClient>")
content = content.replace("Arc<TorClient>", "Arc<SimulatedTorClient>")  # again to catch all
content = re.sub(r'pub tor_client: Arc<TorClient>', 'pub tor_client: Arc<SimulatedTorClient>', content)
content = re.sub(r'tor_client: Arc<TorClient>', 'tor_client: Arc<SimulatedTorClient>', content)
content = re.sub(r'let tor_client = Arc::new\(TorClient::new', 'let tor_client = Arc::new(SimulatedTorClient::new', content)

# 2. Update TorManager to hold the real arti client
content = content.replace(
    "pub struct TorManager {\n    client: Option<Arc<TorClient>>,\n}",
    "use arti_client::TorClient as ArtiTorClient;\nuse tor_rtcompat::tokio::TokioRustlsRuntime;\n\npub struct TorManager {\n    client: Option<Arc<ArtiTorClient<TokioRustlsRuntime>>>,\n}"
)

# 3. Replace TorManager::start with proper implementation
content = re.sub(
    r'pub async fn start\(&mut self, config: TorClientConfig\) -> Result<\(\)\, arti_client::Error> \{\s+// Placeholder: arti client not yet integrated\s+Ok\(\(\)\)\s+\}',
    r'''pub async fn start(&mut self, config: TorClientConfig) -> Result<(), arti_client::Error> {
        let runtime = TokioRustlsRuntime::create()?;
        let client = ArtiTorClient::create(runtime, config)?;
        let client = client.bootstrap().await?;
        self.client = Some(Arc::new(client));
        Ok(())
    }''',
    content,
    flags=re.DOTALL
)

# 4. Update TorManager::get_client to return the correct type
content = content.replace(
    "pub fn get_client(&self) -> Option<Arc<TorClient>> {",
    "pub fn get_client(&self) -> Option<Arc<ArtiTorClient<TokioRustlsRuntime>>> {"
)

# 5. Fix any remaining references to TorClient in the code (like in VpnConnection::new)
content = re.sub(r'tor_client: Arc<TorClient>', 'tor_client: Arc<SimulatedTorClient>', content)

# 6. Fix the clone error on config read (already present, but ensure it's correct)
content = content.replace(
    'self.config.read().await.clone()',
    '(*self.config.read().await).clone()'
)

# 7. Remove duplicate imports (e.g., if we added them twice)
content = re.sub(r'use arti_client::TorClient as ArtiTorClient;\n\s*use arti_client::TorClient as ArtiTorClient;', 'use arti_client::TorClient as ArtiTorClient;', content)

# 8. Add missing import for TokioRustlsRuntime
if 'use tor_rtcompat::tokio::TokioRustlsRuntime;' not in content:
    content = content.replace('use arti_client::TorClient as ArtiTorClient;', 'use arti_client::TorClient as ArtiTorClient;\nuse tor_rtcompat::tokio::TokioRustlsRuntime;', 1)

lib_rs.write_text(content)
print("✅ Arti integration fixed.")
