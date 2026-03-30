#!/usr/bin/env python3
import re
from pathlib import Path

lib_rs = Path("rust/core/src/lib.rs")
content = lib_rs.read_text()

# 1. Restore the 'circuit' variable (was changed to '_circuit')
content = content.replace(
    "let _circuit = self.tor_client.build_circuit().await?;",
    "let circuit = self.tor_client.build_circuit().await?;"
)

# 2. Revert 'randomize' field name (was changed to '_randomize')
#    - In struct definition
content = re.sub(r'pub _randomize: bool,', 'pub randomize: bool,', content)
#    - In field access
content = content.replace("self.config._randomize", "self.config.randomize")
#    - In constructor (line 793)
content = content.replace("_randomize: true,", "randomize: true,")

# 3. Ensure Clone is derived for AppConfig
if "#[derive(Clone, Serialize, Deserialize)]" not in content:
    content = content.replace(
        "#[derive(Serialize, Deserialize)]",
        "#[derive(Clone, Serialize, Deserialize)]"
    )

# 4. Also ensure LeakTestResult has Clone if needed (already fine)
#    Not necessary but safe.

lib_rs.write_text(content)
print("✅ Final corrections applied.")
