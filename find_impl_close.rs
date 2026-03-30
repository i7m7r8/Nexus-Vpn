use std::fs;

fn main() {
    let filepath = "rust/core/src/lib.rs";
    let content = fs::read_to_string(filepath).expect("Cannot read file");
    let lines: Vec<&str> = content.lines().collect();
    
    println!("============================================================");
    println!("FINDING WHERE VpnConnection impl ACTUALLY CLOSES");
    println!("============================================================\n");
    
    // Start from line 1323 (impl VpnConnection)
    let start_idx = 1322; // 0-indexed
    
    let mut impl_depth = 0i32;
    let mut in_impl = false;
    let mut impl_close_line: Option<usize> = None;
    
    println!("Scanning from line 1323...\n");
    
    for i in start_idx..lines.len() {
        let line = lines[i];
        let line_num = i + 1;
        let trimmed = line.trim();
        
        // Check if this is impl VpnConnection start
        if line.contains("impl VpnConnection") && line.contains('{') {
            in_impl = true;
            impl_depth = 1;
            println!("Line {}: impl VpnConnection {{ (depth=1)", line_num);
            continue;
        }
        
        if !in_impl { continue; }
        
        // Count braces (but ignore comments and strings roughly)
        let mut in_comment = false;
        let mut in_string = false;
        
        for ch in line.chars() {
            if ch == '/' && !in_string {
                in_comment = true;
            }
            if ch == '\n' {
                in_comment = false;
            }
            if ch == '"' && !in_comment {
                in_string = !in_string;
            }        }
        
        // Simple brace count for this line
        let opens = line.matches('{').count();
        let closes = line.matches('}').count();
        
        let before_depth = impl_depth;
        impl_depth += opens as i32;
        impl_depth -= closes as i32;
        
        // Check if impl closed
        if before_depth == 1 && impl_depth == 0 && trimmed == "}" {
            impl_close_line = Some(line_num);
            println!("\n⚠️  VpnConnection impl CLOSES at line {}", line_num);
            println!("   Everything after line {} is OUTSIDE the impl!", line_num);
            println!("   But compiler error is at line 2372");
            println!("   This means lines {}-2372 have extra }}\n", line_num + 1);
            
            // Show context
            let start = if i > 3 { i - 3 } else { 0 };
            let end = if i + 3 < lines.len() { i + 3 } else { lines.len() - 1 };
            
            println!("   Context:");
            for j in start..=end {
                let marker = if j == i { ">>> " } else { "    " };
                println!("   {}Line {}: {}", marker, j + 1, &lines[j][..lines[j].len().min(60)]);
            }
            println!();
            
            // Count standalone } between impl close and 2372
            let mut extra_count = 0;
            for k in line_num..2372 {
                if (k - 1) < lines.len() && lines[k - 1].trim() == "}" {
                    extra_count += 1;
                }
            }
            
            println!("   There are {} standalone }} between line {} and 2372", extra_count, line_num + 1);
            println!("   These are causing the error!\n");
            
            break;
        }
        
        // Print depth changes at key points
        if impl_depth == 0 && impl_close_line.is_none() {
            impl_close_line = Some(line_num);
            println!("Line {}: impl depth reached 0", line_num);
        }
    }
        println!("\n============================================================");
    match impl_close_line {
        Some(line) => {
            println!("VpnConnection impl closes at line {}", line);
            if line < 2372 {
                println!("ERROR: impl closes {} lines before compiler error!", 2372 - line);
                println!("RECOMMENDATION: Remove one }} between line {} and 2372", line);
            }
        }
        None => {
            println!("VpnConnection impl does not close before EOF");
        }
    }
    println!("============================================================");
}
