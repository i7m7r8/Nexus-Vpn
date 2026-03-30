#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Replace TorManager stub with the real arti version
tor_manager_real = """pub struct TorManager {
    client: Option<Arc<ArtiTorClient<TokioRustlsRuntime>>>,
}

impl TorManager {
    pub async fn start(&mut self, config: TorClientConfig) -> Result<(), arti_client::Error> {
        let runtime = TokioRustlsRuntime::create()?;
        let client = ArtiTorClient::create(runtime, config)?;
        let client = client.bootstrap().await?;
        self.client = Some(Arc::new(client));
        Ok(())
    }

    pub async fn stop(&mut self) {
        if let Some(client) = self.client.take() {
            drop(client);
        }
    }

    pub fn get_client(&self) -> Option<Arc<ArtiTorClient<TokioRustlsRuntime>>> {
        self.client.clone()
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}"""

# Find and replace the existing TorManager block
content = re.sub(
    r'pub struct TorManager.*?\nimpl TorManager.*?\n}\n\nimpl Default.*?\n}',
    tor_manager_real,
    content,
    flags=re.DOTALL
)

# 2. Restore the Stream enum with Tor variant
stream_enum_real = """enum Stream {
    Tcp(tokio::net::TcpStream),
    Tor(arti_client::DataStream),
}"""
content = re.sub(r'enum Stream \{[^}]*\}', stream_enum_real, content, flags=re.DOTALL)

# 3. Add missing imports (in case they were removed)
imports_needed = [
    "use arti_client::TorClientConfig;",
    "use tor_rtcompat::tokio::TokioRustlsRuntime;",
    "use arti_client::TorClient as ArtiTorClient;"
]
for imp in imports_needed:
    if imp not in content:
        content = imp + "\n" + content

# 4. Fix connect_to_target to use Tor when available
# Find the function and replace the part that creates the stream
content = content.replace(
    "if false {\n            let stream = tokio::net::TcpStream::connect((addr, port)).await?;\n            Ok(Stream::Tcp(stream))\n        } else {",
    "if let Some(tor_client) = self.tor_manager.get_client() {\n            let stream = tor_client.connect((addr, port)).await?;\n            Ok(Stream::Tor(stream))\n        } else {"
)

# 5. Uncomment the circuit variable (it should be used)
content = content.replace(
    "let _circuit = self.tor_client.build_circuit().await?;",
    "let circuit = self.tor_client.build_circuit().await?;"
)

# 6. Remove any leftover placeholder TorClientConfig
content = content.replace("pub struct TorClientConfig;", "")

lib_rs.write_text(content)
print("✅ Restored real arti integration in lib.rs")
