#!/bin/bash
echo "========================================"
echo "TorManager Fix Using SED"
echo "========================================"

LIB="rust/core/src/lib.rs"

# Show current state
echo ""
echo "=== BEFORE ==="
grep -n "TorClientConfig\|TorClient<" rust/core/src/lib.rs | head -10

# ============================================================================
# FIX 1: TorManager struct - Line 809
# ============================================================================
echo ""
echo "1. Fixing TorManager struct..."
sed -i 's/TorClient<arti_client::config::TorClientConfig>/TorClient<tor_rtcompat::PreferredRuntime>/g' $LIB
sed -i 's/TorClient<TorClientConfig>/TorClient<tor_rtcompat::PreferredRuntime>/g' $LIB

# ============================================================================
# FIX 2: get_client return type - Line 830
# ============================================================================
echo "2. Fixing get_client return type..."
sed -i 's/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>/g' $LIB

# ============================================================================
# FIX 3: Builder pattern - Line 817
# ============================================================================
echo "3. Fixing builder pattern..."
sed -i 's/\.with_config(arti_config)/.config(arti_client::config::Config::default())/g' $LIB
sed -i 's/\.with_config(/.config(/g' $LIB

# ============================================================================
# FIX 4: Add with_runtime
# ============================================================================
echo "4. Adding with_runtime..."
sed -i 's/let client = arti_client::TorClient::builder()/let client = arti_client::TorClient::builder()\n            .with_runtime(tor_rtcompat::PreferredRuntime::current())/g' $LIB

# ============================================================================
# FIX 5: Remove arti_config variable
# ============================================================================
echo "5. Removing arti_config variable..."
sed -i '/let arti_config = config\.to_arti();/d' $LIB

# ============================================================================
# FIX 6: Fix to_arti - remove the function (Config doesn't exist in v0.40)
# ============================================================================
echo "6. Removing to_arti function..."
# Remove the entire to_arti function
sed -i '/pub fn to_arti/,/^    }/d' $LIB

# ============================================================================
# FIX 7: Fix orphaned dot on connect_tcp - Line 932
# ============================================================================
echo "7. Fixing connect_tcp..."
sed -i 's/^\s*\.connect_tcp(addr, port)/let arti_stream = client\n                    .connect_tcp(addr, port)/g' $LIB

# ============================================================================
# FIX 8: Remove unused imports
# ============================================================================
echo "8. Removing unused imports..."
sed -i '/^use arti_client::TorClient;$/d' $LIB
sed -i '/^use tor_rtcompat::PreferredRuntime;$/d' $LIB

# Show after state
echo ""
echo "=== AFTER ==="
grep -n "PreferredRuntime\|TorClient<" rust/core/src/lib.rs | head -10

echo ""
echo "========================================"
echo "✅ SED FIX COMPLETE"
echo "========================================"
