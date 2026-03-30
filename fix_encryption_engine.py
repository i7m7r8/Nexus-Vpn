#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()
lines = content.split('\n')

print("="*60)
print("Fix EncryptionEngine Extra Brace")
print("="*60)

# EncryptionEngine is from line 608 to 859 (1-indexed)
# In 0-indexed: 607 to 858
enc_start = 607
enc_end = 858

enc_lines = lines[enc_start:enc_end+1]
enc_content = '\n'.join(enc_lines)

open_b = enc_content.count('{')
close_b = enc_content.count('}')

print(f"\nEncryptionEngine impl block (lines 608-859):")
print(f"  Open braces: {open_b}")
print(f"  Close braces: {close_b}")
print(f"  Balance: {open_b - close_b}")

if close_b > open_b:
    extra = close_b - open_b
    print(f"\n  ❌ Found {extra} extra }} inside EncryptionEngine")
    
    # Find standalone } lines that might be duplicates
    print("\n  Looking for duplicate } lines...")
    
    fixed_lines = []
    removed = 0
    
    for i, line in enumerate(enc_lines):
        stripped = line.strip()
        
        # Check if this is a standalone } that might be duplicate
        if stripped == '}' and removed < extra:
            # Check previous non-empty line
            prev_idx = len(fixed_lines) - 1
            while prev_idx >= 0 and not fixed_lines[prev_idx].strip():
                prev_idx -= 1
            
            if prev_idx >= 0:
                prev_line = fixed_lines[prev_idx].strip()                # If previous line ends with } or ;, this } might be duplicate
                if prev_line.endswith('}') or prev_line.endswith(';') or prev_line.endswith('),'):
                    removed += 1
                    print(f"    Removed line {enc_start + i + 1}: {line}")
                    continue
        
        fixed_lines.append(line)
    
    if removed > 0:
        # Rebuild file
        before = '\n'.join(lines[:enc_start])
        after = '\n'.join(lines[enc_end+1:])
        content = before + '\n' + '\n'.join(fixed_lines) + '\n' + after
        
        lib_path.write_text(content)
        print(f"\n  ✅ Removed {removed} extra }} from EncryptionEngine")
    else:
        print("\n  ⚠️  Could not auto-detect duplicate, checking methods...")
        
        # Check each method individually
        method_starts = []
        for i, line in enumerate(enc_lines):
            if 'pub fn ' in line or '    fn ' in line:
                method_starts.append(i)
        
        print(f"\n  Found {len(method_starts)} methods in EncryptionEngine")
        
        for idx in range(len(method_starts)):
            start = method_starts[idx]
            end = method_starts[idx + 1] if idx + 1 < len(method_starts) else len(enc_lines)
            
            method_lines = enc_lines[start:end]
            method_content = '\n'.join(method_lines)
            
            m_open = method_content.count('{')
            m_close = method_content.count('}')
            m_balance = m_open - m_close
            
            if m_balance != 0:
                print(f"    Method at line {enc_start + start + 1}: balance = {m_balance}")

# Final verification
print("\n" + "="*60)
print("Final Verification:")
print("="*60)

final_content = lib_path.read_text()
total_open = final_content.count('{')
total_close = final_content.count('}')
print(f"Total open: {total_open}")
print(f"Total close: {total_close}")
print(f"Balance: {total_open - total_close}")

if total_open == total_close:
    print("\n✅ FILE IS BALANCED!")
else:
    print(f"\n⚠️  Still imbalanced by {total_open - total_close}")
