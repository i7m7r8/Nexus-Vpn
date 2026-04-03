# Nexus VPN — Masterplan: SNI → Tor Pipeline

> **Goal**: Like Invisible Pro — user configures a decoy SNI hostname. All TLS traffic
> exiting the device carries that decoy SNI in its ClientHello. The *real* destination
> hostname is recovered from the original ClientHello, then the connection is forwarded
> through the Tor network via Arti.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Android App (Kotlin)                                           │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ VpnService  │──│ TUN fd (JNI) │──│ Rust Core (lib.rs)     │ │
│  │ Builder     │  │ 10.8.0.2/24  │  │                        │ │
│  └─────────────┘  └──────────────┘  │  ┌──────────────────┐  │ │
│                                      │  │ smoltcp stack    │  │ │
│  User sets:                          │  │ (VirtualDevice)  │  │ │
│  • decoy SNI host                    │  └────────┬─────────┘  │ │
│  • (optional) Tor bridges            │           │             │ │
│                                      │  ┌────────▼─────────┐  │ │
│                                      │  │ SNI Interceptor  │  │ │
│                                      │  │ 1. Parse TLS CH  │  │ │
│                                      │  │ 2. Extract real  │  │ │
│                                      │  │    hostname      │  │ │
│                                      │  │ 3. Rewrite SNI → │ │ │
│                                      │  │    decoy in pkt  │  │ │
│                                      │  └───┬──────────┬───┘  │ │
│                                      │      │          │       │ │
│                                      │  ┌───▼──┐  ┌───▼────┐  │ │
│                                      │  │Reply │  │Bridge  │  │ │
│                                      │  │to app│  │to Tor  │  │ │
│                                      │  └──────┘  │(Arti)  │  │ │
│                                      │            └────────┘  │ │
│                                      └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase 1 — Fix the Data Bridge (CRITICAL)

### Problem
`Bridge::copy` in `lib.rs` is one-directional, discards all data, and sockets
are re-bridged every ~2ms poll cycle creating infinite duplicate Tor connections.

### Solution

**1a. Make `Bridge::copy` truly bidirectional**

```rust
struct Bridge {
    handle: smoltcp::iface::SocketHandle,
}

impl Bridge {
    async fn copy(
        handle: smoltcp::iface::SocketHandle,
        mut tor_stream: arti_client::DataStream,
        mut iface: smoltcp::iface::Interface,
        mut device: VirtualDevice,
        sockets: &mut smoltcp::iface::SocketSet,
    ) {
        let mut tor_buf = vec![0u8; 8192];
        let mut smoltcp_buf = vec![0u8; 8192];

        loop {
            tokio::select! {
                // Tor → smoltcp
                res = tor_stream.read(&mut tor_buf) => {
                    match res {
                        Ok(0) => break,
                        Ok(n) => {
                            // Write data back to smoltcp socket
                            let socket = sockets.get_mut::<TcpSocket>(handle);
                            if socket.can_send() {
                                let _ = socket.send_slice(&tor_buf[..n]);
                            }
                        }
                        Err(e) => { break; }
                    }
                }
                // smoltcp → Tor
                _ = tokio::time::sleep(Duration::from_millis(1)) => {
                    let socket = sockets.get_mut::<TcpSocket>(handle);
                    if socket.can_recv() {
                        let n = socket.recv_slice(&mut smoltcp_buf).unwrap_or(0);
                        if n > 0 {
                            let _ = tor_stream.write_all(&smoltcp_buf[..n]).await;
                        }
                    }
                    if socket.state() == TcpState::CloseWait || socket.state() == TcpState::Closed {
                        break;
                    }
                }
            }
        }
    }
}
```

**1b. Deduplicate socket bridging**

```rust
// In vpn_main_loop, before the main loop:
let mut bridged: HashSet<smoltcp::iface::SocketHandle> = HashSet::new();

// Inside the poll loop:
for (handle, socket) in stack.socket_set.iter_mut() {
    if let Socket::Tcp(tcp_socket) = socket {
        if tcp_socket.is_active()
            && tcp_socket.state() == smoltcp::socket::tcp::State::Established
            && !bridged.contains(&handle)
        {
            bridged.insert(handle);
            // spawn bridge ONCE per socket
        }
    }
}
```

---

## Phase 2 — Wire Up SNI Parser & Rewriter (CRITICAL)

### Problem
`parser.rs` and `rewriter.rs` exist but are not declared in `sni/mod.rs`.
They are dead code — never compiled, never called.

### Solution

**2a. Add modules to `sni/mod.rs`**

```rust
mod transport;
pub mod parser;
pub mod rewriter;

pub use transport::SniRuntime;
pub use parser::TlsParser;
pub use rewriter::SniRewriter;
```

**2b. Add bounds-checking to parser** (prevent OOB reads on malformed packets)

Every variable-length field read must check `offset + len <= buf.len()` before
advancing the cursor.

**2c. Make rewriter handle "no SNI" case**

Currently `rewrite_sni()` returns `NoSniFound` if the ClientHello lacks an SNI
extension. It must be able to **inject** a new SNI extension block into the
extensions list, adjusting all length fields accordingly.

---

## Phase 3 — The SNI Interceptor Layer (THE CORE FEATURE)

### Problem
There is no code that inspects packet payloads, identifies TLS ClientHello
records, extracts the real hostname, rewrites the SNI, and routes correctly.

### Solution — Insert a `SniInterceptor` between smoltcp and the Tor bridge

```
smoltcp TCP socket established
        │
        ▼
┌──────────────────────────┐
│   SniInterceptor         │
│                          │
│  1. Read TCP payload     │
│     (first bytes = TLS)  │
│                          │
│  2. Is it TLS ClientHello?
│     - content_type == 0x16
│     - handshake_type == 0x01
│                          │
│  3. TlsParser::parse_sni()
│     → extract real hostname│
│                          │
│  4. SniRewriter::rewrite_sni()
│     → replace with decoy  │
│                          │
│  5. Write modified packet │
│     back to smoltcp       │
│                          │
│  6. Store real hostname   │
│     for Tor connection    │
└────────┬─────────────────┘
         │
    real hostname → Tor connect
    modified pkt  → app receives decoy SNI
```

**Implementation:**

```rust
// In lib.rs — new struct
struct SniInterceptor {
    decoy_host: String,
    real_hosts: Arc<Mutex<HashMap<smoltcp::iface::SocketHandle, String>>>,
}

impl SniInterceptor {
    /// Inspect TCP payload, rewrite SNI if TLS ClientHello, return modified payload
    fn intercept(
        &self,
        handle: smoltcp::iface::SocketHandle,
        payload: &mut Vec<u8>,
    ) -> Option<String> {
        // Check if this is a TLS ClientHello
        if payload.len() < 5 { return None; }
        if payload[0] != 0x16 { return None; } // Handshake
        if payload[5] != 0x01 { return None; } // ClientHello

        // Parse original SNI
        let real_host = TlsParser::parse_sni(payload).ok()?;

        // Rewrite SNI to decoy
        if SniRewriter::rewrite_sni(payload, &self.decoy_host).is_ok() {
            self.real_hosts.lock().unwrap().insert(handle, real_host.clone());
            return Some(real_host);
        }
        None
    }
}
```

**Integration into main loop:**

```rust
// When detecting established TCP sockets:
for (handle, socket) in stack.socket_set.iter_mut() {
    if let Socket::Tcp(tcp_socket) = socket {
        if tcp_socket.can_recv() && !bridged.contains(&handle) {
            // Peek at incoming payload
            let mut payload_buf = vec![0u8; 16384];
            let n = tcp_socket.recv_slice(&mut payload_buf).unwrap_or(0);
            if n > 0 {
                payload_buf.truncate(n);

                // Try SNI interception
                if let Some(real_host) = interceptor.intercept(handle, &mut payload_buf) {
                    // Write modified packet (with decoy SNI) back to socket
                    // so the app sees the rewritten ClientHello
                    let _ = tcp_socket.send_slice(&payload_buf);

                    // Store real_host for Tor connection
                    // Bridge will use real_host instead of IP address
                }
            }
        }
    }
}
```

---

## Phase 4 — Hostname-Aware Tor Connections

### Problem
Currently `endpoint.addr.to_string()` gives an IP like `93.184.216.34`.
The original hostname is lost, breaking virtual hosting on the server side.

### Solution
Use the hostname extracted by the SNI interceptor:

```rust
// After SNI interception, real_host is stored in interceptor.real_hosts
let real_host = interceptor.real_hosts.lock().unwrap()
    .remove(&handle)
    .unwrap_or_else(|| endpoint.addr.to_string()); // fallback to IP

tor_clone.connect((real_host, target_port)).await
```

---

## Phase 5 — DNS Interceptor (MAJOR)

### Problem
DNS queries are intercepted and discarded. No resolution, no response.

### Solution
Implement a simple DNS forwarder via Tor:

```rust
// In main loop, when DNS query arrives:
if let Ok((data, endpoint)) = dns_socket.recv() {
    let query = data.to_vec();

    // Parse DNS query, extract domain
    // Forward DNS resolution through Tor
    // Send DNS response back to app via UDP socket
}
```

Alternative (simpler): Let the app resolve DNS normally, intercept the
resulting TCP connections by IP. The SNI interceptor recovers the hostname
from the TLS ClientHello anyway. **DNS interception can be deferred.**

---

## Phase 6 — Bridge/Pluggable Transport Support (MINOR)

### Problem
UI has bridge configuration (obfs4/meek/snowflake) but `TorClientConfig::default()`
ignores it.

### Solution
Pass bridge config from Android prefs → JNI → Rust → `TorClientConfig`:

```kotlin
// In NexusVpnService.kt
val bridgeConfig = prefs.getBridgeConfig() // JSON string
initVpnNative(tunFd!!.fd, sni, bridgeConfig)
```

```rust
// In lib.rs
let mut config = TorClientConfig::default();
if !bridge_config.is_empty() {
    // Configure Arti with bridges
}
```

---

## Phase 7 — Kill Switch (MINOR)

### Problem
`killSwitch` pref exists but no iptables rules are set.

### Solution
On Android, use `VpnService.Builder.addAllowedApplication()` /
`addDisallowedApplication()` instead of iptables. Or set up a
`ProtectSocket` callback that blocks non-Tor traffic.

---

## Implementation Order (for CI efficiency)

| Order | Phase | Files Changed | Risk |
|-------|-------|---------------|------|
| 1 | **Phase 1** — Fix Bridge | `lib.rs` | High — core data path |
| 2 | **Phase 2** — Wire parser/rewriter | `sni/mod.rs`, `sni/parser.rs`, `sni/rewriter.rs` | Low — just wiring |
| 3 | **Phase 3** — SNI Interceptor | `lib.rs`, new `sni/interceptor.rs` | High — core feature |
| 4 | **Phase 4** — Hostname Tor | `lib.rs` | Medium |
| 5 | **Phase 5** — DNS (deferred) | `lib.rs` | Medium |
| 6 | **Phase 6** — Bridges | `lib.rs`, `NexusVpnService.kt` | Low |
| 7 | **Phase 7** — Kill switch | `NexusVpnService.kt` | Low |

---

## What Works Today (After Build Fix)

- ✅ Android UI (Jetpack Compose, Proton VPN-style)
- ✅ TUN interface setup via VpnService.Builder
- ✅ JNI bindings (init/stop/setSNI)
- ✅ smoltcp virtual stack (10.8.0.2/24, MTU 1500)
- ✅ Arti bootstrap to Tor network
- ✅ Raw IP packets flow TUN → smoltcp

## What's Broken (After Build Fix)

- ❌ Bridge discards all data (one-directional, never writes)
- ❌ Infinite duplicate Tor connections (no dedup)
- ❌ SNI parser/rewriter are dead code (not in module tree)
- ❌ SniRuntime is a passthrough (logs "spoofing" but does nothing)
- ❌ DNS queries ignored
- ❌ Tor connections use IP instead of hostname

## End State

User sets `decoy.example.com` as SNI host → presses CONNECT →
all TLS ClientHello packets have their SNI rewritten to `decoy.example.com`
→ the real hostname is extracted → connection forwarded through Tor →
server sees decoy SNI, serves content, traffic exits via Tor exit node
to the real destination. **Exactly like Invisible Pro.**
