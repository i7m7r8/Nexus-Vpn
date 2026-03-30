#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Replace std::sync::{Mutex,RwLock} with tokio::sync versions
content = re.sub(r'use std::sync::\{Mutex, RwLock\};', 'use tokio::sync::{Mutex, RwLock};', content)
content = re.sub(r'use std::sync::Mutex;', 'use tokio::sync::Mutex;', content)
content = re.sub(r'use std::sync::RwLock;', 'use tokio::sync::RwLock;', content)

# 2. Add missing Aead imports for encryption
aead_imports = [
    "use chacha20poly1305::aead::Aead;",
    "use aes_gcm::aead::Aead;"
]
for imp in aead_imports:
    if imp not in content:
        # Insert after existing use statements
        lines = content.split('\n')
        for i, line in enumerate(lines):
            if line.startswith('use ') and not line.startswith('use chacha20poly1305::aead::Aead'):
                lines.insert(i+1, imp)
                break
        content = '\n'.join(lines)

# 3. Fix circuit variable: replace `let _circuit` with `let circuit`
content = content.replace(
    "let _circuit = self.tor_client.build_circuit().await?;",
    "let circuit = self.tor_client.build_circuit().await?;"
)

# 4. Fix the trait object in connect_to_target: use `Box<dyn AsyncRead + AsyncWrite + Unpin + Send>`
#    Already have that, but need to ensure imports.
#    The error E0225 is about mixing auto and non-auto traits. Actually we need a trait alias, but we'll use a boxed trait object.
#    The current code uses `Box<dyn AsyncRead + AsyncWrite + Unpin + Send>`. That should work if all traits are object-safe.
#    `AsyncRead` and `AsyncWrite` are object-safe. `Unpin` is auto-trait, `Send` is auto-trait. It's allowed. The error earlier said "only auto traits can be used as additional traits in a trait object". That's because we had `+ tokio::io::AsyncWrite` which is not auto. But we now use `+ AsyncWrite` (imported). Let's keep as is. No change needed.

# 5. Remove unused imports (like the extra KeyInit from aes_gcm if not needed)
#    We'll leave them for now.

# 6. Remove any duplicate imports that might cause warnings (like duplicate AsyncRead/AsyncWrite)
content = re.sub(r'use tokio::io::\{AsyncRead, AsyncWrite\};\n\s*use tokio::io::\{AsyncRead, AsyncWrite\};', 
                 'use tokio::io::{AsyncRead, AsyncWrite};', content)

# 7. Ensure that `tokio::time::sleep` is imported (it is already via `use tokio::time::sleep;` but we have it)
#    No change needed.

# 8. Also, the `RwLock` usage in the code uses `write().await` and `read().await`, which is correct for tokio::sync::RwLock.
#    So the previous change to tokio::sync will make those `.await` valid.

# Write back
lib_rs.write_text(content)
print("✅ Fixed imports and variable usage.")
