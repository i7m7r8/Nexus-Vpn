#!/usr/bin/env python3
import sys
from pathlib import Path

def fix_brace_balance(file_path):
    with open(file_path, 'r') as f:
        lines = f.readlines()
    
    new_lines = []
    brace_count = 0
    problematic_line = None
    
    for i, line in enumerate(lines, start=1):
        # Count braces in this line
        open_braces = line.count('{')
        close_braces = line.count('}')
        
        # Update count with open braces first? It depends.
        # But we need to detect when close > open+current.
        # We'll update the count after processing the line.
        # But we must consider the net effect.
        new_brace_count = brace_count + open_braces - close_braces
        
        if new_brace_count < 0:
            # This line would make brace count negative => too many closing braces.
            # The problematic line is this one (or maybe the previous one if it's the first).
            problematic_line = i
            print(f"Error: Brace count would become negative at line {i}: {line.strip()}")
            # We can either remove this line or comment it out.
            # Let's skip adding this line to new_lines and continue.
            continue
        else:
            brace_count = new_brace_count
            new_lines.append(line)
    
    # After processing, if brace_count > 0, we have missing closing braces.
    if brace_count > 0:
        print(f"Warning: Brace count at end is {brace_count}. Missing {brace_count} closing braces.")
        # We could add them, but we'll leave that for manual fix.
    else:
        print("Brace balance is now correct.")
    
    # Write the fixed content back if we removed any lines.
    if problematic_line is not None:
        with open(file_path, 'w') as f:
            f.writelines(new_lines)
        print(f"Fixed: removed line {problematic_line}.")
    else:
        print("No problematic line found. Brace balance might be okay.")
        # But we still check for an extra closing brace at the end.
        if new_lines and new_lines[-1].strip() == '}':
            # This might be the extra brace.
            print("Warning: Last line is a lone '}' – likely extra.")
            # Remove it
            new_lines.pop()
            with open(file_path, 'w') as f:
                f.writelines(new_lines)
            print("Removed trailing lone '}'.")
    
if __name__ == "__main__":
    file_path = Path("rust/core/src/lib.rs")
    if not file_path.exists():
        print(f"File {file_path} not found.")
        sys.exit(1)
    fix_brace_balance(file_path)
