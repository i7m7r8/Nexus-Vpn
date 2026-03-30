#!/bin/bash
echo "========================================"
echo "🚑 EMERGENCY CODE RECOVERY"
echo "========================================"

# Reset to commit BEFORE the deletion (72fcc11)
echo "Resetting to commit 72fcc11 (before deletion)..."
git reset --hard 72fcc11

echo "✅ Code recovered!"
echo ""
echo "Current TorManager status:"
grep -A5 "pub struct TorManager" rust/core/src/lib.rs | head -10
