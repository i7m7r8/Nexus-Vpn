#!/bin/bash

echo "============================================================"
echo "🦀 COMPREHENSIVE RUST AUDIT - lib.rs"
echo "============================================================"

cd rust/core

echo ""
echo "[1/6] CARGO CHECK (Compiler Errors)"
echo "------------------------------------------------------------"
cargo check --all-targets 2>&1 | head -100

echo ""
echo "[2/6] CARGO CLIPPY (Lints)"
echo "------------------------------------------------------------"
cargo clippy --all-targets -- -W clippy::all 2>&1 | head -100

echo ""
echo "[3/6] CARGO FIX (Auto-Fix)"
echo "------------------------------------------------------------"
cargo fix --allow-staged --allow-dirty 2>&1 | head -50

echo ""
echo "[4/6] BRACE BALANCE (Python)"
echo "------------------------------------------------------------"
python3 -c "
c = open('src/lib.rs').read()
o = c.count('{')
cl = c.count('}')
print(f'Open: {o}, Close: {cl}, Balance: {o-cl}')
if o == cl:
    print('✅ BALANCED')
else:
    print('❌ IMBALANCED')
"

echo ""
echo "[5/6] STRUCTURAL CHECK (Python)"
echo "------------------------------------------------------------"
python3 << 'PYEOF'
import re

content = open('src/lib.rs').read()
lines = content.split('\n')

# Find all impl blocks
impls = []
stack = []for i, line in enumerate(lines):
    if re.search(r'^impl\s+\w+', line) and '{' in line:
        stack.append((i+1, line.strip()))
    elif line.strip() == '}' and stack:
        start = stack.pop()
        impls.append((start[0], i+1, start[1]))

print(f"Found {len(impls)} impl blocks:")
for start, end, name in impls[:20]:
    print(f"  {name}: lines {start}-{end}")

# Check for nested structs in impls
errors = []
in_impl = False
impl_start = 0
impl_name = ""
for i, line in enumerate(lines):
    if re.search(r'^impl\s+\w+', line) and '{' in line:
        in_impl = True
        impl_start = i + 1
        impl_name = line.strip()
    elif in_impl and line.strip() == '}':
        in_impl = False
    elif in_impl and re.search(r'^pub\s+struct\s+\w+', line):
        errors.append((i+1, impl_name, line.strip()))

if errors:
    print(f"\n❌ {len(errors)} structs inside impl blocks:")
    for line, impl, struct in errors[:10]:
        print(f"  Line {line}: {struct} inside {impl}")
else:
    print("\n✅ No structs inside impl blocks")
PYEOF

echo ""
echo "[6/6] RUST-ANALYZER (If Installed)"
echo "------------------------------------------------------------"
if command -v rust-analyzer &> /dev/null; then
    rust-analyzer diagnostics src/lib.rs 2>&1 | head -50
else
    echo "⚠️  rust-analyzer not installed"
    echo "   Install: rustup component add rust-analyzer"
fi

echo ""
echo "============================================================"
echo "AUDIT COMPLETE"
echo "============================================================"
