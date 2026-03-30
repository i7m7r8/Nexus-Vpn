import re

filepath = 'rust/core/src/lib.rs'
content = open(filepath).read()

print("🔧 Fixing remaining errors...")

# 1. Remove duplicate PreferredRuntime import
content = re.sub(r'\nuse tor_rtcompat::PreferredRuntime;', '', content, count=1)
print("✅ Removed duplicate PreferredRuntime import")

# 2. Fix SplitTunnelMode Default - add manual impl
content = content.replace('pub enum SplitTunnelMode {\n    #[default]', 'pub enum SplitTunnelMode {')

# Add manual Default impl if not exists
if 'impl Default for SplitTunnelMode' not in content:
    content = content.replace(
        'pub enum SplitTunnelMode {\n    IncludeOnly,\n    ExcludeOnly,\n}',
        'pub enum SplitTunnelMode {\n    IncludeOnly,\n    ExcludeOnly,\n}\n\nimpl Default for SplitTunnelMode {\n    fn default() -> Self {\n        SplitTunnelMode::ExcludeOnly\n    }\n}'
    )
print("✅ Fixed SplitTunnelMode Default")

# 3. Fix await in non-async function
content = content.replace(
    'pub fn set_sni_config(&mut self, sni_enabled: bool, custom_sni: String, tor_enabled: bool) {',
    'pub async fn set_sni_config(&mut self, sni_enabled: bool, custom_sni: String, tor_enabled: bool) {'
)
print("✅ Made set_sni_config async")

# 4. Add ArtiConfig import
content = content.replace(
    'use arti_client::TorClient;',
    'use arti_client::TorClient;\nuse arti_client::config::Config as ArtiConfig;'
)
print("✅ Added ArtiConfig import")

# 5. Fix Stream return type
content = content.replace('Result<tor_rtcompat::general::Stream, String>', 'Result<arti_client::DataStream, String>')
content = content.replace('tor_rtcompat::general::Stream', 'arti_client::DataStream')
print("✅ Fixed Stream type to DataStream")

# 6. Fix conns borrow issue
old_conn = '''if let Some(conn) = conns.get(&conn_id) {
            if conn.is_active {
                let mut updated = conn.clone();
                updated.last_used = Instant::now();
                conns.insert(conn_id.clone(), updated);
                return Ok(conn.clone());
            }
        }'''
new_conn = '''let conn_exists = conns.contains_key(&conn_id);
        if conn_exists {
            if let Some(conn) = conns.get(&conn_id) {
                if conn.is_active {
                    let mut updated = conn.clone();
                    updated.last_used = Instant::now();
                    let conn_id_clone = conn_id.clone();
                    conns.insert(conn_id_clone, updated);
                    return Ok(conns.get(&conn_id).unwrap().clone());
                }
            }
        }'''
content = content.replace(old_conn, new_conn)
print("✅ Fixed conns borrow issue")

# 7. Remove Debug derive from Stream enum  
content = content.replace('#[derive(Debug)]\npub enum Stream', '// #[derive(Debug)]\npub enum Stream')
print("✅ Removed Debug from Stream enum")

# 8. Add PasswordHasher and PasswordVerifier traits
content = content.replace(
    'use argon2::{Argon2, password_hash::SaltString};',
    'use argon2::{Argon2, password_hash::SaltString, PasswordHasher, PasswordVerifier};'
)
print("✅ Added argon2 traits")

# 9. Fix Hmac ambiguity
content = content.replace(
    'let mut mac = Hmac::<Sha256>::new_from_slice(key)',
    'let mut mac = <Hmac<Sha256> as KeyInit>::new_from_slice(key)'
)
print("✅ Fixed Hmac ambiguity")

# 10. Comment unused imports
unused = ['use hkdf::Hkdf;', 'use bcrypt::{hash, verify, DEFAULT_COST};', 'use rustls::pki_types::ServerName;', 'use webpki_roots::TLS_SERVER_ROOTS;']
for imp in unused:
    content = content.replace(imp, '// ' + imp)
print("✅ Commented unused imports")

open(filepath, 'w').write(content)
print("\n✅ All fixes applied!")
