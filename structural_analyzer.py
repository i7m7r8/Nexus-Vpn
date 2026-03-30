#!/usr/bin/env python3
"""
INNOVATIVE Structural Analyzer for Rust files
Finds ALL brace, bracket, and structural issues
"""
from pathlib import Path
import re

def analyze_file(filepath):
    content = Path(filepath).read_text()
    lines = content.split('\n')
    
    print("="*70)
    print("INNOVATIVE STRUCTURAL ANALYZER")
    print("="*70)
    
    issues = []
    
    # ========================================================================
    # 1. BRACE DEPTH TRACKING
    # ========================================================================
    print("\n[1/7] Brace Depth Analysis")
    print("-"*70)
    
    depth = 0
    depth_at_line = []
    max_depth = 0
    min_depth = 0
    
    for line in lines:
        depth += line.count('{') - line.count('}')
        depth_at_line.append(depth)
        max_depth = max(max_depth, depth)
        min_depth = min(min_depth, depth)
        if depth < 0:
            issues.append(('ERROR', len(depth_at_line), 'Negative depth (' + str(depth) + ')', line[:50]))
    
    total_open = content.count('{')
    total_close = content.count('}')
    
    print("  Total lines: " + str(len(lines)))
    print("  Total { : " + str(total_open))
    print("  Total } : " + str(total_close))
    print("  Balance: " + str(total_open - total_close))
    print("  Max depth: " + str(max_depth))
    print("  Min depth: " + str(min_depth))
    
    if total_open != total_close:
        issues.append(('ERROR', 0, 'Missing ' + str(total_open - total_close) + ' closing brace(s)', ''))    
    # ========================================================================
    # 2. CODE BLOCK DETECTION
    # ========================================================================
    print("\n[2/7] Code Block Analysis")
    print("-"*70)
    
    blocks = []
    stack = []
    
    for i, line in enumerate(lines):
        match = re.search(r'(pub\s+)?(impl|struct|enum|fn|mod|trait|async\s+fn)\s+(\w+)', line)
        if match and '{' in line:
            block_type = match.group(2)
            block_name = match.group(3)
            start_depth = depth_at_line[i] if i < len(depth_at_line) else 0
            stack.append((block_type, block_name, i+1, start_depth))
        
        if line.strip() == '}' and stack:
            block_type, block_name, start_line, start_depth = stack.pop()
            end_depth = depth_at_line[i] if i < len(depth_at_line) else 0
            blocks.append({
                'type': block_type,
                'name': block_name,
                'start': start_line,
                'end': i+1,
                'start_depth': start_depth,
                'end_depth': end_depth
            })
    
    block_issues = []
    for block in blocks:
        expected = block['start_depth'] - 1
        if block['end_depth'] != expected:
            block_issues.append(block)
            print("  ❌ " + block['type'] + " " + block['name'] + ": lines " + str(block['start']) + "-" + str(block['end']))
            print("      Expected end depth: " + str(expected) + ", Actual: " + str(block['end_depth']))
    
    if not block_issues:
        print("  ✅ All blocks properly closed")
    
    # ========================================================================
    # 3. MERGED LINE DETECTION
    # ========================================================================
    print("\n[3/7] Merged Line Detection")
    print("-"*70)
    
    merged_count = 0
    merged_lines = []
    for i, line in enumerate(lines):        if '///' in line and 'pub ' in line:
            parts = line.split('pub')
            if len(parts) > 1 and '///' in parts[0]:
                print("  ⚠️  Line " + str(i+1) + ": Comment merged with function")
                print("      " + line[:70])
                merged_count += 1
                merged_lines.append(i+1)
                issues.append(('WARNING', i+1, 'Merged comment and function', line[:50]))
    
    if merged_count == 0:
        print("  ✅ No merged lines found")
    
    # ========================================================================
    # 4. NEGATIVE DEPTH LOCATIONS
    # ========================================================================
    print("\n[4/7] Negative Depth Locations")
    print("-"*70)
    
    negative_found = False
    negative_lines = []
    for i, d in enumerate(depth_at_line):
        if d < 0:
            print("  ❌ Line " + str(i+1) + ": depth=" + str(d))
            print("      " + lines[i][:60])
            if i > 0:
                print("      Previous: " + lines[i-1][:60])
            negative_found = True
            negative_lines.append(i+1)
    
    if not negative_found:
        print("  ✅ No negative depth found")
    
    # ========================================================================
    # 5. END OF FILE ANALYSIS
    # ========================================================================
    print("\n[5/7] End of File Analysis")
    print("-"*70)
    
    final_depth = depth_at_line[-1] if depth_at_line else 0
    print("  Final depth: " + str(final_depth))
    
    if final_depth != 0:
        print("  ❌ File ends at depth " + str(final_depth) + " (should be 0)")
        issues.append(('ERROR', len(lines), 'File ends at depth ' + str(final_depth), ''))
        
        print("\n  Last 20 lines with depth:")
        for i in range(max(0, len(lines)-20), len(lines)):
            marker = ">>> " if depth_at_line[i] != 0 else "    "
            print("  " + marker + "Line " + str(i+1) + ": depth=" + str(depth_at_line[i]) + " | " + lines[i][:50])
    else:        print("  ✅ File ends at correct depth")
    
    # ========================================================================
    # 6. SPECIFIC SECTION ANALYSIS
    # ========================================================================
    print("\n[6/7] Section Analysis")
    print("-"*70)
    
    sections = [
        ('EncryptionEngine', 'impl EncryptionEngine', '// === SNI HANDLER'),
        ('SniHandler', 'impl SniHandler', '// === TOR MANAGER'),
        ('TorManager', 'impl TorManager', '// === VPN CONNECTION'),
        ('VpnConnection', 'impl VpnConnection', '// === VPN ENGINE'),
        ('VpnEngine', 'impl VpnEngine', '// === CONNECTION POOL'),
        ('JNI Exports', '// === JNI EXPORTS', '// === INITIALIZATION'),
        ('Tests', '#[cfg(test)]', 'END OF NEXUS'),
    ]
    
    section_issues = []
    for name, start_marker, end_marker in sections:
        start_idx = content.find(start_marker)
        end_idx = content.find(end_marker, start_idx)
        
        if start_idx != -1:
            if end_idx == -1:
                end_idx = len(content)
            
            section = content[start_idx:end_idx]
            o = section.count('{')
            c = section.count('}')
            balance = o - c
            
            status = "❌" if balance != 0 else "✅"
            print("  " + status + " " + name + ": {=" + str(o) + ", }=" + str(c) + ", balance=" + str(balance))
            
            if balance != 0:
                section_issues.append((name, balance))
                issues.append(('ERROR', 0, name + ' section imbalance', 'balance=' + str(balance)))
    
    # ========================================================================
    # 7. FIX RECOMMENDATIONS
    # ========================================================================
    print("\n[7/7] Fix Recommendations")
    print("-"*70)
    
    if not issues:
        print("  ✅ No issues found! File structure looks good.")
    else:
        print("  Found " + str(len(issues)) + " issue(s):")
        print()        
        errors = [i for i in issues if i[0] == 'ERROR']
        warnings = [i for i in issues if i[0] == 'WARNING']
        
        if errors:
            print("  ERRORS (must fix):")
            for err_type, line_num, desc, snippet in errors:
                if line_num > 0:
                    print("    • Line " + str(line_num) + ": " + desc)
                else:
                    print("    • " + desc)
        
        if warnings:
            print("\n  WARNINGS (should fix):")
            for warn_type, line_num, desc, snippet in warnings:
                print("    • Line " + str(line_num) + ": " + desc)
        
        print("\n  SUGGESTED FIXES:")
        
        if total_open > total_close:
            print("    1. Add " + str(total_open - total_close) + " closing } at appropriate location(s)")
        elif total_close > total_open:
            print("    1. Remove " + str(total_close - total_open) + " extra } from file")
        
        if merged_count > 0:
            print("    2. Separate " + str(merged_count) + " merged comment/function line(s)")
            for ml in merged_lines:
                print("       Line " + str(ml) + ": sed -i '" + str(ml) + " s/\\/\\/\\/ Comment    pub/\\/\\/\\/ Comment\\n    pub/' rust/core/src/lib.rs")
        
        if final_depth > 0:
            print("    3. Add " + str(final_depth) + " closing } at end of file")
        
        if negative_found:
            print("    4. Remove extra } where depth goes negative:")
            for nl in negative_lines:
                print("       Line " + str(nl) + ": sed -i '" + str(nl) + "d' rust/core/src/lib.rs")
        
        if section_issues:
            print("    5. Fix section imbalances:")
            for sec_name, sec_balance in section_issues:
                print("       " + sec_name + ": balance=" + str(sec_balance))
    
    # ========================================================================
    # SUMMARY
    # ========================================================================
    print("\n" + "="*70)
    print("SUMMARY")
    print("="*70)
    print("  Total lines: " + str(len(lines)))
    print("  Brace balance: " + str(total_open - total_close))    print("  Block issues: " + str(len(block_issues)))
    print("  Merged lines: " + str(merged_count))
    print("  Negative depth: " + ('Yes' if negative_found else 'No'))
    print("  Final depth: " + str(final_depth))
    print("  Total issues: " + str(len(issues)))
    
    if len(issues) == 0 and total_open == total_close and final_depth == 0:
        print("\n  ✅ FILE STRUCTURE IS VALID!")
    else:
        print("\n  ⚠️  FILE HAS " + str(len(issues)) + " ISSUE(S) - NEEDS FIXING")
    
    print("="*70)
    
    return issues, {
        'total_open': total_open,
        'total_close': total_close,
        'final_depth': final_depth,
        'merged_lines': merged_lines,
        'negative_lines': negative_lines,
        'section_issues': section_issues
    }

if __name__ == '__main__':
    issues, data = analyze_file('rust/core/src/lib.rs')
    
    print("\n" + "="*70)
    print("AUTO-FIX COMMANDS")
    print("="*70)
    
    if data['total_open'] > data['total_close']:
        print("# Add missing closing braces at end of file:")
        for i in range(data['total_open'] - data['total_close']):
            print('echo "}" >> rust/core/src/lib.rs')
    
    if data['merged_lines']:
        print("\n# Fix merged lines:")
        for ml in data['merged_lines']:
            print("sed -i '" + str(ml) + " s/\\/\\/\\/.*pub/\\/\\/\\/ Comment\\n    pub/' rust/core/src/lib.rs")
    
    if data['negative_lines']:
        print("\n# Remove extra closing braces:")
        for nl in sorted(data['negative_lines'], reverse=True):
            print("sed -i '" + str(nl) + "d' rust/core/src/lib.rs")
    
    if data['final_depth'] > 0:
        print("\n# Add closing braces at end:")
        for i in range(data['final_depth']):
            print('echo "}" >> rust/core/src/lib.rs')
    
    print("\n# Verify and push:")    print("python3 -c \"c = open('rust/core/src/lib.rs').read(); print('Balance:', c.count('{') - c.count('}'))\"")
    print("git add rust/core/src/lib.rs && git commit -m 'fix: Structural issues' && git push --force-with-lease origin main")
