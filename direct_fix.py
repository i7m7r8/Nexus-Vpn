#!/usr/bin/env python3
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# Direct replacement of the function
old_func = """    async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Box<dyn tokio::io::AsyncRead + tokio::io::AsyncWrite + Unpin + Send>, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Box::new(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Box::new(stream))
        }
    }"""

new_func = """    async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Stream, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Stream::Tor(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Stream::Tcp(stream))
        }
    }"""

content = content.replace(old_func, new_func)

# Also fix the circuit variable
content = content.replace(
    "let circuit = self.tor_client.build_circuit().await?;",
    "let _circuit = self.tor_client.build_circuit().await?;"
)

# Remove the unused AsyncRead/AsyncWrite import
content = content.replace("use tokio::io::{AsyncRead, AsyncWrite};", "")

lib_rs.write_text(content)
print("✅ Fixed directly.")
