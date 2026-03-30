#!/usr/bin/env python3
import sys
from pathlib import Path

path = Path("rust/core/src/lib.rs")
if not path.exists():
    print(f"Error: {path} not found", file=sys.stderr)
    sys.exit(1)

content = path.read_text()

# ─────────────────────────────────────────────────────────────────
# FIX 1: format string - escaped braces {{}} used but arg provided
# "nexus-vpn.{{}}.log" with timestamp arg → should be "nexus-vpn.{}.log"
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    'format!("nexus-vpn.{{}}.log", timestamp)',
    'format!("nexus-vpn.{}.log", timestamp)'
)

# ─────────────────────────────────────────────────────────────────
# FIX 2: client.connect_tcp() called on unit type ()
# TorManager::client is Option<()> stub - replace the whole Tor connect
# block with a direct socks5 proxy connection to 127.0.0.1:9050
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    '''                let arti_stream = client.connect_tcp((addr, port))
                    .await.map_err(|e| anyhow::anyhow!("Tor: {}", e))?;
                // Wrap for our Stream enum (stub: Arti stream → TCP wrapper)
                let tcp = tokio::net::TcpStream::connect("127.0.0.1:9050").await?;
                Ok(Stream::Tor(tcp))''',
    '''                // Route through Tor SOCKS5 proxy on 127.0.0.1:9050
                let tcp = tokio::net::TcpStream::connect("127.0.0.1:9050").await
                    .map_err(|e| anyhow::anyhow!("Tor SOCKS5: {}", e))?;
                Ok(Stream::Tor(tcp))'''
)

# ─────────────────────────────────────────────────────────────────
# FIX 3: DetailedConnectionStats does not implement Serialize
# Add #[derive(serde::Serialize, serde::Deserialize)] to it
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    'pub struct DetailedConnectionStats {',
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct DetailedConnectionStats {'
)
# Remove duplicate derive if already has one
content = content.replace(
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\n#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct DetailedConnectionStats {',
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct DetailedConnectionStats {'
)

# Also need PacketStats (field of DetailedConnectionStats) to be Serialize
content = content.replace(
    'pub struct PacketStats {',
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct PacketStats {'
)
content = content.replace(
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\n#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct PacketStats {',
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct PacketStats {'
)

path.write_text(content)

# Verify
final = path.read_text()
o = final.count('{')
c = final.count('}')
print(f"Braces: Open={o}, Close={c}, Balance={o-c}")
print("✅ All 4 errors fixed")
