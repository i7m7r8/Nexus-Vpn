use std::fs;

fn main() {
    let filepath = "rust/core/src/lib.rs";
    let content = fs::read_to_string(filepath).expect("Cannot read file");
    let lines: Vec<&str> = content.lines().collect();
    
    println!("============================================================");
    println!("DEEP STRUCTURAL ANALYSIS (lines 1323-1450)");
    println!("============================================================\n");
    
    let start_idx = 1322; // line 1323
    let end_idx = 1450;   // line 1451
    
    let mut depth = 0i32;
    let mut depth_changes: Vec<(usize, i32, &str)> = Vec::new();
    
    println!("Tracking depth from impl VpnConnection {{\n");
    
    for i in start_idx..end_idx.min(lines.len()) {
        let line = lines[i];
        let line_num = i + 1;
        let trimmed = line.trim();
        
        let before = depth;
        depth += line.matches('{').count() as i32;
        depth -= line.matches('}').count() as i32;
        
        // Record significant depth changes
        if line.contains('{') || line.contains('}') {
            depth_changes.push((line_num, depth, trimmed));
        }
        
        // Show depth at key points
        if depth == 0 && before == 1 {
            println!("\n⚠️  DEPTH REACHED 0 AT LINE {}!", line_num);
            println!("   This would close VpnConnection impl early!\n");
            let start = if i > 2 { i - 2 } else { 0 };
            let end = (i + 2).min(lines.len() - 1);
            for j in start..=end {
                let marker = if j == i { ">>> " } else { "    " };
                println!("   {}Line {}: {}", marker, j + 1, &lines[j][..lines[j].len().min(60)]);
            }
            println!();
        }
        
        // Check for suspicious patterns
        if trimmed.starts_with("}") && !trimmed.starts_with("//") {
            // Check indentation
            let indent = line.len() - line.trim_start().len();
            if indent < 4 && depth > 0 {
                println!("⚠️  Line {}: }} with low indentation ({}) at depth {}", line_num, indent, depth);
            }
        }
    }
    
    println!("\n============================================================");
    println!("Depth at line 1450: {}", depth);
    println!("============================================================");
    
    if depth > 1 {
        println!("\n✅ VpnConnection impl still open at line 1450 (depth={})", depth);
        println!("   This is EXPECTED - impl should continue to ~line 2372");
    } else if depth == 1 {
        println!("\n⚠️  VpnConnection impl at depth 1 at line 1450");
        println!("   Should be deeper - check for missing {{");
    } else {
        println!("\n❌ VpnConnection impl CLOSED before line 1450!");
        println!("   Found missing {{ or extra }} between 1323-1450");
    }
    
    println!("\n============================================================");
    println!("All brace lines (1323-1450):");
    println!("============================================================");
    for (line_num, d, code) in &depth_changes {
        println!("  L{:4}: d={} {}", line_num, d, code);
    }
    
    println!("\n============================================================");
}
