#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Remove duplicate Aead import (keep only chacha20poly1305's)
content = re.sub(r'^use aes_gcm::aead::Aead;\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^use chacha20poly1305::aead::Aead;\n', 'use chacha20poly1305::aead::Aead;\n', content)

# 2. Add a Stream enum and its implementations (to avoid trait object issues)
# Insert after the use statements, before the struct definitions
# First find a good insertion point (e.g., after the last use statement)
insertion_point = 0
lines = content.split('\n')
for i, line in enumerate(lines):
    if line.startswith('use ') and not line.strip().endswith(';'):
        insertion_point = i
# Insert after the last use
insertion_point += 1

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
lines.insert(insertion_point, stream_enum)
content = '\n'.join(lines)

# 3. Replace connect_to_target function to use Stream
new_connect = """    async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Stream, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Stream::Tor(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Stream::Tcp(stream))
        }
    }"""
# Find and replace the function
pattern = r'async fn connect_to_target\([^)]*\) -> Result<[^>]+> \{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}'
content = re.sub(pattern, new_connect, content, flags=re.DOTALL)

# 4. Remove the unused imports that might cause warnings
# (we'll leave them, they're harmless)

# 5. Write back
lib_rs.write_text(content)
print("✅ Fixed: removed duplicate Aead import, added Stream enum, replaced connect_to_target.")
