use std::fs;
use std::collections::HashMap;

fn main() {
    let filepath = "rust/core/src/lib.rs";
    let content = fs::read_to_string(filepath).expect("Cannot read file");
    let lines: Vec<&str> = content.lines().collect();
    
    println!("============================================================");
    println!("🦀 DEEP STRUCTURAL AUDIT - rust/core/src/lib.rs");
    println!("============================================================\n");
    
    let mut all_passed = true;
    
    // ============================================================
    // AUDIT 1: BRACE BALANCE
    // ============================================================
    println!("[AUDIT 1] Brace Balance");
    println!("------------------------------------------------------------");
    
    let total_open = content.matches('{').count();
    let total_close = content.matches('}').count();
    
    if total_open == total_close {
        println!("  ✅ PASS: {{={}}}, }}={}, balance=0", total_open, total_close);
    } else {
        println!("  ❌ FAIL: {{={}}}, }}={}, balance={}", total_open, total_close, total_open as i32 - total_close as i32);
        all_passed = false;
    }
    
    // ============================================================
    // AUDIT 2: DEPTH TRACKING
    // ============================================================
    println!("\n[AUDIT 2] Depth Tracking");
    println!("------------------------------------------------------------");
    
    let mut depth = 0i32;
    let mut max_depth = 0i32;
    let mut negative_lines: Vec<usize> = Vec::new();
    
    for (i, line) in lines.iter().enumerate() {
        depth += line.matches('{').count() as i32;
        depth -= line.matches('}').count() as i32;
        if depth > max_depth { max_depth = depth; }
        if depth < 0 { negative_lines.push(i + 1); }
    }
    
    if depth == 0 && negative_lines.is_empty() {
        println!("  ✅ PASS: Final depth=0, no negative depth lines");    } else {
        println!("  ❌ FAIL: Final depth={}, negative lines={}", depth, negative_lines.len());
        for nl in negative_lines.iter().take(5) {
            println!("     Line {}", nl);
        }
        all_passed = false;
    }
    
    // ============================================================
    // AUDIT 3: ENUM CLOSURES
    // ============================================================
    println!("\n[AUDIT 3] Enum Closures");
    println!("------------------------------------------------------------");
    
    let enums = [
        ("VpnProtocol", "pub enum VpnProtocol"),
        ("CipherSuite", "pub enum CipherSuite"),
        ("TlsVersion", "pub enum TlsVersion"),
        ("ConnectionState", "pub enum ConnectionState"),
        ("DnsMode", "pub enum DnsMode"),
        ("SplitTunnelMode", "pub enum SplitTunnelMode"),
        ("BatteryProfile", "pub enum BatteryProfile"),
    ];
    
    for (name, pattern) in enums.iter() {
        let mut found_start = false;
        let mut depth = 0i32;
        let mut enum_closed = false;
        
        for (i, line) in lines.iter().enumerate() {
            if line.contains(pattern) && line.contains('{') {
                found_start = true;
                depth = 1;
            } else if found_start && depth > 0 {
                depth += line.matches('{').count() as i32;
                depth -= line.matches('}').count() as i32;
                if depth == 0 {
                    enum_closed = true;
                    break;
                }
            }
        }
        
        if enum_closed {
            println!("  ✅ PASS: {} properly closed", name);
        } else {
            println!("  ❌ FAIL: {} NOT properly closed", name);
            all_passed = false;
        }
    }    
    // ============================================================
    // AUDIT 4: IMPL BLOCK CLOSURES
    // ============================================================
    println!("\n[AUDIT 4] Impl Block Closures");
    println!("------------------------------------------------------------");
    
    let impls = [
        ("EncryptionEngine", "impl EncryptionEngine"),
        ("SniHandler", "impl SniHandler"),
        ("TorManager", "impl TorManager"),
        ("VpnConnection", "impl VpnConnection"),
        ("VpnEngine", "impl VpnEngine"),
        ("ConnectionPool", "impl ConnectionPool"),
        ("DnsPrivacyEngine", "impl DnsPrivacyEngine"),
        ("LeakPreventionEngine", "impl LeakPreventionEngine"),
        ("BatteryOptimizer", "impl BatteryOptimizer"),
        ("NexusVpnEngine", "impl NexusVpnEngine"),
    ];
    
    for (name, pattern) in impls.iter() {
        let mut found_start = false;
        let mut depth = 0i32;
        let mut impl_closed = false;
        let mut start_line = 0;
        let mut end_line = 0;
        
        for (i, line) in lines.iter().enumerate() {
            if line.contains(pattern) && line.contains('{') {
                found_start = true;
                depth = 1;
                start_line = i + 1;
            } else if found_start && depth > 0 {
                depth += line.matches('{').count() as i32;
                depth -= line.matches('}').count() as i32;
                if depth == 0 {
                    impl_closed = true;
                    end_line = i + 1;
                    break;
                }
            }
        }
        
        if impl_closed {
            println!("  ✅ PASS: {} (lines {}-{})", name, start_line, end_line);
        } else {
            println!("  ❌ FAIL: {} NOT properly closed", name);
            all_passed = false;
        }
    }    
    // ============================================================
    // AUDIT 5: STRUCT CLOSURES
    // ============================================================
    println!("\n[AUDIT 5] Struct Closures");
    println!("------------------------------------------------------------");
    
    let structs = [
        ("VpnServer", "pub struct VpnServer"),
        ("ServerCapabilities", "pub struct ServerCapabilities"),
        ("SniConfig", "pub struct SniConfig"),
        ("TorConfig", "pub struct TorConfig"),
        ("DnsConfig", "pub struct DnsConfig"),
        ("SplitTunnelConfig", "pub struct SplitTunnelConfig"),
        ("LeakPreventionConfig", "pub struct LeakPreventionConfig"),
        ("VpnConnectionStats", "pub struct VpnConnectionStats"),
        ("ConnectionLog", "pub struct ConnectionLog"),
        ("PacketStats", "pub struct PacketStats"),
        ("DetailedConnectionStats", "pub struct DetailedConnectionStats"),
        ("LeakTestResult", "pub struct LeakTestResult"),
        ("PooledConnection", "pub struct PooledConnection"),
    ];
    
    for (name, pattern) in structs.iter() {
        let mut found_start = false;
        let mut depth = 0i32;
        let mut struct_closed = false;
        
        for line in lines.iter() {
            if line.contains(pattern) && line.contains('{') {
                found_start = true;
                depth = 1;
            } else if found_start && depth > 0 {
                depth += line.matches('{').count() as i32;
                depth -= line.matches('}').count() as i32;
                if depth == 0 {
                    struct_closed = true;
                    break;
                }
            }
        }
        
        if struct_closed {
            println!("  ✅ PASS: {} properly closed", name);
        } else {
            println!("  ❌ FAIL: {} NOT properly closed", name);
            all_passed = false;
        }
    }
        // ============================================================
    // AUDIT 6: MERGED LINES
    // ============================================================
    println!("\n[AUDIT 6] Merged Comment/Code Lines");
    println!("------------------------------------------------------------");
    
    let mut merged_count = 0;
    let mut merged_lines: Vec<usize> = Vec::new();
    
    for (i, line) in lines.iter().enumerate() {
        if line.contains("///") && (line.contains("pub ") || line.contains("fn ") || line.contains("struct ") || line.contains("enum ")) {
            // Check if comment and code are on same line (not separated by newline)
            let parts: Vec<&str> = line.split("///").collect();
            if parts.len() > 1 && parts[1].contains("pub") {
                merged_count += 1;
                merged_lines.push(i + 1);
            }
        }
    }
    
    if merged_count == 0 {
        println!("  ✅ PASS: No merged comment/code lines");
    } else {
        println!("  ❌ FAIL: {} merged lines found", merged_count);
        for ml in merged_lines.iter().take(10) {
            println!("     Line {}", ml);
        }
        all_passed = false;
    }
    
    // ============================================================
    // AUDIT 7: END OF FILE STRUCTURE
    // ============================================================
    println!("\n[AUDIT 7] End of File Structure");
    println!("------------------------------------------------------------");
    
    let mut consecutive_close = 0;
    for i in (0..lines.len()).rev() {
        if lines[i].trim() == "}" {
            consecutive_close += 1;
        } else {
            break;
        }
    }
    
    println!("  Consecutive }} at end: {}", consecutive_close);
    
    // Show last 15 lines
    println!("\n  Last 15 lines:");
    for i in (lines.len().saturating_sub(15))..lines.len() {        let marker = if lines[i].trim() == "}" { "  " } else { "  " };
        println!("    {}L{:4}: {}", marker, i + 1, &lines[i][..lines[i].len().min(60)]);
    }
    
    if consecutive_close >= 2 && consecutive_close <= 4 {
        println!("\n  ✅ PASS: Reasonable number of closing braces");
    } else {
        println!("\n  ⚠️  WARNING: Unusual number of closing braces");
    }
    
    // ============================================================
    // AUDIT 8: SECTION BALANCE
    // ============================================================
    println!("\n[AUDIT 8] Section Balance");
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
            
            if bal == 0 {
                println!("  ✅ PASS: {:18} balance=0", name);
            } else {
                println!("  ❌ FAIL: {:18} balance={}", name, bal);
                all_passed = false;
            }
        }
    }
    
    // ============================================================
    // AUDIT 9: MOD TESTS CLOSURE
    // ============================================================
    println!("\n[AUDIT 9] Tests Module Closure");    println!("------------------------------------------------------------");
    
    let mut tests_found = false;
    let mut tests_closed = false;
    let mut tests_depth = 0i32;
    
    for (i, line) in lines.iter().enumerate() {
        if line.contains("#[cfg(test)]") {
            tests_found = true;
            // Find the mod tests {
            for j in i..lines.len().min(i + 5) {
                if lines[j].contains("mod tests") && lines[j].contains('{') {
                    tests_depth = 1;
                    break;
                }
            }
        } else if tests_found && tests_depth > 0 {
            tests_depth += line.matches('{').count() as i32;
            tests_depth -= line.matches('}').count() as i32;
            if tests_depth == 0 {
                tests_closed = true;
                println!("  Tests module closes at line {}", i + 1);
                break;
            }
        }
    }
    
    if tests_found && tests_closed {
        println!("  ✅ PASS: Tests module properly closed");
    } else if !tests_found {
        println!("  ⚠️  WARNING: Tests module not found");
    } else {
        println!("  ❌ FAIL: Tests module NOT properly closed");
        all_passed = false;
    }
    
    // ============================================================
    // FINAL SUMMARY
    // ============================================================
    println!("\n============================================================");
    println!("FINAL AUDIT SUMMARY");
    println!("============================================================");
    
    if all_passed {
        println!("\n  ✅✅✅ ALL AUDITS PASSED! ✅✅✅");
        println!("\n  File structure is VALID and ready for compilation!");
    } else {
        println!("\n  ❌ SOME AUDITS FAILED - Review issues above");
    }
        println!("\n============================================================");
    println!("QUICK STATS");
    println!("============================================================");
    println!("  Total lines:     {}", lines.len());
    println!("  Total {{:         {}", total_open);
    println!("  Total }}:         {}", total_close);
    println!("  Max depth:       {}", max_depth);
    println!("  Final depth:     {}", depth);
    println!("  Merged lines:    {}", merged_count);
    println!("  End braces:      {}", consecutive_close);
    println!("============================================================\n");
    
    std::process::exit(if all_passed { 0 } else { 1 });
}
