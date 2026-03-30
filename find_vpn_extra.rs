use std::fs;

fn main() {
    let filepath = "rust/core/src/lib.rs";
    let content = fs::read_to_string(filepath).expect("Failed to read file");
    let lines: Vec<&str> = content.lines().collect();
    
    println!("============================================================");
    println!("🦀 FINDING EXTRA }} INSIDE VpnConnection impl");
    println!("============================================================\n");
    
    // VpnConnection impl: lines 1323-2372 (1-indexed)
    let start_idx = 1322; // 0-indexed
    let end_idx = 2371;   // 0-indexed
    
    let mut depth = 0i32;
    let mut depth_history: Vec<(usize, i32, &str)> = Vec::new();
    let mut method_starts: Vec<usize> = Vec::new();
    
    println!("Scanning VpnConnection impl (lines 1323-2372)...\n");
    
    for i in start_idx..=end_idx {
        if i >= lines.len() { break; }
        
        let line = lines[i];
        let line_num = i + 1;
        let before_depth = depth;
        
        depth += line.matches('{').count() as i32;
        depth -= line.matches('}').count() as i32;
        
        depth_history.push((line_num, depth, line));
        
        // Track method starts
        if line.contains("fn ") && line.contains('(') && !line.trim().starts_with("//") {
            method_starts.push(line_num);
        }
        
        // KEY: If depth goes to 0 before line 2372, VpnConnection closes early!
        if depth == 0 && line_num < 2372 && line.trim() == "}" {
            println!("⚠️  FOUND! VpnConnection impl closes at line {}", line_num);
            println!("   But compiler error is at line 2372");
            println!("   This means line {} has an EXTRA }}\n", line_num);
            
            // Show 10 lines of context
            let start = if i > 5 { i - 5 } else { 0 };
            let end = if i + 5 < lines.len() { i + 5 } else { lines.len() - 1 };
            
            println!("   Context:");
            for j in start..=end {
                let marker = if j == i { ">>> " } else { "    " };
                let depth_marker = if depth_history.get(j - start_idx).map(|(_, d, _)| *d).unwrap_or(0) == 0 { " [DEPTH=0]" } else { "" };
                println!("   {}Line {}: {}{}", marker, j + 1, &lines[j][..lines[j].len().min(60)], depth_marker);
            }
            println!();
            
            println!("   RECOMMENDATION: Remove line {}", line_num);
            println!("   Command: sed -i '{}d' {}", line_num, filepath);
            println!();
            return;
        }
    }
    
    println!("============================================================");
    println!("Depth at end of VpnConnection impl: {}", depth);
    println!("============================================================\n");
    
    if depth > 0 {
        println!("⚠️  VpnConnection impl has {} unclosed {{", depth);
        println!("   Need to add }} before line 2372");
    } else if depth == 0 {
        println!("✅ VpnConnection impl properly closed");
        println!("   But compiler says line 2372 is unexpected");
        println!("   This means line 2372 itself is the EXTRA }}\n");
        println!("   RECOMMENDATION: Remove line 2372");
        println!("   Command: sed -i '2372d' {}", filepath);
    }
    
    println!("\n============================================================");
    println!("Method boundaries in VpnConnection:");
    println!("============================================================");
    for &ml in &method_starts {
        println!("  Line {}", ml);
    }
    
    println!("\n============================================================");
}
