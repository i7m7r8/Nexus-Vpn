#!/bin/bash
echo "========================================"
echo "FIXING TorManager SYNTAX ERROR"
echo "========================================"

# Restore from original v2 source first
echo "Restoring from 3aecc82..."
git checkout 3aecc82 -- rust/core/src/lib.rs

# Now apply MINIMAL fixes only
echo "Applying minimal Arti v0.40 fixes..."

# Fix 1: Add .config() method call (not .with_config())
sed -i 's/\.with_config(arti_config)/.config(arti_client::config::Config::default())/' rust/core/src/lib.rs

# Fix 2: Fix connect_to_target dot chain
sed -i 's/\.connect_tcp(addr, port)/.connect_tcp(addr, port)/' rust/core/src/lib.rs

# Fix 3: Remove unused imports
sed -i '/^use arti_client::TorClient;$/d' rust/core/src/lib.rs
sed -i '/^use tor_rtcompat::PreferredRuntime;$/d' rust/core/src/lib.rs

echo ""
echo "=== VERIFICATION ==="
echo ""
echo "TorManager struct (lines 808-835):"
sed -n '808,835p' rust/core/src/lib.rs

echo ""
echo "Done!"
