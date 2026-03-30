#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Remove duplicate Aead import (if still there)
content = re.sub(r'^use aes_gcm::aead::Aead;\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^use chacha20poly1305::aead::Aead;\n', 'use chacha20poly1305::aead::Aead;\n', content)

# 2. Ensure the Stream enum exists
if "enum Stream {" not in content:
    # Insert after the use statements
    stream_enum = """
// Custom stream type to avoid trait object restrictions
enum Stream {
    Tcp(tokio::net::TcpStream),
    Tor(arti_client::DataStream),
}

impl tokio::io::AsyncRead for Stream {
    fn poll_read(
        mut self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
        buf: &mut tokio::io::ReadBuf<'_>,
    ) -> std::task::Poll<std::io::Result<()>> {
        match &mut *self {
            Stream::Tcp(s) => std::pin::Pin::new(s).poll_read(cx, buf),
            Stream::Tor(s) => std::pin::Pin::new(s).poll_read(cx, buf),
        }
    }
}

impl tokio::io::AsyncWrite for Stream {
    fn poll_write(
        mut self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
        buf: &[u8],
    ) -> std::task::Poll<Result<usize, std::io::Error>> {
        match &mut *self {
            Stream::Tcp(s) => std::pin::Pin::new(s).poll_write(cx, buf),
            Stream::Tor(s) => std::pin::Pin::new(s).poll_write(cx, buf),
        }
    }

    fn poll_flush(
        mut self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
    ) -> std::task::Poll<Result<(), std::io::Error>> {
        match &mut *self {
            Stream::Tcp(s) => std::pin::Pin::new(s).poll_flush(cx),
            Stream::Tor(s) => std::pin::Pin::new(s).poll_flush(cx),
        }
    }

    fn poll_shutdown(
        mut self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
    ) -> std::task::Poll<Result<(), std::io::Error>> {
        match &mut *self {
            Stream::Tcp(s) => std::pin::Pin::new(s).poll_shutdown(cx),
            Stream::Tor(s) => std::pin::Pin::new(s).poll_shutdown(cx),
        }
    }
}
"""
    # Insert after the last use statement
    lines = content.split('\n')
    last_use = 0
    for i, line in enumerate(lines):
        if line.startswith('use '):
            last_use = i
    lines.insert(last_use+1, stream_enum)
    content = '\n'.join(lines)
    print("✅ Added Stream enum.")

# 3. Replace connect_to_target function to use Stream
old_func_pattern = r'async fn connect_to_target\([^)]*\) -> Result<[^>]+> \{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}'
new_func = """    async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Stream, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Stream::Tor(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Stream::Tcp(stream))
        }
    }"""
content = re.sub(old_func_pattern, new_func, content, flags=re.DOTALL)
print("✅ Replaced connect_to_target.")

# 4. Fix unused circuit variable
content = content.replace(
    "let circuit = self.tor_client.build_circuit().await?;",
    "let _circuit = self.tor_client.build_circuit().await?;"
)

# 5. Remove unused imports (optional)
# Remove duplicate import of AsyncRead/AsyncWrite (already have one)
# Remove Ipv6Addr import if unused (we'll comment it)
content = content.replace("use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};", "use std::net::{IpAddr, Ipv4Addr, SocketAddr};")

# Remove AesKeyInit if unused
content = content.replace("use aes_gcm::aead::KeyInit as AesKeyInit;", "// use aes_gcm::aead::KeyInit as AesKeyInit;")

# Remove the duplicate AsyncRead/AsyncWrite if they appear twice (already handled)

# Write back
lib_rs.write_text(content)
print("✅ All fixes applied.")
