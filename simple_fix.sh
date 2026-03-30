#!/bin/bash
echo "========================================"
echo "SIMPLE TorManager Fix"
echo "========================================"

LIB="rust/core/src/lib.rs"

# Fix 1: TorClientConfig → PreferredRuntime in struct
echo "1. Fixing TorManager struct..."
sed -i 's/TorClient<TorClientConfig>/TorClient<tor_rtcompat::PreferredRuntime>/g' $LIB
sed -i 's/TorClient<arti_client::config::TorClientConfig>/TorClient<tor_rtcompat::PreferredRuntime>/g' $LIB
echo "   ✅ Done"

# Fix 2: Fix get_client return type
echo "2. Fixing get_client return type..."
sed -i 's/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<arti_client::config::TorClientConfig>>>>/Option<std::sync::Arc<tokio::sync::Mutex<arti_client::TorClient<tor_rtcompat::PreferredRuntime>>>>/g' $LIB
echo "   ✅ Done"

# Fix 3: Fix builder - with_config → config
echo "3. Fixing builder pattern..."
sed -i 's/\.with_config(arti_config)/.config(arti_client::config::Config::default())/g' $LIB
sed -i 's/\.with_config(/.config(/g' $LIB
echo "   ✅ Done"

# Fix 4: Add with_runtime before .config()
echo "4. Adding with_runtime..."
sed -i 's/\.config(arti_client::config::Config::default())/.with_runtime(tor_rtcompat::PreferredRuntime::current())\n            .config(arti_client::config::Config::default())/g' $LIB
echo "   ✅ Done"

# Fix 5: Remove arti_config variable
echo "5. Removing arti_config variable..."
sed -i '/let arti_config = config\.to_arti();/d' $LIB
echo "   ✅ Done"

# Fix 6: Fix to_arti - ConfigParts → Config
echo "6. Fixing to_arti..."
sed -i 's/ConfigParts/Config/g' $LIB
echo "   ✅ Done"

# Fix 7: Fix orphaned dot on connect_tcp
echo "7. Fixing connect_tcp orphaned dot..."
sed -i 's/^\s*\.connect_tcp(addr, port)/let arti_stream = client\n                    .connect_tcp(addr, port)/g' $LIB
echo "   ✅ Done"

echo ""
echo "=== Verification ==="
grep -A3 "pub struct TorManager" $LIB | head -5
echo ""
echo "Done!"
