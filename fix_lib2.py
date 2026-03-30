#!/usr/bin/env python3
import re, sys, shutil
from pathlib import Path

TARGET = Path("rust/core/src/lib.rs")
if not TARGET.exists():
    print(f"ERROR: {TARGET} not found. Run from Nexus-Vpn repo root.", file=sys.stderr)
    sys.exit(1)

shutil.copy2(TARGET, TARGET.with_suffix(".rs.bak"))

with open(TARGET, "r") as f:
    content = f.read()

def apply_until_stable(text, fn):
    while True:
        new = fn(text)
        if new == text: return text
        text = new

def _dot_chain(text):
    text = re.sub(r'"[)]*\s*\+\s*"\."\s*\+\s*String::from\("', '.', text)
    text = re.sub(r'"[)]*\s*\+\s*"\."\s*\+\s*"', '.', text)
    return text

def _lit_dot(text):
    return re.sub(r'"([^"\n]*)"\s*\+\s*"\."\s*\+\s*"([^"\n]*)"', r'"\1.\2"', text)

def _lit_concat(text):
    return re.sub(r'"([^"\n]*)"\s*\+\s*"\.([^"\n]*)"', r'"\1.\2"', text)

def _fmt_simp(text):
    return re.sub(r'format!\("\{\}\{\}",\s*"([^"]*)",\s*"([^"]*)"\)', r'"\1\2"', text)

# Phase 1: dot-chain BEFORE unwrapping String::from
content = apply_until_stable(content, _dot_chain)

# Phase 2: unwrap ALL String::from("x") → "x"  (all are corruption artifacts)
content = apply_until_stable(content, lambda t: re.sub(r'String::from\("', '"', t))

# Phase 3: literal string fixes
content = apply_until_stable(content, _lit_dot)
content = apply_until_stable(content, _lit_concat)
content = apply_until_stable(content, _dot_chain)
content = apply_until_stable(content, _lit_dot)
content = apply_until_stable(content, _lit_concat)

# Phase 4: format! simplification
content = apply_until_stable(content, _fmt_simp)
content = re.sub(r'format!\(format!\("([^"]+)"\),\s*([^)]+)\),\s*\2\)', r'format!("\1", \2)', content)
content = re.sub(r'format!\(format!\("([^"]+)",\s*([^)]+)\),\s*\2\)', r'format!("\1", \2)', content)

# Phase 5: fix extra ) in .join() from simplified format!
content = re.sub(r'\.join\("([^"]+)"\)\);', r'.join("\1");', content)
content = re.sub(r'\.join\("([^"]+)"\)\)', r'.join("\1")', content)

# Phase 6: leading garbage close-parens in string literals
content = re.sub(r'"[)]{1,10}([a-zA-Z0-9_])', r'"\1', content)

# Phase 7: raw string corruption r")%Y... → r"%Y...
content = re.sub(r'r"[)]+(%[YmdHMS_0-9:%-]+)"', r'r"\1"', content)

# Phase 8: JSON/map key: ")stats" → "stats"
content = re.sub(r'(?<![a-zA-Z0-9_])"[)]+([a-zA-Z_][a-zA-Z0-9_]*)"', r'"\1"', content)

# Phase 9: domain list: "facebook.com").to_string() → "facebook.com".to_string()
content = re.sub(r'"([a-zA-Z0-9._-]+)"\)\.to_string\(\)', r'"\1".to_string()', content)

# Phase 10: log_connection_event extra close parens from nested String::from wrappers
content = re.sub(r'\("([^"]+)"\)\)\),\s*', r'("\1", ', content)
content = re.sub(r'\("([^"]+)"\)\),\s*', r'("\1", ', content)

# Phase 11: stray duplicate function tail artifact
content = re.sub(r'\}\"\), e\)\)\?;\s*Ok\(Stream::Tcp\(stream\)\)\s*\}\s*\}', '}', content)

# Phase 12: Rust range operator 0..n that got corrupted
content = re.sub(r'(\d)"[)]*\s*\+\s*"\.\.', r'\1..', content)

# Phase 13: assert_eq extra )
content = re.sub(r'assert_eq!\(([^,]+),\s*"([^"]+)"\)\);', r'assert_eq!(\1, "\2");', content)

# Final passes
content = apply_until_stable(content, _dot_chain)
content = apply_until_stable(content, _lit_dot)
content = apply_until_stable(content, _lit_concat)
content = apply_until_stable(content, _fmt_simp)

lines = content.split('\n')
sf_lines = [i+1 for i, l in enumerate(lines) if 'String::from' in l]
print(f"Remaining String::from: {sf_lines if sf_lines else 'NONE - all clear!'}")

with open(TARGET, "w") as f:
    f.write(content)
print(f"Done. {len(lines)} lines written to {TARGET}")
