#!/usr/bin/env python3
"""
Targeted TorManager Fix - Lines 732-850
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
lines = lib_path.read_text().split('\n')

print("="*60)
print("TARGETED TorManager Fix (lines 732-850)")
print("="*60)

new_lines = []
i = 0

while i < len(lines):
    line = lines[i]
    
    # ========================================================================
    # FIX 1: TorManager struct (line 732) - change generic to PreferredRuntime
    # ========================================================================
    if i == 731 and 'pub struct TorManager {' in line:
        new_lines.append('#[derive(Clone)]')
        new_lines.append('pub struct TorManager {')
        new_lines.append('    client: Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>,')
        print("  ✅ Fixed TorManager struct (line 732)")
        i += 4  # Skip old struct definition
        continue
    
    # ========================================================================
    # FIX 2: get_client() return type (around line 830)
    # ========================================================================
    if 'pub fn get_client(&self)' in line and 'TorClientConfig' in line:
        new_lines.append('    pub fn get_client(&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>> {')
        print("  ✅ Fixed get_client() return type (line 830)")
        i += 1
        continue
    
    # ========================================================================
    # FIX 3: TorManager::start() builder pattern (around line 817)
    # ========================================================================
    if '.with_config(arti_config)' in line:
        new_lines.append('            .config(arti_client::config::Config::default())')
        print("  ✅ Fixed builder pattern (line 817)")
        i += 1
        continue
    
    if '.with_config(' in line and 'config' in line.lower():        new_lines.append('            .config(arti_client::config::Config::default())')
        i += 1
        continue
    
    # ========================================================================
    # FIX 4: to_arti() function (lines 155-156) - fix return type
    # ========================================================================
    if 'pub fn to_arti(&self) -> arti_client::config::Config' in line:
        # Just return Config directly
        new_lines.append('    pub fn to_arti(&self) -> arti_client::config::Config {')
        print("  ✅ Fixed to_arti() signature (line 155)")
        i += 1
        continue
    
    if 'arti_client::config::Config::default()' in line and 'to_arti' not in line:
        new_lines.append('        arti_client::config::Config::default()')
        i += 1
        continue
    
    # ========================================================================
    # FIX 5: Fix orphaned dot on connect_tcp (line 932)
    # ========================================================================
    if line.strip() == '.connect_tcp(addr, port)' and i > 0:
        # This should be part of a method chain - check previous line
        if 'client' in lines[i-1].lower() or 'lock' in lines[i-1].lower():
            new_lines.append('                let arti_stream = client')
            new_lines.append('                    .connect_tcp(addr, port)')
            new_lines.append('                    .await')
            new_lines.append('                    .map_err(|e| anyhow::anyhow!("Arti connect_tcp: {}", e))?;')
            new_lines.append('                drop(client);')
            new_lines.append('                Ok(Stream::Tor(arti_stream))')
            print("  ✅ Fixed connect_to_target (line 932)")
            i += 1
            continue
    
    # ========================================================================
    # FIX 6: Remove arti_config variable if unused
    # ========================================================================
    if 'let arti_config = config.to_arti();' in line:
        # Keep it for now, it's used by .config()
        pass
    
    new_lines.append(line)
    i += 1

lib_path.write_text('\n'.join(new_lines))
print("\n✅ TARGETED FIX COMPLETE!")
