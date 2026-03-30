#!/usr/bin/env python3
import re
from pathlib import Path

# 1. Update Cargo.toml to remove arti-client
cargo_toml = Path("rust/core/Cargo.toml")
cargo_content = cargo_toml.read_text()
cargo_content = re.sub(r'^arti-client = .*\n', '', cargo_content, flags=re.MULTILINE)
cargo_content = re.sub(r'^tor-rtcompat = .*\n', '', cargo_content, flags=re.MULTILINE)
cargo_toml.write_text(cargo_content)
print("✅ Removed arti-client from Cargo.toml")

# 2. Update lib.rs to remove all arti references
lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# Remove arti imports
content = re.sub(r'^use arti_client::.*;\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^use tor_rtcompat::.*;\n', '', content, flags=re.MULTILINE)

# Replace TorManager with stub that doesn't use arti
tor_manager_stub = """pub struct TorManager {
    client: Option<()>,
}

impl TorManager {
    pub async fn start(&mut self, _config: TorClientConfig) -> Result<(), String> {
        Ok(())
    }

    pub async fn stop(&mut self) {
        self.client = None;
    }

    pub fn get_client(&self) -> Option<()> {
        None
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}"""
content = re.sub(r'pub struct TorManager.*?\nimpl TorManager.*?\n}\n\nimpl Default.*?\n}', tor_manager_stub, content, flags=re.DOTALL)

# Remove Stream enum's Tor variant
stream_enum_stub = """enum Stream {
    Tcp(tokio::net::TcpStream),
}"""
content = re.sub(r'enum Stream \{[^}]*\}', stream_enum_stub, content, flags=re.DOTALL)

# Fix connect_to_target to not use Tor
content = content.replace(
    "if let Some(tor_client) = self.tor_manager.get_client() {\n            let stream = tor_client.connect((addr, port)).await?;\n            Ok(Stream::Tor(stream))\n        } else {",
    "if false {\n            let stream = tokio::net::TcpStream::connect((addr, port)).await?;\n            Ok(Stream::Tcp(stream))\n        } else {"
)

# Remove any remaining arti_client references in the Stream impl
content = content.replace("Tor(arti_client::DataStream),", "")

# Fix circuit variable
content = content.replace(
    "let circuit = self.tor_client.build_circuit().await?;",
    "let _circuit = self.tor_client.build_circuit().await?;"
)

# Remove unused anyhow import
content = content.replace("use anyhow::Error;", "")

lib_rs.write_text(content)
print("✅ Removed all arti references from lib.rs")
