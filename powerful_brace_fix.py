#!/usr/bin/env python3
"""
POWERFUL Brace Balance Fix - Finds EXACT location of imbalance
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("POWERFUL Brace Balance Fix")
print("="*60)

# First, REMOVE the extra } we added at end
print("\n1. Removing extra } added at end of file...")
content = content.rstrip()
if content.endswith('}'):
    content = content[:-1]
    print("   ✅ Removed trailing }")
print("   ✅ Cleaned end of file")

# Split into lines for analysis
lines = content.split('\n')

# Find ALL impl blocks and check their brace balance
print("\n2. Analyzing ALL impl blocks...")
print("-"*60)

impl_blocks = []
current_impl = None
impl_start = 0
brace_depth = 0
impl_brace_count = 0

for i, line in enumerate(lines):
    # Check for impl block start
    if 'impl ' in line and '{' in line:
        if current_impl:
            impl_blocks.append((current_impl, impl_start, i-1, impl_brace_count))
        current_impl = line.strip()[:50]
        impl_start = i
        impl_brace_count = 0
        brace_depth = 1
    elif current_impl:
        impl_brace_count += line.count('{')
        impl_brace_count -= line.count('}')
        
        # Check for impl block end
        if brace_depth > 0 and '}' in line:            # Simple check - if line is just }, impl might be ending
            if line.strip() == '}' and impl_brace_count == 0:
                impl_blocks.append((current_impl, impl_start, i, impl_brace_count))
                current_impl = None
                impl_brace_count = 0

# Add last impl if not closed
if current_impl:
    impl_blocks.append((current_impl, impl_start, len(lines)-1, impl_brace_count))

# Show all impl blocks with imbalance
for name, start, end, balance in impl_blocks:
    status = "❌" if balance != 0 else "✅"
    if balance != 0:
        print(f"Lines {start}-{end}: {status} {name}")
        print(f"   Balance: {balance} (positive = missing }}, negative = extra }})")

# Specifically check EncryptionEngine (lines 608-859 per error)
print("\n3. Checking EncryptionEngine impl (lines 608-859)...")
print("-"*60)

enc_start = 607  # 0-indexed
enc_end = 858    # 0-indexed

enc_lines = lines[enc_start:enc_end+1]
enc_content = '\n'.join(enc_lines)

open_braces = enc_content.count('{')
close_braces = enc_content.count('}')

print(f"Open braces: {{ = {open_braces}")
print(f"Close braces: }} = {close_braces}")
print(f"Balance: {open_braces - close_braces}")

if open_braces > close_braces:
    print(f"❌ MISSING {open_braces - close_braces} closing brace(s)")
elif close_braces > open_braces:
    print(f"❌ EXTRA {close_braces - open_braces} closing brace(s)")
    print(f"   Need to REMOVE {close_braces - open_braces} }} from this block")
else:
    print("✅ EncryptionEngine is balanced!")

# Find methods inside EncryptionEngine
print("\n4. Finding methods inside EncryptionEngine...")
print("-"*60)

method_starts = []
for i, line in enumerate(enc_lines):
    if 'pub fn ' in line or 'fn ' in line:
        method_starts.append((enc_start + i, line.strip()[:60]))
for line_num, method in method_starts[:20]:
    print(f"   Line {line_num}: {method}")

# Check each method for brace balance
print("\n5. Checking each method's brace balance...")
print("-"*60)

for idx in range(len(method_starts)):
    start_line = method_starts[idx][0]
    if idx + 1 < len(method_starts):
        end_line = method_starts[idx + 1][0] - 1
    else:
        end_line = enc_end
    
    method_lines = lines[start_line:end_line+1]
    method_content = '\n'.join(method_lines)
    
    open_b = method_content.count('{')
    close_b = method_content.count('}')
    balance = open_b - close_b
    
    if balance != 0:
        print(f"   ❌ Lines {start_line}-{end_line}: balance = {balance}")
        print(f"      {method_starts[idx][1][:50]}...")

# FIX: Remove extra } from EncryptionEngine
print("\n6. Attempting automatic fix...")
print("-"*60)

if close_braces > open_braces:
    extra_count = close_braces - open_braces
    print(f"Found {extra_count} extra }} in EncryptionEngine")
    
    # Find and remove standalone } lines in the impl block
    fixed_enc_lines = []
    removed = 0
    for i, line in enumerate(enc_lines):
        stripped = line.strip()
        # Remove standalone } if we still need to remove
        if stripped == '}' and removed < extra_count:
            # Check if this is a duplicate (previous non-empty line also ends with })
            if fixed_enc_lines:
                prev = fixed_enc_lines[-1].strip()
                if prev.endswith('}') or prev.endswith(';'):
                    removed += 1
                    print(f"   Removed extra }} at line {enc_start + i}")
                    continue
        fixed_enc_lines.append(line)
        if removed > 0:
        # Replace the EncryptionEngine section in content
        before_enc = '\n'.join(lines[:enc_start])
        after_enc = '\n'.join(lines[enc_end+1:])
        content = before_enc + '\n' + '\n'.join(fixed_enc_lines) + '\n' + after_enc
        print(f"   ✅ Removed {removed} extra }} from EncryptionEngine")
    else:
        print("   ⚠️  Could not auto-fix, manual review needed")

# Final verification
print("\n7. Final verification...")
print("-"*60)

final_open = content.count('{')
final_close = content.count('}')
print(f"Total open braces: {{ = {final_open}")
print(f"Total close braces: }} = {final_close}")
print(f"Balance: {final_open - final_close}")

if final_open == final_close:
    print("\n✅ FILE IS BALANCED!")
    lib_path.write_text(content)
    print("✅ File saved!")
else:
    print(f"\n⚠️  Still imbalanced by {final_open - final_close}")
    print("   Manual review required")

print("\n" + "="*60)
