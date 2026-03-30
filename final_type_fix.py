#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Fix the return type of connect_to_target to use Box<dyn Trait>
#    Replace the whole function with a version that returns a boxed trait object.
connect_to_target_pattern = r'async fn connect_to_target\(&self, addr: &str, port: u16\) -> Result<impl tokio::io::AsyncRead \+ tokio::io::AsyncWrite \+ Unpin, anyhow::Error> \{[^}]*\}'
new_connect = '''async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Box<dyn tokio::io::AsyncRead + tokio::io::AsyncWrite + Unpin + Send>, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Box::new(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Box::new(stream))
        }
    }'''
content = re.sub(connect_to_target_pattern, new_connect, content, flags=re.DOTALL)
print("✅ Fixed connect_to_target return type")

# 2. Add missing import for AsyncRead and AsyncWrite if needed
if "use tokio::io::{AsyncRead, AsyncWrite};" not in content:
    # Insert after other tokio imports
    lines = content.split('\n')
    for i, line in enumerate(lines):
        if line.startswith("use tokio::time::"):
            lines.insert(i+1, "use tokio::io::{AsyncRead, AsyncWrite};")
            break
    content = '\n'.join(lines)
    print("✅ Added AsyncRead/AsyncWrite import")

# 3. Remove unused imports (optional but nice)
unused_imports = [
    "use tokio::sync::{RwLock, Mutex, mpsc};",
    "use tokio::time::{interval, Duration, sleep};",
    "use std::io;",
    "use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};",
    "use aes_gcm::aead::{Aead, KeyInit, Payload};",
    "use rustls::{ClientConfig, ClientConnection, RootCertStore};",
    "use std::io::Cursor;",
]
for imp in unused_imports:
    content = content.replace(imp + "\n", "")
    content = content.replace(imp, "")
print("✅ Removed some unused imports")

# 4. Fix the unused variable `circuit` in the simulated Tor client (just prefix with underscore)
content = content.replace(
    "let circuit = self.tor_client.build_circuit().await?;",
    "let _circuit = self.tor_client.build_circuit().await?;"
)

# 5. Ensure the start method's return type uses arti_client::Error (which is fine)
# We already have it in the code.

lib_rs.write_text(content)
print("✅ lib.rs patched for type compatibility")
