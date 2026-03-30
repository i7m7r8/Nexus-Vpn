use std::fs;
use std::env;

fn main() {
    let filepath = env::args().nth(1).unwrap_or_else(|| "rust/core/src/lib.rs".to_string());
    
    println!("============================================================");
    println!("RUST BRACE BALANCE FIXER");
    println!("============================================================\n");
    
    let content = match fs::read_to_string(&filepath) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("Error reading file: {}", e);
            std::process::exit(1);
        }
    };
    
    let lines: Vec<&str> = content.lines().collect();
    let total_open = content.matches('{').count();
    let total_close = content.matches('}').count();
    
    println!("[1] Total Brace Count");
    println!("------------------------------------------------------------");
    println!("  Open braces:  {}", total_open);
    println!("  Close braces: {}", total_close);
    println!("  Balance:      {}", total_open as i32 - total_close as i32);
    
    let mut depth = 0i32;
    let mut max_depth = 0i32;
    let mut negative_lines: Vec<usize> = Vec::new();
    let mut depth_at_line: Vec<i32> = Vec::new();
    
    for (i, line) in lines.iter().enumerate() {
        depth += line.matches('{').count() as i32;
        depth -= line.matches('}').count() as i32;
        depth_at_line.push(depth);
        
        if depth > max_depth { max_depth = depth; }
        if depth < 0 { negative_lines.push(i + 1); }
    }
    
    println!("\n[2] Depth Analysis");
    println!("------------------------------------------------------------");
    println!("  Max depth:   {}", max_depth);
    println!("  Final depth: {}", depth);
    println!("  Negative lines: {}", negative_lines.len());
    
    if !negative_lines.is_empty() {        println!("\n  Lines with negative depth:");
        for &nl in negative_lines.iter().take(10) {
            let idx = nl - 1;
            if idx < lines.len() {
                let preview = &lines[idx][..lines[idx].len().min(60)];
                println!("    Line {}: {}", nl, preview);
            }
        }
    }
    
    println!("\n[3] End of File Analysis");
    println!("------------------------------------------------------------");
    
    let mut consecutive_close: Vec<usize> = Vec::new();
    for i in (0..lines.len()).rev() {
        if lines[i].trim() == "}" {
            consecutive_close.push(i + 1);
        } else {
            break;
        }
    }
    
    println!("  Consecutive }} at end: {}", consecutive_close.len());
    for &line_num in consecutive_close.iter().rev() {
        println!("    Line {}", line_num);
    }
    
    println!("\n[4] VpnConnection impl Analysis");
    println!("------------------------------------------------------------");
    
    let mut vpn_start: Option<usize> = None;
    let mut vpn_depth = 0i32;
    
    for (i, line) in lines.iter().enumerate() {
        if line.contains("impl VpnConnection") && line.contains('{') {
            vpn_start = Some(i + 1);
            vpn_depth = 1;
            println!("  VpnConnection impl starts at line {}", i + 1);
        } else if vpn_start.is_some() && vpn_depth > 0 {
            vpn_depth += line.matches('{').count() as i32;
            vpn_depth -= line.matches('}').count() as i32;
            
            if vpn_depth == 0 {
                println!("  VpnConnection impl ends at line {}", i + 1);
                break;
            }
        }
    }
    
    println!("\n============================================================");    println!("SUMMARY");
    println!("============================================================");
    
    let balance = total_open as i32 - total_close as i32;
    let mut valid = true;
    
    if balance != 0 {
        println!("\n  [!] Brace count mismatch: {}", balance);
        valid = false;
    }
    if depth != 0 {
        println!("  [!] Final depth not zero: {}", depth);
        valid = false;
    }
    if !negative_lines.is_empty() {
        println!("  [!] Negative depth at {} lines", negative_lines.len());
        valid = false;
    }
    
    if valid {
        println!("\n  FILE STRUCTURE VALID");
    } else {
        println!("\n  ISSUES FOUND - Apply fixes below:");
    }
    
    println!("\n============================================================");
    println!("FIX COMMANDS");
    println!("============================================================");
    
    if balance > 0 {
        println!("\n  Missing {} closing brace(s)", balance);
        println!("  Command:");
        for _ in 0..balance {
            println!("    echo '}}' >> {}", filepath);
        }
    } else if balance < 0 {
        println!("\n  Extra {} closing brace(s)", -balance);
        if !negative_lines.is_empty() {
            println!("  Remove }} at lines (reverse order):");
            for &nl in negative_lines.iter().rev().take(5) {
                println!("    sed -i '{}d' {}", nl, filepath);
            }
        }
    }
    
    if consecutive_close.len() > 3 {
        println!("\n  Too many consecutive }} at end");
        println!("  Consider removing line {}", consecutive_close[0]);
        println!("    sed -i '{}d' {}", consecutive_close[0], filepath);
    }    
    println!("\n  Verify:");
    println!("    python3 -c \"c=open('{}').read(); print('Balance:', c.count('{{')-c.count('}}'))\"", filepath);
    
    println!("\n============================================================");
    
    std::process::exit(if valid { 0 } else { 1 });
}
