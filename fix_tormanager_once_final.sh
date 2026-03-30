#!/bin/bash
echo "========================================"
echo "TorManager - ONCE AND FOR ALL FIX"
echo "========================================"

LIB="rust/core/src/lib.rs"

# Fix 1: Add generic to TorClient in struct
sed -i 's/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient>>>/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>/' $LIB
echo "✅ Added generic to TorClient"

# Fix 2: Remove arti_config from create_bootstrapped call
sed -i 's/\.create_bootstrapped(arti_config)/.create_bootstrapped()/' $LIB
echo "✅ Fixed create_bootstrapped() call"

# Fix 3: Remove arti_config variable (not needed)
sed -i '/let arti_config = config\.to_arti();/d' $LIB
echo "✅ Removed unused arti_config"

# Fix 4: Fix get_client return type
sed -i 's/pub fn get_client(\&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient>>>/pub fn get_client(\&self) -> Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>/' $LIB
echo "✅ Fixed get_client return type"

echo ""
echo "=== VERIFICATION ==="
grep -A25 "pub struct TorManager" $LIB
echo ""
echo "Done!"
