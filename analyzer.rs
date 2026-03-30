use std::fs;

fn main() {
    let filepath = "rust/core/src/lib.rs";
    let content = fs::read_to_string(filepath).expect("Cannot read file");
    let lines: Vec<&str> = content.lines().collect();
    
    println!("============================================================");
    println!("RUST BRACE ANALYZER");
    println!("============================================================\n");
    
    // Count braces
    let open = content.matches('{').count();
    let close = content.matches('}').count();
    
    println!("Open braces:  {}", open);
    println!("Close braces: {}", close);
    println!("Balance:      {}", open as i32 - close as i32);
    
    // Track depth
    let mut depth = 0i32;
    let mut max_depth = 0i32;
    let mut neg_lines: Vec<usize> = Vec::new();
    
    for (i, line) in lines.iter().enumerate() {
        depth += line.matches('{').count() as i32;
        depth -= line.matches('}').count() as i32;
        if depth > max_depth { max_depth = depth; }
        if depth < 0 { neg_lines.push(i + 1); }
    }
    
    println!("\nMax depth:   {}", max_depth);
    println!("Final depth: {}", depth);
    
    if !neg_lines.is_empty() {
        println!("\nNegative depth lines:");
        for n in neg_lines.iter().take(5) {
            println!("  Line {}", n);
        }
    }
    
    // Count consecutive } at end
    let mut consec = 0;
    for i in (0..lines.len()).rev() {
        if lines[i].trim() == "}" { consec += 1; } else { break; }
    }
    
    println!("\nConsecutive }} at end: {}", consec);
    
    // Summary
    println!("\n============================================================");
    if open == close && depth == 0 {
        println!("FILE STRUCTURE VALID");
    } else {
        println!("ISSUES FOUND");
        if open > close {
            println!("Missing {} closing brace(s)", open - close);
        }
    }
    println!("============================================================");
}
