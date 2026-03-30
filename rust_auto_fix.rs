use std::process::Command;
use std::fs;
use std::env;

fn main() {
    println!("============================================================");
    println!("🦀 RUST POWERFUL CODE ANALYZER & AUTO-FIXER");
    println!("============================================================\n");
    
    let workspace = env::args().nth(1).unwrap_or_else(|| ".".to_string());
    
    // Change to workspace directory
    env::set_current_dir(&workspace).expect("Failed to change directory");
    
    println!("Working directory: {}\n", env::current_dir().unwrap().display());
    
    let mut issues_found = 0;
    let mut issues_fixed = 0;
    
    // ============================================================
    // 1. CARGO CHECK - Basic compilation errors
    // ============================================================
    println!("[1/6] Running cargo check...");
    println!("------------------------------------------------------------");
    
    let check = Command::new("cargo")
        .args(&["check", "--all-targets"])
        .output();
    
    match check {
        Ok(output) => {
            if !output.status.success() {
                let stderr = String::from_utf8_lossy(&output.stderr);
                let error_count = stderr.matches("error:").count();
                issues_found += error_count;
                println!("  ⚠️  Found {} compilation error(s)", error_count);
                
                // Show first 5 errors
                for line in stderr.lines().filter(|l| l.contains("error:")).take(5) {
                    println!("    {}", line);
                }
            } else {
                println!("  ✅ No compilation errors");
            }
        }
        Err(e) => println!("  ❌ Failed to run cargo check: {}", e),
    }
    
    // ============================================================    // 2. CARGO CLIPPY - Advanced linting
    // ============================================================
    println!("\n[2/6] Running cargo clippy...");
    println!("------------------------------------------------------------");
    
    let clippy = Command::new("cargo")
        .args(&["clippy", "--all-targets", "--", "-D", "warnings"])
        .output();
    
    match clippy {
        Ok(output) => {
            if !output.status.success() {
                let stderr = String::from_utf8_lossy(&output.stderr);
                let warning_count = stderr.matches("warning:").count() + stderr.matches("error:").count();
                issues_found += warning_count;
                println!("  ⚠️  Found {} clippy warning(s)", warning_count);
            } else {
                println!("  ✅ No clippy warnings");
            }
        }
        Err(e) => println!("  ❌ Failed to run cargo clippy: {}", e),
    }
    
    // ============================================================
    // 3. CARGO FIX - Auto-fix issues
    // ============================================================
    println!("\n[3/6] Running cargo fix (auto-fix)...");
    println!("------------------------------------------------------------");
    
    let fix = Command::new("cargo")
        .args(&["fix", "--allow-staged", "--allow-dirty"])
        .output();
    
    match fix {
        Ok(output) => {
            let stdout = String::from_utf8_lossy(&output.stdout);
            let fixed_count = stdout.matches("Fixed").count();
            issues_fixed += fixed_count;
            
            if fixed_count > 0 {
                println!("  ✅ Auto-fixed {} issue(s)", fixed_count);
            } else {
                println!("  ℹ️  No auto-fixable issues");
            }
        }
        Err(e) => println!("  ❌ Failed to run cargo fix: {}", e),
    }
    
    // ============================================================
    // 4. RUSTFMT - Code formatting    // ============================================================
    println!("\n[4/6] Running rustfmt...");
    println!("------------------------------------------------------------");
    
    let fmt = Command::new("cargo")
        .args(&["fmt"])
        .output();
    
    match fmt {
        Ok(output) => {
            if output.status.success() {
                println!("  ✅ Code formatted successfully");
            } else {
                println!("  ⚠️  rustfmt found formatting issues");
            }
        }
        Err(e) => println!("  ❌ Failed to run rustfmt: {}", e),
    }
    
    // ============================================================
    // 5. BRACE BALANCE CHECK
    // ============================================================
    println!("\n[5/6] Checking brace balance...");
    println!("------------------------------------------------------------");
    
    let lib_path = "rust/core/src/lib.rs";
    if let Ok(content) = fs::read_to_string(lib_path) {
        let total_open = content.matches('{').count();
        let total_close = content.matches('}').count();
        let balance = total_open as i32 - total_close as i32;
        
        println!("  File: {}", lib_path);
        println!("  Open braces:  {}", total_open);
        println!("  Close braces: {}", total_close);
        println!("  Balance:      {}", balance);
        
        if balance == 0 {
            println!("  ✅ Brace balance OK");
        } else {
            println!("  ❌ Brace imbalance: {}", balance);
            issues_found += 1;
            
            // Auto-fix
            if balance > 0 {
                println!("  🔧 Adding {} closing brace(s)...", balance);
                let mut file_content = content.clone();
                for _ in 0..balance {
                    file_content.push_str("\n}");
                }
                fs::write(lib_path, file_content).expect("Failed to write file");                issues_fixed += 1;
                println!("  ✅ Fixed brace imbalance");
            }
        }
        
        // Check depth
        let mut depth = 0i32;
        let mut negative_lines: Vec<usize> = Vec::new();
        
        for (i, line) in content.lines().enumerate() {
            depth += line.matches('{').count() as i32;
            depth -= line.matches('}').count() as i32;
            if depth < 0 {
                negative_lines.push(i + 1);
            }
        }
        
        if !negative_lines.is_empty() {
            println!("  ❌ Negative depth at {} lines", negative_lines.len());
            for &nl in negative_lines.iter().take(5) {
                println!("     Line {}", nl);
            }
            issues_found += negative_lines.len();
        } else {
            println!("  ✅ No negative depth");
        }
        
        // Final depth
        if depth != 0 {
            println!("  ❌ Final depth: {} (should be 0)", depth);
            issues_found += 1;
        } else {
            println!("  ✅ Final depth: 0");
        }
    } else {
        println!("  ⚠️  Could not read {}", lib_path);
    }
    
    // ============================================================
    // 6. CARGO AUDIT - Security vulnerabilities
    // ============================================================
    println!("\n[6/6] Running cargo audit...");
    println!("------------------------------------------------------------");
    
    let audit = Command::new("cargo")
        .args(&["audit"])
        .output();
    
    match audit {
        Ok(output) => {            if !output.status.success() {
                let stderr = String::from_utf8_lossy(&output.stderr);
                let vuln_count = stderr.matches("vulnerability").count();
                if vuln_count > 0 {
                    println!("  ⚠️  Found {} security vulnerability(ies)", vuln_count);
                    issues_found += vuln_count;
                }
            } else {
                println!("  ✅ No security vulnerabilities");
            }
        }
        Err(e) => println!("  ℹ️  cargo audit not installed (cargo install cargo-audit)"),
    }
    
    // ============================================================
    // SUMMARY
    // ============================================================
    println!("\n============================================================");
    println!("SUMMARY");
    println!("============================================================");
    println!("  Issues found:  {}", issues_found);
    println!("  Issues fixed:  {}", issues_fixed);
    println!("  Remaining:     {}", issues_found - issues_fixed);
    
    if issues_found == issues_fixed {
        println!("\n  ✅ ALL ISSUES RESOLVED!");
    } else if issues_found == 0 {
        println!("\n  ✅ NO ISSUES FOUND!");
    } else {
        println!("\n  ⚠️  {} issue(s) need manual review", issues_found - issues_fixed);
    }
    
    println!("\n============================================================");
    println!("NEXT STEPS");
    println!("============================================================");
    println!("  1. Review changes: git diff");
    println!("  2. Test locally:   cargo test");
    println!("  3. Commit:         git add . && git commit -m 'fix: Auto-fixed issues'");
    println!("  4. Push:           git push");
    println!("============================================================\n");
    
    std::process::exit(if issues_found == issues_fixed { 0 } else { 1 });
}
