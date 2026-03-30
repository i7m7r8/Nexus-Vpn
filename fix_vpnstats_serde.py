#!/usr/bin/env python3
import sys
from pathlib import Path

path = Path("rust/core/src/lib.rs")
if not path.exists():
    print(f"Error: {path} not found", file=sys.stderr)
    sys.exit(1)

content = path.read_text()

# VpnConnectionStats has TWO separate derive lines:
# #[derive(Clone, Debug)]
# #[derive(Default)]
# Merge them into one and add Serialize+Deserialize
content = content.replace(
    '#[derive(Clone, Debug)]\n#[derive(Default)]\npub struct VpnConnectionStats {',
    '#[derive(Clone, Debug, Default, serde::Serialize, serde::Deserialize)]\npub struct VpnConnectionStats {'
)

path.write_text(content)

# Verify the replace worked
final = path.read_text()
if 'serde::Serialize' in final and 'VpnConnectionStats' in final:
    idx = final.find('pub struct VpnConnectionStats')
    snippet = final[max(0,idx-80):idx+40]
    print(f"✅ VpnConnectionStats derive line:\n{snippet}")
else:
    print("❌ Replace did not match - check manually", file=sys.stderr)
    sys.exit(1)

o = final.count('{')
c = final.count('}')
print(f"Braces: Open={o}, Close={c}, Balance={o-c}")
