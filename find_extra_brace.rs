use std::fs;

fn main() {
    let filepath = "rust/core/src/lib.rs";
    let content = fs::read_to_string(filepath).expect("Failed to read file");
    let lines: Vec<&str> = content.lines().collect();
    
    println!("============================================================");
    println!("FINDING EXTRA }} INSIDE VpnConnection impl");
    println!("============================================================\n");
    
    // VpnConnection impl: lines 1323-2372 (1-indexed)
    let start_idx = 1322; // 0-indexed
    let end_idx = 2371;   // 0-indexed
    
    let mut depth = 0i32;
    let mut method_depth: Vec<(usize, i32, &str)> = Vec::new();
    let mut standalone_close: Vec<usize> = Vec::new();
    
    println!("Scanning VpnConnection impl (lines 1323-2372)...\n");
    
    for i in start_idx..=end_idx {
        if i >= lines.len() { break; }
        
        let line = lines[i];
        let line_num = i + 1;
        
        // Track depth
        let before_depth = depth;
        depth += line.matches('{').count() as i32;
        depth -= line.matches('}').count() as i32;
        
        // Record standalone } lines
        if line.trim() == "}" {
            standalone_close.push(line_num);
        }
        
        // If depth goes to 0 before line 2372, we found early close
        if depth == 0 && line_num < 2372 && line.trim() == "}" {
            println!("⚠️  FOUND! VpnConnection impl closes at line {}", line_num);
            println!("   But compiler expects it to close at line 2372");
            println!("   This means line {} has an EXTRA }}\n", line_num);
            
            // Show context
            let start = if i > 3 { i - 3 } else { 0 };
            let end = if i + 3 < lines.len() { i + 3 } else { lines.len() - 1 };
            
            println!("   Context around line {}:", line_num);
            for j in start..=end {
                let marker = if j == i { ">>> " } else { "    " };
                println!("   {}Line {}: {}", marker, j + 1, &lines[j][..lines[j].len().min(70)]);
            }
            println!();
            break;
        }
        
        // Track method boundaries
        if line.contains("fn ") && line.contains('(') {
            method_depth.push((line_num, before_depth, line));
        }
    }
    
    println!("============================================================");
    println!("Standalone }} lines in VpnConnection impl:");
    println!("============================================================");
    
    for &line_num in &standalone_close {
        println!("  Line {}", line_num);
    }
    
    println!("\n============================================================");
    println!("RECOMMENDATION:");
    println!("============================================================");
    
    if depth > 0 {
        println!("  VpnConnection impl has {} unclosed {{", depth);
        println!("  Need to add }} before line 2372");
    } else if standalone_close.len() > 30 {
        println!("  Many standalone }} found - one is extra");
        println!("  Check the line marked with ⚠️ above");
    }
    
    println!("\n============================================================");
}
