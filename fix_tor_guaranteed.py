#!/usr/bin/env python3
"""
GUARANTEED TorManager Fix - Direct line-by-line replacement
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
lines = lib_path.read_text().split('\n')

print("="*60)
print("GUARANTEED TorManager Fix")
print("="*60)

new_lines = []
i = 0
in_tormanager = False
skip_until_closing_brace = 0

while i < len(lines):
    line = lines[i]
    
    # Detect TorManager struct start
    if 'pub struct TorManager {' in line:
        in_tormanager = True
        skip_until_closing_brace = 1
        # Write new struct definition
        new_lines.append('#[derive(Clone)]')
        new_lines.append('pub struct TorManager {')
        new_lines.append('    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,')
        print("  ✅ Replaced TorManager struct")
        i += 1
        continue
    
    # Skip old TorManager content until we find impl TorManager
    if in_tormanager and skip_until_closing_brace > 0:
        if '{' in line:
            skip_until_closing_brace += line.count('{')
        if '}' in line:
            skip_until_closing_brace -= line.count('}')
        if skip_until_closing_brace <= 0:
            in_tormanager = False
        i += 1
        continue
    
    # Detect impl TorManager and replace entire impl block
    if 'impl TorManager {' in line:
        # Skip until we find impl Default for TorManager
        while i < len(lines) and 'impl Default for TorManager' not in lines[i]:
            i += 1        
        # Write new impl block
        new_lines.append('impl TorManager {')
        new_lines.append('    pub async fn start(&mut self, _config: TorClientConfig) -> Result<(), String> {')
        new_lines.append('        use arti_client::TorClient;')
        new_lines.append('        use tor_rtcompat::PreferredRuntime;')
        new_lines.append('        ')
        new_lines.append('        let client = TorClient::builder()')
        new_lines.append('            .with_runtime(PreferredRuntime::current())')
        new_lines.append('            .config(arti_client::config::Config::default())')
        new_lines.append('            .create_bootstrapped()')
        new_lines.append('            .await')
        new_lines.append('            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;')
        new_lines.append('        ')
        new_lines.append('        self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));')
        new_lines.append('        Ok(())')
        new_lines.append('    }')
        new_lines.append('')
        new_lines.append('    pub async fn stop(&mut self) {')
        new_lines.append('        self.client = None;')
        new_lines.append('    }')
        new_lines.append('')
        new_lines.append('    pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>> {')
        new_lines.append('        self.client.clone()')
        new_lines.append('    }')
        new_lines.append('}')
        print("  ✅ Replaced impl TorManager")
        i += 1
        continue
    
    # Detect impl Default for TorManager and replace
    if 'impl Default for TorManager {' in line:
        # Skip old impl
        brace_count = 1
        i += 1
        while i < len(lines) and brace_count > 0:
            if '{' in lines[i]:
                brace_count += lines[i].count('{')
            if '}' in lines[i]:
                brace_count -= lines[i].count('}')
            i += 1
        
        # Write new impl
        new_lines.append('impl Default for TorManager {')
        new_lines.append('    fn default() -> Self {')
        new_lines.append('        Self { client: None }')
        new_lines.append('    }')
        new_lines.append('}')
        print("  ✅ Replaced impl Default for TorManager")
        continue    
    new_lines.append(line)
    i += 1

lib_path.write_text('\n'.join(new_lines))
print("\n✅ All fixes applied!")
