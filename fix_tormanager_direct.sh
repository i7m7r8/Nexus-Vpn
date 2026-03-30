#!/bin/bash
echo "========================================"
echo "Fixing TorManager - Direct sed replace"
echo "========================================"

LIB="rust/core/src/lib.rs"

# Step 1: Fix struct definition (remove PreferredRuntime generic)
sed -i 's/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient>>>/' $LIB
echo "✅ Fixed TorManager struct type"

# Step 2: Fix TorClient::builder() → TorClient::create_bootstrapped(config)
sed -i 's/leti_client::TorClient::builder()/let client = arti_client::TorClient::create_bootstrapped(arti_config)/' $LIB
sed -i '/\.with_runtime(tor_rtcompat::PreferredRuntime::current())/d' $LIB
sed -i '/\.with_config(arti_config)/d' $LIB
sed -i 's/\.create_bootstrapped()/.create_bootstrapped(arti_config)/' $LIB
echo "✅ Fixed TorClient creation"

# Step 3: Fix get_client return type
sed -i 's/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient>>>/' $LIB
echo "✅ Fixed get_client return type"

echo ""
echo "=== VERIFICATION ==="
grep -A15 "pub struct TorManager" $LIB | head -20
echo ""
echo "Done!"
