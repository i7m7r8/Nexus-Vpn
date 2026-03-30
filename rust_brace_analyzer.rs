use std::fs;
use std::env;
use std::process;

fn main() {
    println!("============================================================");
    println!("🦀 RUST COMPREHENSIVE BRACE ANALYZER & FIXER");
    println!("============================================================\n");
    
    let filepath = env::args().nth(1).unwrap_or_else(|| "rust/core/src/lib.rs".to_string());
    
    // Read file
    let content = match fs::read_to_string(&filepath) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("❌ Error reading file: {}", e);
            process::exit(1);
        }
    };
    
    let lines: Vec<&str> = content.lines().collect();
    let total_lines = lines.len();
    
    // ============================================================
    // 1. TOTAL BRACE COUNT
    // ============================================================
    println!("[1/7] Total Brace Count");
    println!("------------------------------------------------------------");
    
    let total_open = content.matches('{').count();
    let total_close = content.matches('}').count();
    let balance = total_open as i32 - total_close as i32;
    
    println!("  File:           {}", filepath);
    println!("  Total lines:    {}", total_lines);
    println!("  Open braces:    {}", total_open);
    println!("  Close braces:   {}", total_close);
    println!("  Balance:        {}", balance);
    
    if balance == 0 {
        println!("  ✅ Brace count OK");
    } else {
        println!("  ❌ Brace count MISMATCH");
    }
    
    // ============================================================
    // 2. DEPTH ANALYSIS
    // ============================================================
    println!("\n[2/7] Depth Analysis");    println!("------------------------------------------------------------");
    
    let mut depth = 0i32;
    let mut max_depth = 0i32;
    let mut min_depth = 0i32;
    let mut depth_at_line: Vec<i32> = Vec::new();
    let mut negative_lines: Vec<usize> = Vec::new();
    
    for (i, line) in lines.iter().enumerate() {
        depth += line.matches('{').count() as i32;
        depth -= line.matches('}').count() as i32;
        depth_at_line.push(depth);
        
        if depth > max_depth { max_depth = depth; }
        if depth < min_depth { min_depth = depth; }
        if depth < 0 { negative_lines.push(i + 1); }
    }
    
    let final_depth = depth;
    
    println!("  Max depth:      {}", max_depth);
    println!("  Min depth:      {}", min_depth);
    println!("  Final depth:    {}", final_depth);
    println!("  Negative lines: {}", negative_lines.len());
    
    if final_depth == 0 && negative_lines.is_empty() {
        println!("  ✅ Depth analysis OK");
    } else {
        println!("  ❌ Depth analysis ISSUES");
        if !negative_lines.is_empty() {
            println!("\n  Lines with negative depth:");
            for &nl in negative_lines.iter().take(10) {
                let idx = nl - 1;
                if idx < lines.len() {
                    println!("    Line {}: {}", nl, &lines[idx][..lines[idx].len().min(50)]);
                }
            }
        }
    }
    
    // ============================================================
    // 3. END OF FILE ANALYSIS
    // ============================================================
    println!("\n[3/7] End of File Analysis");
    println!("------------------------------------------------------------");
    
    let mut consecutive_close: Vec<usize> = Vec::new();
    for i in (0..lines.len()).rev() {
        if lines[i].trim() == "}" {
            consecutive_close.push(i + 1);        } else {
            break;
        }
    }
    
    println!("  Consecutive }} at end: {}", consecutive_close.len());
    for &line_num in consecutive_close.iter().rev() {
        println!("    Line {}", line_num);
    }
    
    // Show last 15 lines with depth
    println!("\n  Last 15 lines:");
    for i in (total_lines.saturating_sub(15))..total_lines {
        let marker = if depth_at_line[i] != 0 { "⚠️  " } else { "✅  " };
        println!("    {}L{:4}: d={} {}", marker, i + 1, depth_at_line[i], &lines[i][..lines[i].len().min(50)]);
    }
    
    // ============================================================
    // 4. CODE BLOCK ANALYSIS
    // ============================================================
    println!("\n[4/7] Code Block Analysis");
    println!("------------------------------------------------------------");
    
    let mut blocks: Vec<(&str, &str, usize, usize)> = Vec::new();
    let mut stack: Vec<(&str, &str, usize)> = Vec::new();
    
    for (i, line) in lines.iter().enumerate() {
        let line_num = i + 1;
        
        // Detect block start (impl, struct, enum, fn, mod)
        if line.contains("impl ") && line.contains('{') {
            let name = extract_name(line, "impl");
            stack.push(("impl", name, line_num));
        } else if line.contains("struct ") && line.contains('{') {
            let name = extract_name(line, "struct");
            stack.push(("struct", name, line_num));
        } else if line.contains("mod ") && line.contains('{') {
            let name = extract_name(line, "mod");
            stack.push(("mod", name, line_num));
        } else if (line.contains("fn ") || line.contains("async fn")) && line.contains('{') {
            let name = extract_name(line, "fn");
            stack.push(("fn", name, line_num));
        }
        
        // Detect block end
        if line.trim() == "}" && !stack.is_empty() {
            let (btype, bname, start_line) = stack.pop().unwrap();
            blocks.push((btype, bname, start_line, line_num));
        }
    }    
    println!("  Total blocks found: {}", blocks.len());
    
    // Show last 10 blocks
    println!("\n  Last 10 blocks:");
    for block in blocks.iter().rev().take(10) {
        println!("    {} {} : lines {}-{}", block.0, block.1, block.2, block.3);
    }
    
    // ============================================================
    // 5. VPNCONNECTION IMPL ANALYSIS
    // ============================================================
    println!("\n[5/7] VpnConnection impl Analysis");
    println!("------------------------------------------------------------");
    
    let mut vpn_start: Option<usize> = None;
    let mut vpn_end: Option<usize> = None;
    let mut vpn_depth = 0i32;
    
    for (i, line) in lines.iter().enumerate() {
        let line_num = i + 1;
        
        if line.contains("impl VpnConnection") && line.contains('{') {
            vpn_start = Some(line_num);
            vpn_depth = 1;
        } else if vpn_start.is_some() && vpn_depth > 0 {
            vpn_depth += line.matches('{').count() as i32;
            vpn_depth -= line.matches('}').count() as i32;
            
            if vpn_depth == 0 && vpn_end.is_none() {
                vpn_end = Some(line_num);
                break;
            }
        }
    }
    
    match (vpn_start, vpn_end) {
        (Some(start), Some(end)) => {
            println!("  VpnConnection impl: lines {}-{}", start, end);
            println!("  ✅ VpnConnection impl properly closed");
        }
        (Some(start), None) => {
            println!("  VpnConnection impl: starts at line {}", start);
            println!("  ❌ VpnConnection impl NOT closed!");
        }
        (None, _) => {
            println!("  ❌ VpnConnection impl NOT found!");
        }
    }
        // ============================================================
    // 6. SECTION ANALYSIS
    // ============================================================
    println!("\n[6/7] Section Analysis");
    println!("------------------------------------------------------------");
    
    let sections = [
        ("EncryptionEngine", "impl EncryptionEngine", "// === SNI HANDLER"),
        ("SniHandler", "impl SniHandler", "// === TOR MANAGER"),
        ("TorManager", "impl TorManager", "// === VPN CONNECTION"),
        ("VpnConnection", "impl VpnConnection", "// === VPN ENGINE"),
        ("VpnEngine", "impl VpnEngine", "// === CONNECTION POOL"),
        ("JNI Exports", "// === JNI EXPORTS", "// === INITIALIZATION"),
        ("Tests", "#[cfg(test)]", "END OF NEXUS"),
    ];
    
    for (name, start_marker, end_marker) in sections.iter() {
        let start_idx = content.find(start_marker);
        let end_idx = content.find(end_marker);
        
        if let Some(start) = start_idx {
            let end = end_idx.unwrap_or(content.len());
            let section = &content[start..end];
            let o = section.matches('{').count();
            let c = section.matches('}').count();
            let bal = o as i32 - c as i32;
            
            let status = if bal == 0 { "✅" } else { "❌" };
            println!("  {} {:18}: {{={:3}, }}={:3}, balance={:3}", status, name, o, c, bal);
        }
    }
    
    // ============================================================
    // 7. SUMMARY & FIX RECOMMENDATIONS
    // ============================================================
    println!("\n[7/7] Summary & Fix Recommendations");
    println!("------------------------------------------------------------");
    
    let mut issues = 0;
    
    if balance != 0 {
        issues += 1;
        println!("  ❌ Brace count mismatch: {}", balance);
    }
    if final_depth != 0 {
        issues += 0;
        println!("  ❌ Final depth not zero: {}", final_depth);
    }
    if !negative_lines.is_empty() {
        issues += 1;        println!("  ❌ Negative depth at {} lines", negative_lines.len());
    }
    if consecutive_close.len() > 3 {
        issues += 1;
        println!("  ❌ Too many consecutive }} at end: {}", consecutive_close.len());
    }
    
    println!("\n  Total issues: {}", issues);
    
    if issues == 0 {
        println!("\n  ✅ FILE STRUCTURE VALID");
    } else {
        println!("\n  ⚠️  FILE HAS {} ISSUE(S)", issues);
    }
    
    // Fix commands
    println!("\n============================================================");
    println!("FIX COMMANDS");
    println!("============================================================");
    
    if balance > 0 {
        println!("\n  Missing {} closing brace(s):", balance);
        for _ in 0..balance {
            println!("    echo '}}' >> {}", filepath);
        }
    } else if balance < 0 {
        println!("\n  Extra {} closing brace(s):", -balance);
        if !negative_lines.is_empty() {
            println!("  Remove }} at lines (reverse order):");
            for &nl in negative_lines.iter().rev().take(5) {
                println!("    sed -i '{}d' {}", nl, filepath);
            }
        }
    }
    
    if consecutive_close.len() > 3 {
        println!("\n  Too many }} at end:");
        println!("  Consider removing line {}", consecutive_close[0]);
        println!("    sed -i '{}d' {}", consecutive_close[0], filepath);
    }
    
    println!("\n  Verify:");
    println!("    ./fix_braces {}", filepath);
    println!("    python3 -c \"c=open('{}').read(); print('Balance:', c.count('{{')-c.count('}}'))\"", filepath);
    
    println!("\n============================================================");
    
    process::exit(if issues == 0 { 0 } else { 1 });
}
fn extract_name(line: &str, keyword: &str) -> &str {
    if let Some(pos) = line.find(keyword) {
        let rest = &line[pos + keyword.len()..];
        let rest = rest.trim_start();
        if let Some(end) = rest.find(|c: char| !c.is_alphanumeric() && c != '_') {
            return &rest[..end];
        }
        return rest.trim_end();
    }
    "unknown"
}
