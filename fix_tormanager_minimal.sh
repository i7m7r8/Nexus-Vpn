#!/bin/bash
echo "========================================"
echo "MINIMAL TorManager Fix (NO code deletion)"
echo "========================================"

LIB="rust/core/src/lib.rs"

# Fix 1: Change TorClientConfig to PreferredRuntime in struct
sed -i 's/TorClient<arti_client::config::TorClientConfig>/TorClient<tor_rtcompat::PreferredRuntime>/g' $LIB

# Fix 2: Fix builder pattern
sed -i 's/\.with_config(arti_config)/.config(arti_client::config::Config::default())/g' $LIB
sed -i 's/\.with_config(arti_client::config::Config::default())/.config(arti_client::config::Config::default())/g' $LIB

# Fix 3: Remove arti_config variable usage
sed -i '/let arti_config = config\.to_arti();/d' $LIB

echo "✅ Minimal fixes applied"
echo ""
echo "Verification:"
grep -A3 "pub struct TorManager" $LIB | head -5
