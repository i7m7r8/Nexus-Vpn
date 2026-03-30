#!/usr/bin/env python3
import re
from pathlib import Path

# 1. Update Cargo.toml with correct arti dependencies
cargo_toml = Path("rust/core/Cargo.toml")
cargo_content = cargo_toml.read_text()

# Ensure arti-client with the right features
arti_line = 'arti-client = { version = "0.40.0", default-features = false, features = ["rustls", "tokio"] }'
if 'arti-client' not in cargo_content:
    # Add under [dependencies]
    cargo_content = re.sub(r'(\[dependencies\]\n)', r'\1' + arti_line + '\n', cargo_content, count=1)
else:
    # Replace the existing line
    cargo_content = re.sub(r'^arti-client = .*\n', arti_line + '\n', cargo_content, flags=re.MULTILINE)

# Add tor-rtcompat with tokio and rustls features
tor_rtcompat_line = 'tor-rtcompat = { version = "0.40.0", features = ["tokio", "rustls"] }'
if 'tor-rtcompat' not in cargo_content:
    cargo_content = re.sub(r'(\[dependencies\]\n)', r'\1' + tor_rtcompat_line + '\n', cargo_content, count=1)
else:
    cargo_content = re.sub(r'^tor-rtcompat = .*\n', tor_rtcompat_line + '\n', cargo_content, flags=re.MULTILINE)

cargo_toml.write_text(cargo_content)
print("✅ Cargo.toml updated with arti-client and tor-rtcompat")

# 2. Fix lib.rs imports and variable usage
lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# Remove any duplicate import lines (we'll add them cleanly)
lines = content.split('\n')
imports_to_keep = []
for line in lines:
    if line.startswith('use '):
        # Skip if it's an arti or tor_rtcompat import, we'll add them later
        if 'arti_client' in line or 'tor_rtcompat' in line:
            continue
    imports_to_keep.append(line)
content = '\n'.join(imports_to_keep)

# Add the necessary imports at the top (after the first use lines)
import_lines = [
    "use arti_client::TorClientConfig;",
    "use tor_rtcompat::Runtime;",
    "use tor_rtcompat::tokio::TokioRustlsRuntime;",
    "use arti_client::TorClient as ArtiTorClient;",
]
# Find where to insert them (after the first use block)
first_use_index = 0
for i, line in enumerate(content.split('\n')):
    if line.startswith('use '):
        first_use_index = i
        break
# Insert after that line
lines = content.split('\n')
for i, imp in enumerate(import_lines):
    lines.insert(first_use_index + i + 1, imp)
content = '\n'.join(lines)
print("✅ Added arti imports")

# Fix the variable name in connect_to_target
content = content.replace(
    "if let Some(_tor_client) = self.tor_manager.get_client() {",
    "if let Some(tor_client) = self.tor_manager.get_client() {"
)

# Ensure the TorManager struct is correct (it should hold ArtiTorClient)
if "pub struct TorManager" in content:
    # Replace if needed (we assume it's already correct, but ensure no leftover SimulatedTorClient)
    content = content.replace(
        "pub struct TorManager {\n    client: Option<Arc<SimulatedTorClient>>,\n}",
        "pub struct TorManager {\n    client: Option<Arc<ArtiTorClient<TokioRustlsRuntime>>>,\n}"
    )

# Ensure the start method uses the correct types
if "pub async fn start(&mut self, config: TorClientConfig) -> Result<(), arti_client::Error> {" in content:
    # It might be already correct, but we can replace to be sure
    start_method = """pub async fn start(&mut self, config: TorClientConfig) -> Result<(), arti_client::Error> {
        let runtime = TokioRustlsRuntime::create()?;
        let client = ArtiTorClient::create(runtime, config)?;
        let client = client.bootstrap().await?;
        self.client = Some(Arc::new(client));
        Ok(())
    }"""
    content = re.sub(
        r'pub async fn start\(&mut self, config: TorClientConfig\) -> Result<\(\), arti_client::Error> \{.*?\}',
        start_method,
        content,
        flags=re.DOTALL
    )

# Ensure get_client returns the right type
content = content.replace(
    "pub fn get_client(&self) -> Option<Arc<SimulatedTorClient>> {",
    "pub fn get_client(&self) -> Option<Arc<ArtiTorClient<TokioRustlsRuntime>>> {"
)

# Also, fix any usage of the custom TorClient in VpnConnection (keep SimulatedTorClient for that)
# We must not replace all occurrences, only the ones in TorManager.
# Since the custom TorClient is named SimulatedTorClient, we keep it.

lib_rs.write_text(content)
print("✅ lib.rs patched")

print("\n🎉 Done! Now commit and push.")
