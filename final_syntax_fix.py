#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# Find and fix the connect_to_target function
# We'll replace the entire function with a clean version
connect_to_target = '''    async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Box<dyn tokio::io::AsyncRead + tokio::io::AsyncWrite + Unpin + Send>, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Box::new(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Box::new(stream))
        }
    }'''

# Remove any existing connect_to_target function
# Match from the function start to the next function or end of impl
pattern = r'async fn connect_to_target\([^)]*\) -> Result<[^>]+> \{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}'
content = re.sub(pattern, connect_to_target, content, flags=re.DOTALL)

# Also remove any extra closing braces that might have been added
# Count braces to ensure balance
lines = content.split('\n')
brace_count = 0
new_lines = []
for line in lines:
    brace_count += line.count('{') - line.count('}')
    new_lines.append(line)
    # If we go negative, we have extra closing braces
if brace_count < 0:
    # Remove extra closing braces at the end
    fixed_lines = []
    for line in reversed(new_lines):
        if line.strip() == '}':
            continue
        fixed_lines.insert(0, line)
    new_lines = fixed_lines

content = '\n'.join(new_lines)

lib_rs.write_text(content)
print("✅ Fixed connect_to_target function and brace balance")
