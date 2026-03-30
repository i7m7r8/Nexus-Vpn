#!/usr/bin/env python3
"""
Find the EXACT location of missing brace
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("Finding Missing Brace")
print("="*60)

# Split into lines and check each section
lines = content.split('\n')

# Track brace balance per section
sections = []
current_section = ""
brace_count = 0
section_start = 0

for i, line in enumerate(lines):
    # Check for section markers
    if '// ===' in line or 'pub struct' in line or 'pub enum' in line or 'impl ' in line:
        if current_section and brace_count != 0:
            sections.append((section_start, i-1, current_section, brace_count))
        current_section = line.strip()
        section_start = i
        brace_count = 0
    
    brace_count += line.count('{')
    brace_count -= line.count('}')

# Add last section
if current_section and brace_count != 0:
    sections.append((section_start, len(lines)-1, current_section, brace_count))

print("\nSections with brace imbalance:")
print("-" * 60)
for start, end, name, count in sections[:20]:  # Show first 20
    status = "❌" if count != 0 else "✅"
    print(f"Lines {start}-{end}: {status} {name[:60]} (balance: {count})")

# Find the specific area with issue
print("\n" + "="*60)
print("Checking major impl blocks...")
print("="*60)
impl_blocks = [
    ("EncryptionEngine", "impl EncryptionEngine"),
    ("SniHandler", "impl SniHandler"),
    ("TorManager", "impl TorManager"),
    ("VpnConnection", "impl VpnConnection"),
    ("VpnEngine", "impl VpnEngine"),
    ("ConnectionPool", "impl ConnectionPool"),
    ("DnsPrivacyEngine", "impl DnsPrivacyEngine"),
    ("LeakPreventionEngine", "impl LeakPreventionEngine"),
    ("BatteryOptimizer", "impl BatteryOptimizer"),
    ("NexusVpnEngine", "impl NexusVpnEngine"),
]

for name, search in impl_blocks:
    start_idx = content.find(search)
    if start_idx != -1:
        # Find the end (next impl or end of file)
        next_impl = content.find("impl ", start_idx + 1)
        if next_impl == -1:
            next_impl = len(content)
        
        block = content[start_idx:next_impl]
        open_count = block.count('{')
        close_count = block.count('}')
        balance = open_count - close_count
        
        status = "❌" if balance != 0 else "✅"
        print(f"{name}: {status} (open: {open_count}, close: {close_count}, balance: {balance})")

# Check for common missing brace patterns
print("\n" + "="*60)
print("Checking for common missing brace patterns...")
print("="*60)

patterns = [
    ("if without }", "if ", "}"),
    ("match without }", "match ", "}"),
    ("fn without }", "pub fn ", "}"),
    ("impl without }", "impl ", "}"),
]

for name, start_pattern, end_pattern in patterns:
    starts = content.count(start_pattern)
    ends = content.count(end_pattern)
    print(f"{name}: {starts} starts, {ends} ends")

print("\n" + "="*60)
print("RECOMMENDATION:")
print("="*60)
print("Add one } at the end of the file or find the impl block")print("with balance != 0 and add closing brace there")
