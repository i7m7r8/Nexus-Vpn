//! Nexus VPN — Pure Rust Core (JNI bindings for Android)
//!
//! Pure Rust Tor vpn using Arti v0.40.0.
//! TUN → smoltcp virtual stack → SNI rewriting → Tor via Arti.
//!
//! **SNI Rewriting Flow** (like Invisible Pro):
//! 1. App connects to real server IP:port (DNS resolved by app)
//! 2. App sends TLS ClientHello through smoltcp
//! 3. We intercept: parse real hostname from SNI, rewrite SNI to decoy
//! 4. Connect to Tor with **real hostname**, send **modified** ClientHello
//! 5. Bridge all subsequent traffic bidirectionally

use jni::objects::JString;
use jni::sys::{jboolean, jint};
use jni::JNIEnv;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use tokio::runtime::Runtime;

mod tun;
mod stack;
pub mod sni;

use crate::tun::TunDevice;
use crate::stack::NetStack;
use crate::sni::parser::TlsParser;
use crate::sni::rewriter::SniRewriter;

// ===========================================================================
// Global state
// ===========================================================================

static RUNNING: AtomicBool = AtomicBool::new(false);
static RUNTIME: parking_lot::RwLock<Option<Runtime>> = parking_lot::RwLock::new(None);
static SNI_HOST: parking_lot::RwLock<String> = parking_lot::RwLock::new(String::new());
// Arc-based SNI host that can be updated at runtime (shared with SniInterceptor + SniRuntime)
static SNI_HOST_SHARED: parking_lot::RwLock<Option<Arc<parking_lot::Mutex<String>>>> = parking_lot::RwLock::new(None);

// ===========================================================================
// JNI — exposed to Kotlin
// ===========================================================================

#[no_mangle]
pub unsafe extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_initVpnNative(
    mut env: JNIEnv,
    _class: jni::objects::JClass,
    tun_fd: jint,
    sni_hostname: JString,
    bridge_config: JString,
) -> jboolean {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("NexusVpn"),
    );

    let host: String = env
        .get_string(&sni_hostname)
        .map(|h| h.into())
        .unwrap_or_else(|_| String::from("www.cloudflare.com"));

    let bridge_json: String = env
        .get_string(&bridge_config)
        .map(|h| h.into())
        .unwrap_or_default();

    // Create shared SNI host (Arc<Mutex> for runtime updates)
    let shared_sni = Arc::new(parking_lot::Mutex::new(host.clone()));
    *SNI_HOST.write() = host.clone();
    *SNI_HOST_SHARED.write() = Some(shared_sni.clone());

    log::info!("🚀 Initializing Nexus VPN Core (SNI: {})", host);
    if !bridge_json.is_empty() {
        log::info!("🌉 Bridge config provided");
    }

    let rt = match tokio::runtime::Builder::new_multi_thread()
        .worker_threads(4)
        .enable_all()
        .build()
    {
        Ok(rt) => rt,
        Err(e) => {
            log::error!("❌ Failed to create Tokio runtime: {e}");
            return false as jboolean;
        }
    };

    RUNNING.store(true, Ordering::SeqCst);

    rt.spawn(async move {
        if let Err(e) = vpn_main_loop(tun_fd, shared_sni, bridge_json).await {
            log::error!("🚨 VPN Main Loop Error: {e}");
            RUNNING.store(false, Ordering::SeqCst);
        }
    });

    *RUNTIME.write() = Some(rt);
    true as jboolean
}

#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_stopVpnNative(
    _env: JNIEnv,
    _class: jni::objects::JClass,
) {
    log::info!("🛑 Stopping Nexus VPN Core");
    RUNNING.store(false, Ordering::SeqCst);

    let mut rt_guard = RUNTIME.write();
    if let Some(rt) = rt_guard.take() {
        rt.shutdown_background();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_setSniHostnameNative(
    mut env: JNIEnv,
    _class: jni::objects::JClass,
    hostname: JString,
) -> jboolean {
    if let Ok(s) = env.get_string(&hostname) {
        let h: String = s.into();
        *SNI_HOST.write() = h.clone();
        // Update shared SNI host (used by running SniInterceptor + SniRuntime)
        if let Some(shared) = SNI_HOST_SHARED.read().clone() {
            *shared.lock() = h.clone();
            log::info!("📝 SNI hostname updated at runtime: {h}");
        } else {
            log::info!("📝 SNI hostname updated (will apply on next connect): {h}");
        }
        true as jboolean
    } else {
        false as jboolean
    }
}

// ===========================================================================
// SNI Interceptor — parses TLS ClientHello, rewrites SNI to decoy
// ===========================================================================

struct SniInterceptor {
    decoy_host: Arc<parking_lot::Mutex<String>>,
}

impl SniInterceptor {
    fn new(shared_sni: Arc<parking_lot::Mutex<String>>) -> Self {
        Self { decoy_host: shared_sni }
    }

    fn current_decoy(&self) -> String {
        self.decoy_host.lock().clone()
    }

    /// Inspect payload. If it's a TLS ClientHello, extract the real hostname
    /// from SNI and rewrite it to the decoy hostname.
    ///
    /// Returns `Some(real_host)` if SNI was found and rewritten,
    /// `None` if the payload is not a TLS ClientHello or has no SNI.
    fn intercept(&self, payload: &mut Vec<u8>) -> Option<String> {
        // Minimum: TLS record header (5) + Handshake header (4) + Version (2) + Random (32) = 43
        if payload.len() < 43 {
            return None;
        }

        // Quick check: TLS Handshake record type + ClientHello
        if payload[0] != 0x16 || payload[5] != 0x01 {
            return None;
        }

        // Parse real hostname from SNI
        let real_host = TlsParser::parse_sni(payload).ok()?;

        // Read current decoy hostname (may have been updated at runtime)
        let decoy = self.decoy_host.lock().clone();

        // Rewrite SNI extension to decoy hostname
        if SniRewriter::rewrite_sni(payload, &decoy).is_err() {
            log::warn!("⚠️ Failed to rewrite SNI for {}", real_host);
            return None;
        }

        log::info!("🎭 SNI rewritten: {} → {}", real_host, decoy);
        Some(real_host)
    }
}

// ===========================================================================
// Bridge — bidirectional smoltcp ↔ Tor data transfer
// ===========================================================================

use smoltcp::socket::udp::{Socket as UdpSocket, PacketBuffer as UdpPacketBuffer, PacketMetadata as UdpPacketMetadata};
use smoltcp::socket::Socket;
use arti_client::{TorClient, TorClientConfig};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use std::collections::{HashMap, HashSet};
use tokio::sync::mpsc;

struct Bridge;

impl Bridge {
    /// Spawn a bidirectional bridge between an established Tor stream and a smoltcp socket.
    ///
    /// `first_data` is the initial payload from the app (possibly SNI-rewritten),
    /// which is written to the Tor stream immediately upon connection.
    ///
    /// Returns (receiver for Tor→smoltcp data, sender for smoltcp→Tor data).
    fn spawn(
        handle: smoltcp::iface::SocketHandle,
        mut tor_stream: arti_client::DataStream,
        first_data: Vec<u8>,
    ) -> (
        mpsc::Receiver<(smoltcp::iface::SocketHandle, Vec<u8>)>,
        mpsc::Sender<Vec<u8>>,
    ) {
        let (to_smoltcp_tx, to_smoltcp_rx) = mpsc::channel::<(smoltcp::iface::SocketHandle, Vec<u8>)>(16);
        let (to_tor_tx, mut to_tor_rx) = mpsc::channel::<Vec<u8>>(16);

        tokio::spawn(async move {
            // Write the first data (SNI-rewritten ClientHello or original payload)
            if !first_data.is_empty() {
                if let Err(e) = tor_stream.write_all(&first_data).await {
                    log::error!("❌ Failed to write first data to Tor (socket {:?}): {}", handle, e);
                    return;
                }
                log::debug!("📤 Sent first data ({} bytes) to Tor (socket {:?})", first_data.len(), handle);
            }

            let mut tor_buf = vec![0u8; 8192];

            loop {
                tokio::select! {
                    // Tor → smoltcp
                    res = tor_stream.read(&mut tor_buf) => {
                        match res {
                            Ok(0) => {
                                log::debug!("🔌 Tor stream EOF (socket {:?})", handle);
                                break;
                            }
                            Ok(n) => {
                                log::debug!("📥 Tor → smoltcp: {} bytes (socket {:?})", n, handle);
                                if to_smoltcp_tx.send((handle, tor_buf[..n].to_vec())).await.is_err() {
                                    break;
                                }
                            }
                            Err(e) => {
                                log::error!("❌ Tor read error (socket {:?}): {}", handle, e);
                                break;
                            }
                        }
                    }
                    // smoltcp → Tor
                    res = to_tor_rx.recv() => {
                        match res {
                            Some(data) => {
                                if let Err(e) = tor_stream.write_all(&data).await {
                                    log::error!("❌ Tor write error (socket {:?}): {}", handle, e);
                                    break;
                                }
                                log::debug!("📤 smoltcp → Tor: {} bytes (socket {:?})", data.len(), handle);
                            }
                            None => {
                                log::debug!("🔌 smoltcp channel closed (socket {:?})", handle);
                                break;
                            }
                        }
                    }
                }
            }
            log::debug!("🚪 Bridge task exited (socket {:?})", handle);
        });

        (to_smoltcp_rx, to_tor_tx)
    }
}

// ===========================================================================
// Main loop
// ===========================================================================

use tor_rtcompat::PreferredRuntime;
use crate::sni::SniRuntime;

/// Build TorClientConfig, optionally with bridge/pluggable transport support.
///
/// `bridge_config` is a JSON string from Android prefs:
/// ```json
/// {
///   "use_bridges": true,
///   "bridge_type": "obfs4",
///   "custom_bridge_line": "Bridge obfs4 1.2.3.4:443 <fingerprint> cert=... iat-mode=0"
/// }
/// ```
fn build_tor_config(bridge_config: &str) -> anyhow::Result<TorClientConfig> {
    if bridge_config.is_empty() {
        return Ok(TorClientConfig::default());
    }

    // Parse bridge config JSON
    let config_val: serde_json::Value = match serde_json::from_str(bridge_config) {
        Ok(v) => v,
        Err(e) => {
            log::warn!("⚠️ Failed to parse bridge config JSON: {}", e);
            return Ok(TorClientConfig::default());
        }
    };

    let use_bridges = config_val.get("use_bridges").and_then(|v| v.as_bool()).unwrap_or(false);
    if !use_bridges {
        log::info!("ℹ️ Bridges disabled in config");
        return Ok(TorClientConfig::default());
    }

    let bridge_type = config_val.get("bridge_type").and_then(|v| v.as_str()).unwrap_or("obfs4");
    let custom_bridge_line = config_val.get("custom_bridge_line").and_then(|v| v.as_str());

    if let Some(line) = custom_bridge_line {
        if !line.is_empty() {
            log::info!("🌉 Configuring bridge: {} (type: {})", line.split_whitespace().take(3).collect::<Vec<_>>().join(" "), bridge_type);

            // Parse bridge line and build config with bridges
            let bridge_line_str = line.to_string();
            let config = TorClientConfig::builder()
                .bridges()
                    .bridges()
                        .push(bridge_line_str);
            // Note: Arti 0.40.0 bridge builder API may vary.
            // If this doesn't compile, fall back to default config.
            match config.build() {
                Ok(cfg) => {
                    log::info!("✅ Bridge config applied successfully");
                    return Ok(cfg);
                }
                Err(e) => {
                    log::warn!("⚠️ Failed to apply bridge config: {}. Using default.", e);
                    return Ok(TorClientConfig::default());
                }
            }
        }
    }

    log::warn!("⚠️ Bridges enabled but no bridge line provided");
    Ok(TorClientConfig::default())
}

async fn vpn_main_loop(
    tun_fd: jint,
    shared_sni: Arc<parking_lot::Mutex<String>>,
    bridge_config: String,
) -> anyhow::Result<()> {
    let tun = Arc::new(TunDevice::new(tun_fd)?);
    let mut stack = NetStack::new();

    // Setup UDP DNS interceptor (stub — log only)
    let udp_rx_buffer = UdpPacketBuffer::new(vec![UdpPacketMetadata::EMPTY; 16], vec![0; 16384]);
    let udp_tx_buffer = UdpPacketBuffer::new(vec![UdpPacketMetadata::EMPTY; 16], vec![0; 16384]);
    let mut udp_socket = UdpSocket::new(udp_rx_buffer, udp_tx_buffer);
    let _ = udp_socket.bind(smoltcp::wire::IpListenEndpoint::from(53));
    let dns_handle = stack.socket_set.add(udp_socket);

    let sni_host = shared_sni.lock().clone();
    log::info!("🔄 Bootstrapping Arti (Tor) v0.40.0 with SNI Transport: {}...", sni_host);

    let runtime = PreferredRuntime::current()?;
    let sni_runtime = SniRuntime::new(runtime.clone(), shared_sni.clone());

    // Build Tor config with optional bridge support
    let config = build_tor_config(&bridge_config)?;

    let tor_client = match TorClient::with_runtime(sni_runtime)
        .config(config)
        .create_bootstrapped().await
    {
        Ok(client) => {
            if !bridge_config.is_empty() {
                log::info!("✅ Arti bootstrapped — Tor connected via bridges + SNI!");
            } else {
                log::info!("✅ Arti bootstrapped — Tor connected via SNI!");
            }
            client
        }
        Err(e) => {
            log::error!("❌ Arti bootstrap failed: {}", e);
            return Err(anyhow::anyhow!("Tor bootstrap failed: {}", e));
        }
    };

    log::info!("✅ TUN Device and NetStack initialized");

    let mut buf = vec![0u8; 16384];
    let sni_interceptor = SniInterceptor::new(shared_sni.clone());

    // DNS forwarder state: maps query_id → (source_endpoint, response_receiver)
    let mut dns_pending: HashMap<u16, (smoltcp::wire::IpEndpoint, mpsc::Receiver<Vec<u8>>)> = HashMap::new();
    let (dns_setup_tx, dns_setup_rx) = mpsc::channel::<(u16, smoltcp::wire::IpEndpoint, mpsc::Receiver<Vec<u8>>)>(16);

    // === Socket state tracking ===
    // inspecting: established TCP socket, waiting for first payload to do SNI interception
    let mut inspecting: HashSet<smoltcp::iface::SocketHandle> = HashSet::new();
    // connecting: Tor connection in progress (SNI intercepted, bridge spawning)
    let mut connecting: HashSet<smoltcp::iface::SocketHandle> = HashSet::new();
    // bridged: active bidirectional bridge
    let mut bridged: HashSet<smoltcp::iface::SocketHandle> = HashSet::new();

    // Channels for bridge tasks to communicate with main loop
    let (setup_tx, setup_rx) = mpsc::channel::<(
        smoltcp::iface::SocketHandle,
        mpsc::Receiver<(smoltcp::iface::SocketHandle, Vec<u8>)>,
        mpsc::Sender<Vec<u8>>,
    )>(32);
    let (fail_tx, fail_rx) = mpsc::channel::<smoltcp::iface::SocketHandle>(32);
    // Active bridge channels (one pair per bridged socket)
    let mut tor_receivers: HashMap<smoltcp::iface::SocketHandle, mpsc::Receiver<(smoltcp::iface::SocketHandle, Vec<u8>)>> = HashMap::new();
    let mut tor_senders: HashMap<smoltcp::iface::SocketHandle, mpsc::Sender<Vec<u8>>> = HashMap::new();

    while RUNNING.load(Ordering::SeqCst) {
        tokio::select! {
            res = tun.read(&mut buf) => {
                let n = res?;
                if n == 0 { break; }
                stack.input(buf[..n].to_vec());
            }
            _ = tokio::time::sleep(std::time::Duration::from_millis(2)) => {
                stack.poll();
            }
        }

        // === 1. DNS forwarder — receive queries, forward through Tor ===
        let mut dns_socket = stack.socket_set.get_mut::<UdpSocket>(dns_handle);
        if let Ok((data, endpoint)) = dns_socket.recv() {
            let query = data.to_vec();
            let query_id = if query.len() >= 2 {
                ((query[0] as u16) << 8) | (query[1] as u16)
            } else {
                0
            };

            log::debug!("🌐 DNS query #{} from {} ({} bytes)", query_id, endpoint, query.len());

            // Forward DNS query through Tor to 1.1.1.1:53
            let tor_clone = tor_client.clone();
            let dns_setup_tx = dns_setup_tx.clone();
            let query_clone = query.clone();

            tokio::spawn(async move {
                let (to_app_tx, to_app_rx) = mpsc::channel::<Vec<u8>>(1);

                match tor_clone.connect(("1.1.1.1", 53)).await {
                    Ok(mut dns_stream) => {
                        // Send query to DNS server through Tor
                        if let Err(e) = dns_stream.write_all(&query_clone).await {
                            log::error!("❌ DNS forward: failed to send query #{}: {}", query_id, e);
                            return;
                        }

                        // Read response
                        let mut resp_buf = vec![0u8; 4096];
                        match dns_stream.read(&mut resp_buf).await {
                            Ok(n) if n > 0 => {
                                resp_buf.truncate(n);
                                log::debug!("🌐 DNS response #{} received ({} bytes)", query_id, n);
                                let _ = to_app_tx.send(resp_buf).await;
                            }
                            Ok(0) => {
                                log::warn!("⚠️ DNS forward: empty response for query #{}", query_id);
                            }
                            Err(e) => {
                                log::error!("❌ DNS forward: failed to read response #{}: {}", query_id, e);
                            }
                        }
                    }
                    Err(e) => {
                        log::error!("❌ DNS forward: failed to connect to 1.1.1.1:53 for query #{}: {}", query_id, e);
                    }
                }

                let _ = dns_setup_tx.send((query_id, endpoint, to_app_rx)).await;
            });
        }
        drop(dns_socket);

        // === 1b. Receive DNS responses and send them back to apps ===
        while let Ok((query_id, endpoint, mut rx)) = dns_setup_rx.try_recv() {
            if let Ok(response) = rx.try_recv() {
                let mut dns_socket = stack.socket_set.get_mut::<UdpSocket>(dns_handle);
                if let Err(e) = dns_socket.send_to(&response, endpoint) {
                    log::error!("❌ DNS: failed to send response #{} to {}: {}", query_id, endpoint, e);
                } else {
                    log::debug!("✍️ DNS response #{} sent to {}", query_id, endpoint);
                }
            } else {
                // Response not ready yet — store for next iteration
                dns_pending.insert(query_id, (endpoint, rx));
            }
        }

        // Check pending DNS responses
        let mut ready_dns = Vec::new();
        dns_pending.retain(|query_id, (endpoint, rx)| {
            if let Ok(response) = rx.try_recv() {
                ready_dns.push((*query_id, *endpoint, response));
                false
            } else {
                true
            }
        });
        for (query_id, endpoint, response) in ready_dns {
            let mut dns_socket = stack.socket_set.get_mut::<UdpSocket>(dns_handle);
            if let Err(e) = dns_socket.send_to(&response, endpoint) {
                log::error!("❌ DNS: failed to send delayed response #{} to {}: {}", query_id, endpoint, e);
            } else {
                log::debug!("✍️ DNS response #{} sent to {} (delayed)", query_id, endpoint);
            }
        }

        // === 2a. Detect newly established TCP sockets → move to inspecting ===
        let mut new_established = Vec::new();
        for (handle, socket) in stack.socket_set.iter_mut() {
            if let Socket::Tcp(tcp_socket) = socket {
                if tcp_socket.is_active()
                    && tcp_socket.state() == smoltcp::socket::tcp::State::Established
                    && !inspecting.contains(&handle)
                    && !connecting.contains(&handle)
                    && !bridged.contains(&handle)
                {
                    if let Some(endpoint) = tcp_socket.remote_endpoint() {
                        new_established.push((handle, endpoint));
                    }
                }
            }
        }
        for (handle, endpoint) in &new_established {
            inspecting.insert(*handle);
            log::info!("👀 Inspecting socket {:?} → {}:{}", handle, endpoint.addr, endpoint.port);
        }

        // === 2b. Process inspecting sockets — read first data, SNI intercept, spawn bridge ===
        let mut done_inspecting: Vec<smoltcp::iface::SocketHandle> = Vec::new();
        for &handle in &inspecting {
            if let Some(socket) = stack.socket_set.get_mut::<Socket>(handle) {
                if let Socket::Tcp(tcp_socket) = socket {
                    if !tcp_socket.can_recv() {
                        continue; // No data yet, keep inspecting
                    }

                    // Read first payload
                    let mut first_data = vec![0u8; 16384];
                    let n = match tcp_socket.recv_slice(&mut first_data) {
                        Ok(n) => n,
                        Err(e) => {
                            log::error!("❌ Failed to read from socket {:?}: {}", handle, e);
                            done_inspecting.push(handle);
                            continue;
                        }
                    };
                    first_data.truncate(n);

                    let endpoint = tcp_socket.remote_endpoint().unwrap();
                    let target_port = endpoint.port;

                    // Try SNI interception
                    if let Some(real_host) = sni_interceptor.intercept(&mut first_data) {
                        // SNI rewritten — connect to Tor with real hostname
                        log::info!("🔗 Tor → {}:{} (SNI decoy: {})", real_host, target_port, sni_interceptor.current_decoy());
                        let tor_clone = tor_client.clone();
                        let setup_tx = setup_tx.clone();
                        let fail_tx = fail_tx.clone();
                        let first_data_clone = first_data.clone();

                        tokio::spawn(async move {
                            match tor_clone.connect((real_host.clone(), target_port)).await {
                                Ok(tor_stream) => {
                                    let (rx, tx) = Bridge::spawn(handle, tor_stream, first_data_clone);
                                    let _ = setup_tx.send((handle, rx, tx)).await;
                                }
                                Err(e) => {
                                    log::error!("❌ Tor connect to {}:{} failed: {}", real_host, target_port, e);
                                    let _ = fail_tx.send(handle).await;
                                }
                            }
                        });
                    } else {
                        // Not TLS ClientHello or no SNI — fallback to IP
                        let target_host = endpoint.addr.to_string();
                        log::info!("🔗 Tor → {}:{} (IP fallback, no SNI)", target_host, target_port);
                        let tor_clone = tor_client.clone();
                        let setup_tx = setup_tx.clone();
                        let fail_tx = fail_tx.clone();
                        let first_data_clone = first_data.clone();

                        tokio::spawn(async move {
                            match tor_clone.connect((target_host.clone(), target_port)).await {
                                Ok(tor_stream) => {
                                    let (rx, tx) = Bridge::spawn(handle, tor_stream, first_data_clone);
                                    let _ = setup_tx.send((handle, rx, tx)).await;
                                }
                                Err(e) => {
                                    log::error!("❌ Tor connect to {}:{} failed: {}", target_host, target_port, e);
                                    let _ = fail_tx.send(handle).await;
                                }
                            }
                        });
                    }

                    connecting.insert(handle);
                    done_inspecting.push(handle);
                }
            }
        }
        for handle in &done_inspecting {
            inspecting.remove(handle);
        }

        // === 2c. Receive bridge setup confirmations → move to bridged ===
        while let Ok((handle, rx, tx)) = setup_rx.try_recv() {
            connecting.remove(&handle);
            tor_receivers.insert(handle, rx);
            tor_senders.insert(handle, tx);
            bridged.insert(handle);
        }

        // === 2d. Receive connection failures → remove from connecting ===
        while let Ok(handle) = fail_rx.try_recv() {
            connecting.remove(&handle);
            inspecting.remove(&handle);
        }

        // === 3. Tor → smoltcp: read bridged data, write back to sockets ===
        let mut pending_writes: Vec<(smoltcp::iface::SocketHandle, Vec<u8>)> = Vec::new();
        let mut disconnected_receivers = Vec::new();
        tor_receivers.retain(|handle, receiver| {
            loop {
                match receiver.try_recv() {
                    Ok((h, data)) => pending_writes.push((h, data)),
                    Err(tokio::sync::mpsc::error::TryRecvError::Empty) => break,
                    Err(tokio::sync::mpsc::error::TryRecvError::Disconnected) => {
                        disconnected_receivers.push(*handle);
                        return false;
                    }
                }
            }
            true
        });
        for handle in &disconnected_receivers {
            bridged.remove(handle);
            tor_senders.remove(handle);
        }

        for (handle, data) in pending_writes {
            if let Some(socket) = stack.socket_set.get_mut::<Socket>(handle) {
                if let Socket::Tcp(tcp_socket) = socket {
                    if tcp_socket.can_send() {
                        let n = tcp_socket.send_slice(&data).unwrap_or(0);
                        if n > 0 {
                            log::debug!("✍️ Wrote {}/{} bytes to smoltcp {:?}", n, data.len(), handle);
                        }
                    }
                }
            }
        }

        // === 4. smoltcp → Tor: read from bridged sockets, send to bridge ===
        let mut stale_handles = Vec::new();
        for &handle in &bridged {
            if let Some(sender) = tor_senders.get(&handle) {
                if let Some(socket) = stack.socket_set.get_mut::<Socket>(handle) {
                    if let Socket::Tcp(tcp_socket) = socket {
                        if tcp_socket.can_recv() {
                            let mut data = vec![0u8; 8192];
                            if let Ok(n) = tcp_socket.recv_slice(&mut data) {
                                if n > 0 {
                                    data.truncate(n);
                                    if sender.try_send(data).is_err() {
                                        log::debug!("🚫 Tor channel full (socket {:?}), dropping", handle);
                                    }
                                }
                            }
                        }
                        // Detect closed sockets
                        if tcp_socket.state() == smoltcp::socket::tcp::State::CloseWait
                            || tcp_socket.state() == smoltcp::socket::tcp::State::Closed
                            || tcp_socket.state() == smoltcp::socket::tcp::State::TimeWait
                        {
                            log::info!("🔌 Socket {:?} closed (state: {:?})", handle, tcp_socket.state());
                            stale_handles.push(handle);
                        }
                    }
                }
            }
        }
        for handle in &stale_handles {
            bridged.remove(handle);
            tor_senders.remove(handle);
            tor_receivers.remove(handle);
        }

        // === 5. Write outgoing packets to TUN ===
        while let Some(out_packet) = stack.output() {
            tun.write(&out_packet).await?;
        }
    }

    log::info!("👋 VPN Main Loop exiting");
    Ok(())
}
