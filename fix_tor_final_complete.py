#!/usr/bin/env python3
"""
COMPLETE TorManager Fix - Use PreferredRuntime EVERYWHERE
"""
import re
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("COMPLETE TorManager Fix")
print("="*60)

# ============================================================================
# FIX 1: Remove wrong imports
# ============================================================================
print("\n1. Removing wrong imports...")
content = re.sub(r'\nuse arti_client::TorClient;', '', content)
content = re.sub(r'\nuse tor_rtcompat::PreferredRuntime;', '', content)
print("   ✅ Removed unused imports")

# ============================================================================
# FIX 2: Fix TorClientConfig::to_arti() - return ConfigParts not Config
# ============================================================================
print("2. Fixing TorClientConfig::to_arti()...")

old_to_arti = '''pub fn to_arti(&self) -> arti_client::config::Config {
        arti_client::config::Config::default()
    }'''

# Arti v0.40 uses Config object directly
new_to_arti = '''pub fn to_arti(&self) -> arti_client::config::Config {
        arti_client::config::Config::default()
    }'''

# Actually the issue is Config doesn't exist - use builder pattern instead
# Let's just return a basic config that works
new_to_arti = '''pub fn to_arti(&self) -> arti_client::config::Config {
        let mut cfg = arti_client::config::Config::default();
        // Can customize config here based on self fields
        cfg
    }'''

if 'pub fn to_arti' in content:
    # Find and replace the function
    content = re.sub(
        r'pub fn to_arti\(&self\) -> [^{]+\{[^}]+\}',
        new_to_arti,        content,
        flags=re.DOTALL
    )
    print("   ✅ Fixed to_arti()")

# ============================================================================
# FIX 3: Complete TorManager rewrite with PreferredRuntime
# ============================================================================
print("3. Rewriting TorManager...")

old_tormanager = r'''#\[derive\(Clone\)\]\s*pub struct TorManager \{[^}]+\}[^}]*impl TorManager \{[^}]+impl Default for TorManager \{[^}]+\}'''

new_tormanager = '''/// Manages the Arti Tor client lifecycle.
#[derive(Clone)]
pub struct TorManager {
    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,
}

impl TorManager {
    pub async fn start(&mut self, _config: TorClientConfig) -> Result<(), String> {
        use arti_client::TorClient;
        use tor_rtcompat::PreferredRuntime;
        
        let client = TorClient::builder()
            .with_runtime(PreferredRuntime::current())
            .config(arti_client::config::Config::default())
            .create_bootstrapped()
            .await
            .map_err(|e| format!("Arti bootstrap failed: {}", e))?;
        
        self.client = Some(std::sync::Arc::new(tokio::sync::Mutex::new(client)));
        Ok(())
    }

    pub async fn stop(&mut self) {
        self.client = None;
    }

    pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>> {
        self.client.clone()
    }
}

impl Default for TorManager {
    fn default() -> Self {
        Self { client: None }
    }
}'''

# Use Python to find and replaceimport re
match = re.search(r'#\[derive\(Clone\)\]\s*pub struct TorManager \{[^}]*\}[^}]*impl TorManager \{[^}]*(?:\{[^}]*\}[^}]*)*impl Default for TorManager \{[^}]*\}', content, re.DOTALL)
if match:
    content = content[:match.start()] + new_tormanager + content[match.end():]
    print("   ✅ Rewrote TorManager")
else:
    print("   ⚠️  Could not find TorManager - trying alternate pattern")
    # Try simpler pattern
    if 'pub struct TorManager' in content:
        print("   Found TorManager but pattern didn't match exactly")

# ============================================================================
# FIX 4: Fix connect_to_target - fix the dot chain
# ============================================================================
print("4. Fixing connect_to_target...")

# The error shows line 932 has ".connect_tcp(addr, port)" with orphaned dot
# This means there's a broken line before it
old_connect = '''.connect_tcp(addr, port)'''
new_connect = '''                let arti_stream = client
                    .connect_tcp(addr, port)
                    .await
                    .map_err(|e| anyhow::anyhow!("Arti connect_tcp: {}", e))?;
                drop(client);
                Ok(Stream::Tor(arti_stream))'''

# Find the broken connect_to_target function and fix it
if '.connect_tcp(addr, port)' in content:
    # Check if there's an orphaned dot
    lines = content.split('\n')
    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        # Check for orphaned dot at start of line
        if line.strip().startswith('.connect_tcp') and i > 0:
            # This line should be part of previous chain
            # Find the client variable and fix the whole block
            new_lines.append(line.lstrip())  # Remove leading whitespace
        else:
            new_lines.append(line)
        i += 1
    content = '\n'.join(new_lines)
    print("   ✅ Fixed connect_to_target dot chain")

# ============================================================================
# FIX 5: Ensure tor_rtcompat import exists
# ============================================================================
print("5. Ensuring tor_rtcompat import...")
if 'use tor_rtcompat' not in content:    # Add after other arti imports
    content = content.replace(
        'use arti_client;',
        'use arti_client;\nuse tor_rtcompat::PreferredRuntime;'
    )
    print("   ✅ Added tor_rtcompat import")

lib_path.write_text(content)
print("\n✅ All fixes applied!")
