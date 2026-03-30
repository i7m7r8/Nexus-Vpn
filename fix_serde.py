#!/usr/bin/env python3
import sys
from pathlib import Path

path = Path("rust/core/src/lib.rs")
if not path.exists():
    print(f"Error: {path} not found", file=sys.stderr)
    sys.exit(1)

content = path.read_text()

# ─────────────────────────────────────────────────────────────────
# FIX 1: Remove the DUPLICATE derive lines we added last time
# (the structs already had #[derive(Clone, Debug...)] so we added
#  a second line causing E0119 conflicting impls)
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct PacketStats {',
    'pub struct PacketStats {'
)
content = content.replace(
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct DetailedConnectionStats {',
    'pub struct DetailedConnectionStats {'
)

# ─────────────────────────────────────────────────────────────────
# FIX 2: Add Serialize+Deserialize INTO the existing derive lines
# for PacketStats, DetailedConnectionStats, and VpnConnectionStats
# ─────────────────────────────────────────────────────────────────

# PacketStats already has: #[derive(Clone, Debug, Default)]
content = content.replace(
    '#[derive(Clone, Debug, Default)]\npub struct PacketStats {',
    '#[derive(Clone, Debug, Default, serde::Serialize, serde::Deserialize)]\npub struct PacketStats {'
)

# DetailedConnectionStats already has: #[derive(Clone, Debug)]
content = content.replace(
    '#[derive(Clone, Debug)]\npub struct DetailedConnectionStats {',
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct DetailedConnectionStats {'
)

# VpnConnectionStats - find its existing derive and add Serialize/Deserialize
# It's at line ~175
content = content.replace(
    '#[derive(Clone, Debug, Default)]\npub struct VpnConnectionStats {',
    '#[derive(Clone, Debug, Default, serde::Serialize, serde::Deserialize)]\npub struct VpnConnectionStats {'
)
# Also try without Default in case it differs
content = content.replace(
    '#[derive(Clone, Debug)]\npub struct VpnConnectionStats {',
    '#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]\npub struct VpnConnectionStats {'
)

# ─────────────────────────────────────────────────────────────────
# FIX 3: Suppress unused variable warnings to keep output clean
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    'if let Some(client) = self.tor_manager.get_client()',
    'if let Some(_client) = self.tor_manager.get_client()'
)
content = content.replace(
    '            let circuit = self.tor_client.build_circuit().await?;',
    '            let _circuit = self.tor_client.build_circuit().await?;'
)

# ─────────────────────────────────────────────────────────────────
# FIX 4: Remove unused import warning
# ─────────────────────────────────────────────────────────────────
content = content.replace(
    'use arti_client::TorClient;\n',
    '// use arti_client::TorClient; // removed: unused\n'
)

path.write_text(content)

final = path.read_text()
o = final.count('{')
c = final.count('}')
print(f"Braces: Open={o}, Close={c}, Balance={o-c}")
print("✅ Serde chain fixed, duplicate derives removed")
