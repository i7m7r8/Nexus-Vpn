#!/usr/bin/env python3
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("Finding Missing Brace")
print("="*60)

lines = content.split('\n')

# Check each major section
sections = [
    ("EncryptionEngine", "impl EncryptionEngine", "impl SniHandler"),
    ("SniHandler", "impl SniHandler", "impl TorManager"),
    ("TorManager", "impl TorManager", "impl VpnConnection"),
    ("VpnConnection", "impl VpnConnection", "impl VpnEngine"),
    ("VpnEngine", "impl VpnEngine", "impl ConnectionPool"),
    ("ConnectionPool", "impl ConnectionPool", "impl DnsPrivacyEngine"),
    ("DnsPrivacyEngine", "impl DnsPrivacyEngine", "impl LeakPreventionEngine"),
    ("LeakPreventionEngine", "impl LeakPreventionEngine", "impl BatteryOptimizer"),
    ("BatteryOptimizer", "impl BatteryOptimizer", "impl NexusVpnEngine"),
    ("NexusVpnEngine", "impl NexusVpnEngine", "// ==="),
]

print("\nChecking each impl block:")
print("-"*60)

for name, start_marker, end_marker in sections:
    start_idx = content.find(start_marker)
    end_idx = content.find(end_marker, start_idx)
    
    if start_idx != -1:
        if end_idx == -1:
            end_idx = len(content)
        
        block = content[start_idx:end_idx]
        open_b = block.count('{')
        close_b = block.count('}')
        balance = open_b - close_b
        
        status = "❌" if balance != 0 else "✅"
        print(f"{name}: {status} (open={open_b}, close={close_b}, balance={balance})")

# Check enums and structs
print("\n" + "="*60)
print("Checking enums:")
print("-"*60)

enums = [
    ("VpnProtocol", "pub enum VpnProtocol", "pub enum CipherSuite"),
    ("CipherSuite", "pub enum CipherSuite", "pub enum TlsVersion"),
    ("TlsVersion", "pub enum TlsVersion", "pub enum ConnectionState"),
    ("ConnectionState", "pub enum ConnectionState", "pub struct VpnServer"),
    ("DnsMode", "pub enum DnsMode", "pub enum SplitTunnelMode"),
    ("SplitTunnelMode", "pub enum SplitTunnelMode", "pub enum BatteryProfile"),
    ("BatteryProfile", "pub enum BatteryProfile", "pub struct"),
]

for name, start_marker, end_marker in enums:
    start_idx = content.find(start_marker)
    end_idx = content.find(end_marker, start_idx)
    
    if start_idx != -1:
        if end_idx == -1:
            end_idx = len(content)
        
        block = content[start_idx:end_idx]
        open_b = block.count('{')
        close_b = block.count('}')
        balance = open_b - close_b
        
        status = "❌" if balance != 0 else "✅"
        print(f"{name}: {status} (balance={balance})")

# Find where the file ends
print("\n" + "="*60)
print("Last 30 lines of file:")
print("-"*60)
for line in lines[-30:]:
    print(line)

print("\n" + "="*60)
print("RECOMMENDATION:")
print("-"*60)
print("The file is missing 1 closing brace.")
print("Look for an impl/enum/struct block with balance=1")
print("Add }} at the end of that block")
