#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Add missing imports at the top (after existing ones)
imports_to_add = [
    "use std::sync::{Mutex, RwLock};",
    "use std::time::Duration;",
    "use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};",
    "use tokio::time::sleep;",
    "use chacha20poly1305::KeyInit;",  # For ChaCha20Poly1305::new
    "use aes_gcm::aead::KeyInit as AesKeyInit;",  # For Aes256Gcm::new
]

# Find the position to insert (after the last existing use statement)
lines = content.split('\n')
last_use_index = 0
for i, line in enumerate(lines):
    if line.startswith('use ') and not line.startswith('use std::sync::{Mutex, RwLock};'):
        last_use_index = i
    # Also skip our own imports if they already exist
    for imp in imports_to_add:
        if line.strip() == imp:
            # Already present, remove from list
            if imp in imports_to_add:
                imports_to_add.remove(imp)

# Insert new imports after the last use line
if imports_to_add:
    for imp in reversed(imports_to_add):
        lines.insert(last_use_index + 1, imp)
content = '\n'.join(lines)
print("✅ Added missing imports.")

# 2. Replace connect_to_target function with a simple version
#    The original had a complex trait object that caused errors.
#    We'll replace it with a simple one that returns Box<dyn AsyncRead + AsyncWrite + Unpin + Send>.
#    But we also need to import AsyncRead/AsyncWrite from tokio.
#    We'll ensure they are already imported.
if "use tokio::io::{AsyncRead, AsyncWrite};" not in content:
    # Add after other imports
    lines = content.split('\n')
    for i, line in enumerate(lines):
        if line.startswith('use std::sync::'):
            lines.insert(i+1, "use tokio::io::{AsyncRead, AsyncWrite};")
            break
    content = '\n'.join(lines)
    print("✅ Added AsyncRead/AsyncWrite imports.")

# Now replace the function
new_connect = """    async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Box<dyn AsyncRead + AsyncWrite + Unpin + Send>, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Box::new(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Box::new(stream))
        }
    }"""

# Find the old function and replace
old_pattern = r'async fn connect_to_target\([^)]*\) -> Result<[^>]+> \{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}'
content = re.sub(old_pattern, new_connect, content, flags=re.DOTALL)
print("✅ Replaced connect_to_target function.")

# 3. Fix any remaining unused imports (optional)
# Remove duplicate tokio::io::AsyncRead import if it appears twice
content = re.sub(r'use tokio::io::\{AsyncRead, AsyncWrite\};\n\s*use tokio::io::\{AsyncRead, AsyncWrite\};', 'use tokio::io::{AsyncRead, AsyncWrite};', content)

lib_rs.write_text(content)
print("✅ All fixes applied.")
