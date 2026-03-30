#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# Find the exact connect_to_target function and replace it
# First, locate where it is
lines = content.split('\n')
start_line = -1
brace_count = 0

for i, line in enumerate(lines):
    if 'async fn connect_to_target' in line:
        start_line = i
        break

if start_line != -1:
    # Find the end of the function by counting braces
    brace_count = 0
    end_line = start_line
    for i in range(start_line, len(lines)):
        brace_count += lines[i].count('{')
        brace_count -= lines[i].count('}')
        if brace_count == 0 and i > start_line:
            end_line = i
            break
    
    # Replace the function with a clean version
    new_function = '''    async fn connect_to_target(&self, addr: &str, port: u16) -> Result<Box<dyn tokio::io::AsyncRead + tokio::io::AsyncWrite + Unpin + Send>, anyhow::Error> {
        if let Some(tor_client) = self.tor_manager.get_client() {
            let stream = tor_client.connect((addr, port)).await?;
            Ok(Box::new(stream))
        } else {
            let stream = tokio::net::TcpStream::connect((addr, port)).await?;
            Ok(Box::new(stream))
        }
    }'''
    
    # Replace the lines
    new_lines = lines[:start_line] + [new_function] + lines[end_line+1:]
    content = '\n'.join(new_lines)
    print("✅ Replaced connect_to_target function")
else:
    print("Could not find connect_to_target function")

# Also fix the unused circuit variable (prefix with underscore)
content = content.replace(
    "let circuit = self.tor_client.build_circuit().await?;",
    "let _circuit = self.tor_client.build_circuit().await?;"
)

# Remove any duplicate imports that might cause issues
content = re.sub(r'use tokio::io::\{AsyncRead, AsyncWrite\};\n\s*use tokio::io::\{AsyncRead, AsyncWrite\};', 
                 'use tokio::io::{AsyncRead, AsyncWrite};', content)

lib_rs.write_text(content)
print("✅ Fixed lib.rs")
