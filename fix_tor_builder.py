import re

filepath = 'rust/core/src/lib.rs'
content = open(filepath).read()

# Fix TorClient builder
old_builder = '''let client = TorClient::builder()
            .with_runtime(PreferredRuntime::current())
            .config(ArtiConfig::default())
            .create_bootstrapped()
            .await'''

new_builder = '''let client = TorClient::builder()
            .config(ArtiConfig::default())
            .create_bootstrapped()
            .await'''

content = content.replace(old_builder, new_builder)

# Fix connect_tcp → connect
content = content.replace('.connect_tcp((addr, port))', '.connect(format!("{}:{}", addr, port))')

# Fix is_bootstrapped_blocking → is_ready
content = content.replace('is_bootstrapped_blocking()', 'is_ready().await')

# Fix mut rng
content = content.replace('let rng = self.rng.lock().await;', 'let mut rng = self.rng.lock().await;')

# Fix Box::leak
content = content.replace('Box::leak(cstring).as_ptr()', 'Box::leak(Box::new(cstring)).as_ptr()')

# Fix ServerCapabilities
content = content.replace(
    '#[derive(Clone, Debug, Default, Serialize, Deserialize)]\npub struct ServerCapabilities {',
    '#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]\npub struct ServerCapabilities {'
)

# Fix SplitTunnelMode Default
content = content.replace(
    'pub enum SplitTunnelMode {',
    'pub enum SplitTunnelMode {\n    #[default]'
)
content = content.replace(
    '    IncludeOnly,',
    '    IncludeOnly,'
)

# Fix Instant serialization - remove Serialize/Deserialize from PooledConnection
content = re.sub(
    r'#\[derive\(Clone, Debug, Serialize, Deserialize\)\]\npub struct PooledConnection',
    '#[derive(Clone, Debug)]\npub struct PooledConnection',
    content
)

open(filepath, 'w').write(content)
print("✅ Tor builder and other fixes applied")
