use std::fs;

fn main() {
    let filepath = "rust/core/src/lib.rs";
    let content = fs::read_to_string(filepath).expect("Failed to read file");
    let lines: Vec<&str> = content.lines().collect();
    
    println!("============================================================");
    println!("🦀 FINDING WHERE VpnConnection impl CLOSES EARLY");
    println!("============================================================\n");
    
    // VpnConnection impl starts at line 1323
    let start_idx = 1322; // 0-indexed
    
    let mut depth = 0i32;
    let mut found_close = false;
    
    println!("Scanning from line 1323 (VpnConnection impl start)...\n");
    
    for i in start_idx..lines.len() {
        let line = lines[i];
        let line_num = i + 1;
        
        let before_depth = depth;
        depth += line.matches('{').count() as i32;
        depth -= line.matches('}').count() as i32;
        
        // Check if we hit depth 0 (impl block closed)
        if before_depth == 1 && depth == 0 && line.trim() == "}" {
            println!("⚠️  VpnConnection impl CLOSES at line {}", line_num);
            println!("   But compiler error is at line 2372");
            println!("   This means lines {}-2372 are OUTSIDE the impl block!\n", line_num + 1);
            
            // Show context
            let start = if i > 3 { i - 3 } else { 0 };
            let end = if i + 3 < lines.len() { i + 3 } else { lines.len() - 1 };
            
            println!("   Context around line {}:", line_num);
            for j in start..=end {
                let marker = if j == i { ">>> " } else { "    " };
                println!("   {}Line {}: {}", marker, j + 1, &lines[j][..lines[j].len().min(60)]);
            }
            println!();
            
            found_close = true;
            
            // Check what's between this close and line 2372
            if line_num < 2372 {
                println!("   📊 Lines {} to 2372 are OUTSIDE VpnConnection impl:", line_num + 1);
                
                // Count standalone } in that range
                let mut extra_close_count = 0;
                for k in (line_num)..2372 {
                    if k < lines.len() && lines[k].trim() == "}" {
                        extra_close_count += 1;
                    }
                }
                
                println!("   There are {} standalone }} between line {} and 2372", extra_close_count, line_num + 1);
                println!("   These are causing the 'unexpected closing delimiter' error!\n");
                
                println!("   RECOMMENDATION:");
                println!("   The }} at line {} closes VpnConnection too early", line_num);
                println!("   Check if there's a missing {{ before line {}", line_num);
                println!("   Or remove one of the extra }} between {} and 2372\n", line_num + 1);
            }
            
            break;
        }
    }
    
    if !found_close {
        println!("✅ VpnConnection impl does not close before line 2372");
        println!("   The issue might be elsewhere in the file\n");
    }
    
    println!("============================================================");
    println!("Final depth at end of file: {}", depth);
    println!("============================================================");
}
