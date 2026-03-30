#!/usr/bin/env python3
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# Change _circuit to circuit (the log line uses circuit)
content = content.replace(
    "let _circuit = self.tor_client.build_circuit().await?;",
    "let circuit = self.tor_client.build_circuit().await?;"
)

lib_rs.write_text(content)
print("✅ Fixed circuit variable.")
