#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Fix circuit variable: change from _circuit to circuit (so it's used)
content = content.replace(
    "let _circuit = self.tor_client.build_circuit().await?;",
    "let circuit = self.tor_client.build_circuit().await?;"
)

# 2. Fix the trait object in connect_to_target - replace with Stream enum
# First, ensure the Stream enum is present (add if missing)
if "enum Stream {" not in content:
    # Insert after the use statements
    lines = content.split('\n')
    last_use = 0
    for i, line in enumerate(lines):
        if line.startswith('use '):
            last_use = i
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
    lines.insert(last_use + 1, stream_enum)
    content = '\n'.join(lines)
    print("✅ Added Stream enum.")

# 3. Replace the connect_to_target function to return Stream
# Find the function and replace it
func_pattern = r'async fn connect_to_target\([^)]*\) -> Result<[^>]+> \{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}'
new_func = """    async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Stream, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Stream::Tor(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Stream::Tcp(stream))
        }
    }"""
content = re.sub(func_pattern, new_func, content, flags=re.DOTALL)
print("✅ Replaced connect_to_target to return Stream.")

# 4. Remove the unused AsyncRead/AsyncWrite imports if they're not needed elsewhere
# But we still need them for the Stream impl, so keep them.

# 5. Also ensure the circuit variable is used (we already changed _circuit to circuit)
# So the log line will now have access to circuit.

lib_rs.write_text(content)
print("✅ All final fixes applied.")
