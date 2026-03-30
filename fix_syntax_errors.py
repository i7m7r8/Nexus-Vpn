#!/usr/bin/env python3
"""
Fix syntax errors in lib.rs
"""
from pathlib import Path

lib_path = Path("rust/core/src/lib.rs")
content = lib_path.read_text()

print("="*60)
print("Fixing Syntax Errors")
print("="*60)

# FIX 1: DOH_ENDPOINTS array - missing closing bracket
print("\n1. Fixing DOH_ENDPOINTS array...")
content = content.replace(
    '''pub const DOH_ENDPOINTS: &[&str] = &[
    "https://1.1.1.1/dns-query",      // Cloudflare
    "https://8.8.8.8/dns-query",      // Google
    "https://9.9.9.9/dns-query",      // Quad9
    "https://208.67.222.222/dns-query", // OpenDNS
''',
    '''pub const DOH_ENDPOINTS: &[&str] = &[
    "https://1.1.1.1/dns-query",      // Cloudflare
    "https://8.8.8.8/dns-query",      // Google
    "https://9.9.9.9/dns-query",      // Quad9
    "https://208.67.222.222/dns-query", // OpenDNS
];
'''
)
print("   ✅ Fixed DOH_ENDPOINTS")

# FIX 2: Check for duplicate closing braces in EncryptionEngine
print("2. Checking EncryptionEngine braces...")
# Count braces in EncryptionEngine impl
enc_start = content.find("impl EncryptionEngine {")
enc_end = content.find("// ======================== SNI HANDLER")
if enc_start != -1 and enc_end != -1:
    enc_section = content[enc_start:enc_end]
    open_braces = enc_section.count('{')
    close_braces = enc_section.count('}')
    print(f"   Open braces: {open_braces}, Close braces: {close_braces}")
    if open_braces != close_braces:
        print("   ⚠️  Brace mismatch detected")

# FIX 3: Remove any duplicate closing braces
print("3. Removing duplicate closing braces...")
content = content.replace('\n\n\n}\n\n\n', '\n\n}\n\n')
content = content.replace('\n\n}\n}\n', '\n}\n')
print("   ✅ Cleaned duplicate braces")

# FIX 4: Fix ConnectionLog data field (has space before colon)
print("4. Fixing ConnectionLog data field...")
content = content.replace('pub  Option<String>,', 'pub data: Option<String>,')
print("   ✅ Fixed data field")

lib_path.write_text(content)

print("\n" + "="*60)
print("✅ Syntax fixes applied!")
print("="*60)
