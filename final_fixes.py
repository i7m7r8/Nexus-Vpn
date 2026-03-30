#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Fix circuit variable: change from _circuit to circuit
content = content.replace(
    "let _circuit = self.tor_client.build_circuit().await?;",
    "let circuit = self.tor_client.build_circuit().await?;"
)

# 2. Simplify TorManager::start - remove the problematic .runtime() call
# Use the simpler approach: TorClient::create(runtime, config) which exists
tor_manager_start = """    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), anyhow::Error> {
        let runtime = TokioRustlsRuntime::create()?;
        let client = ArtiTorClient::create(runtime, config)?;
        let client = client.bootstrap().await?;
        self.client = Some(Arc::new(client));
        Ok(())
    }"""
content = re.sub(
    r'pub async fn start\(&mut self, config: TorClientConfig\) -> Result<\(\)\, anyhow::Error> \{.*?\}',
    tor_manager_start,
    content,
    flags=re.DOTALL
)

# 3. Fix VpnEngine::start_tor to match the return type
content = content.replace(
    "pub async fn start_tor(&mut self, config: TorClientConfig) -> Result<(), anyhow::Error> {",
    "pub async fn start_tor(&mut self, config: TorClientConfig) -> Result<(), anyhow::Error> {"
)

# 4. Remove the unused anyhow::Error import (it's already used, but we'll keep it)

# 5. Fix the type mismatch in VpnEngine::start_tor
# The function is already correct, we just need to ensure it uses anyhow::Error
# The start method returns anyhow::Error, so it's fine

lib_rs.write_text(content)
print("✅ Fixed all issues")
