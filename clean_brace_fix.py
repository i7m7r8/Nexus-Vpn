#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("Clean Brace Fix")
print("="*60)

# Step 1: Remove trailing } we added earlier
print("\n1. Cleaning end of file...")
content = content.rstrip()
while content.endswith('}'):
    content = content[:-1]
    print("   Removed trailing }")
print("   ✅ End cleaned")

# Step 2: Check EncryptionEngine section specifically
print("\n2. Checking EncryptionEngine (lines 608-859)...")

lines = content.split('\n')
enc_start = 607
enc_end = 858

enc_lines = lines[enc_start:enc_end+1]
enc_content = '\n'.join(enc_lines)

open_b = enc_content.count('{')
close_b = enc_content.count('}')

print(f"   Open: {open_b}, Close: {close_b}")
print(f"   Balance: {open_b - close_b}")

if close_b > open_b:
    extra = close_b - open_b
    print(f"   ❌ {extra} extra }} found")
    
    # Remove extra } from end of EncryptionEngine
    enc_content = enc_content.rstrip()
    while enc_content.endswith('}'):
        enc_content = enc_content[:-1]
        extra -= 1
        if extra <= 0:
            break
    print(f"   ✅ Removed {close_b - enc_content.count('}')} extra }}")
    
    # Rebuild file
    before = '\n'.join(lines[:enc_start])
    after = '\n'.join(lines[enc_end+1:])
    content = before + '\n' + enc_content + '\n' + after

# Step 3: Final balance check
print("\n3. Final verification...")
total_open = content.count('{')
total_close = content.count('}')
print(f"   Open: {total_open}, Close: {total_close}")
print(f"   Balance: {total_open - total_close}")

if total_open == total_close:
    print("\n   ✅ BALANCED!")
    lib_path.write_text(content)
    print("   ✅ File saved!")
else:
    print(f"\n   ⚠️  Imbalance: {total_open - total_close}")

print("\n" + "="*60)
