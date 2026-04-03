//! Nexus VPN — Pure Rust Core (JNI bindings for Android)
//!
//! Pure Rust Tor vpn using Arti v0.40.0.
//! TUN interface reads raw IPv4 packets → virtual TCP stack → SNI rewriting → Arti.

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

// ===========================================================================
// Global state
// ===========================================================================

static RUNNING: AtomicBool = AtomicBool::new(false);
static RUNTIME: parking_lot::RwLock<Option<Runtime>> = parking_lot::RwLock::new(None);
static SNI_HOST: parking_lot::RwLock<String> = parking_lot::RwLock::new(String::new());

// ===========================================================================
// JNI — exposed to Kotlin
// ===========================================================================

#[no_mangle]
pub unsafe extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_initVpnNative(
    mut env: JNIEnv,
    _class: jni::objects::JClass,
    tun_fd: jint,
    sni_hostname: JString,
) -> jboolean {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("NexusVpn"),
    );

    let host: String = env
        .get_string(&sni_hostname)
        .map(|h| h.into())
        .unwrap_or_else(|_| String::from("www.cloudflare.com"));

    *SNI_HOST.write() = host.clone();
    log::info!("🚀 Initializing Nexus VPN Core (SNI: {})", host);

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
        if let Err(e) = vpn_main_loop(tun_fd, host).await {
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
    
    // Drop runtime to stop all tasks
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
        log::info!("📝 SNI hostname updated: {h}");
        true as jboolean
    } else {
        false as jboolean
    }
}

use smoltcp::socket::udp::{Socket as UdpSocket, PacketBuffer as UdpPacketBuffer, PacketMetadata as UdpPacketMetadata};
use smoltcp::socket::Socket;
use arti_client::{TorClient, TorClientConfig};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use std::collections::{HashMap, HashSet};
use tokio::sync::mpsc;

struct Bridge;

impl Bridge {
    /// Spawn a bidirectional bridge between a Tor stream and smoltcp socket.
    /// Returns (receiver for Tor→smoltcp data, sender for smoltcp→Tor data).
    fn spawn(
        handle: smoltcp::iface::SocketHandle,
        tor_stream: arti_client::DataStream,
    ) -> (
        mpsc::Receiver<(smoltcp::iface::SocketHandle, Vec<u8>)>,
        mpsc::Sender<Vec<u8>>,
    ) {
        let (to_smoltcp_tx, to_smoltcp_rx) = mpsc::channel::<(smoltcp::iface::SocketHandle, Vec<u8>)>(16);
        let (to_tor_tx, mut to_tor_rx) = mpsc::channel::<Vec<u8>>(16);

        tokio::spawn(async move {
            let mut tor_buf = vec![0u8; 8192];

            loop {
                tokio::select! {
                    // Tor → smoltcp
                    res = tor_stream.read(&mut tor_buf) => {
                        match res {
                            Ok(0) => {
                                log::debug!("🔌 Tor stream EOF for socket {:?}", handle);
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
                                log::debug!("🔌 smoltcp channel closed for socket {:?}", handle);
                                break;
                            }
                        }
                    }
                }
            }
            log::debug!("🚪 Bridge task exited for socket {:?}", handle);
        });

        (to_smoltcp_rx, to_tor_tx)
    }
}

use tor_rtcompat::PreferredRuntime;
use crate::sni::SniRuntime;

async fn vpn_main_loop(tun_fd: jint, sni_host: String) -> anyhow::Result<()> {
    let tun = Arc::new(TunDevice::new(tun_fd)?);
    let mut stack = NetStack::new();
    
    // Setup UDP DNS interceptor
    let udp_rx_buffer = UdpPacketBuffer::new(vec![UdpPacketMetadata::EMPTY; 16], vec![0; 16384]);
    let udp_tx_buffer = UdpPacketBuffer::new(vec![UdpPacketMetadata::EMPTY; 16], vec![0; 16384]);
    let mut udp_socket = UdpSocket::new(udp_rx_buffer, udp_tx_buffer);
    let _ = udp_socket.bind(smoltcp::wire::IpListenEndpoint::from(53));
    let dns_handle = stack.socket_set.add(udp_socket);

    log::info!("🔄 Bootstrapping Arti (Tor) v0.40.0 with SNI Transport: {}...", sni_host);
    
    let runtime = PreferredRuntime::current()?;
    let sni_runtime = SniRuntime::new(runtime.clone(), sni_host.clone());
    let config = TorClientConfig::default();
    
    let tor_client = match TorClient::with_runtime(sni_runtime)
        .config(config)
        .create_bootstrapped().await 
    {
        Ok(client) => {
            log::info!("✅ Arti bootstrapped — Tor connected via SNI!");
            client
        }
        Err(e) => {
            log::error!("❌ Arti bootstrap failed: {}", e);
            return Err(anyhow::anyhow!("Tor bootstrap failed: {}", e));
        }
    };

    log::info!("✅ TUN Device and NetStack initialized");

    let mut buf = vec![0u8; 16384];

    // Track which sockets have been bridged (prevent infinite duplicates)
    let mut bridged: HashSet<smoltcp::iface::SocketHandle> = HashSet::new();
    // Track sockets with in-flight Tor connection attempts (prevent duplicate spawns)
    let mut connecting: HashSet<smoltcp::iface::SocketHandle> = HashSet::new();
    // Receivers for Tor → smoltcp data (one per bridged socket)
    let mut tor_receivers: HashMap<smoltcp::iface::SocketHandle, mpsc::Receiver<(smoltcp::iface::SocketHandle, Vec<u8>)>> = HashMap::new();
    // Senders for smoltcp → Tor data (one per bridged socket)
    let mut tor_senders: HashMap<smoltcp::iface::SocketHandle, mpsc::Sender<Vec<u8>>> = HashMap::new();
    // Channel for bridge tasks to return their channel endpoints to the main loop
    let (setup_tx, setup_rx) = mpsc::channel::<(
        smoltcp::iface::SocketHandle,
        mpsc::Receiver<(smoltcp::iface::SocketHandle, Vec<u8>)>,
        mpsc::Sender<Vec<u8>>,
    )>(32);
    // Channel for bridge tasks to report connection failures (so we can retry)
    let (fail_tx, fail_rx) = mpsc::channel::<smoltcp::iface::SocketHandle>(32);

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

        // 1. Process DNS queries (stub — log only for now)
        let mut dns_socket = stack.socket_set.get_mut::<UdpSocket>(dns_handle);
        if let Ok((data, endpoint)) = dns_socket.recv() {
            let _query_data = data.to_vec();
            log::debug!("🌐 Intercepted DNS query from {}", endpoint);
        }
        drop(dns_socket);

        // 2. Process TCP connections — bridge new established sockets
        let mut new_connections = Vec::new();
        for (handle, socket) in stack.socket_set.iter_mut() {
            if let Socket::Tcp(tcp_socket) = socket {
                if tcp_socket.is_active()
                    && tcp_socket.state() == smoltcp::socket::tcp::State::Established
                    && !bridged.contains(&handle)
                    && !connecting.contains(&handle)
                {
                    if let Some(endpoint) = tcp_socket.remote_endpoint() {
                        new_connections.push((handle, endpoint));
                    }
                }
            }
        }

        for (handle, endpoint) in new_connections {
            connecting.insert(handle);
            let tor_clone = tor_client.clone();
            let setup_tx = setup_tx.clone();
            let fail_tx = fail_tx.clone();
            let target_host = endpoint.addr.to_string();
            let target_port = endpoint.port;

            log::info!("🔗 Bridging TCP connection to Tor: {}:{}", target_host, target_port);

            tokio::spawn(async move {
                match tor_clone.connect((target_host.clone(), target_port)).await {
                    Ok(tor_stream) => {
                        log::info!("✅ Connected to {} via Tor (socket {:?})", target_host, handle);
                        let (rx, tx) = Bridge::spawn(handle, tor_stream);
                        let _ = setup_tx.send((handle, rx, tx)).await;
                    }
                    Err(e) => {
                        log::error!("❌ Failed to connect to {} via Tor: {}", target_host, e);
                        let _ = fail_tx.send(handle).await;
                    }
                }
            });
        }

        // 2b. Receive channel setup confirmations from bridge tasks
        while let Ok((handle, rx, tx)) = setup_rx.try_recv() {
            connecting.remove(&handle);
            tor_receivers.insert(handle, rx);
            tor_senders.insert(handle, tx);
            bridged.insert(handle);
        }

        // 2c. Receive connection failures — remove from connecting so they can retry
        while let Ok(handle) = fail_rx.try_recv() {
            connecting.remove(&handle);
        }

        // 3. Read Tor → smoltcp data and write it back to the socket
        //    Collect data first to avoid borrow conflicts on socket_set
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
        // Clean up handles whose bridge tasks have exited
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
                            log::debug!("✍️ Wrote {}/{} bytes to smoltcp socket {:?}", n, data.len(), handle);
                        }
                    }
                }
            }
        }

        // 4. Read smoltcp → Tor data from bridged sockets and send to bridge
        let mut stale_handles = Vec::new();
        for &handle in bridged.iter() {
            if let Some(sender) = tor_senders.get(&handle) {
                if let Some(socket) = stack.socket_set.get_mut::<Socket>(handle) {
                    if let Socket::Tcp(tcp_socket) = socket {
                        if tcp_socket.can_recv() {
                            let mut data = vec![0u8; 8192];
                            if let Ok(n) = tcp_socket.recv_slice(&mut data) {
                                if n > 0 {
                                    data.truncate(n);
                                    if sender.try_send(data).is_err() {
                                        log::debug!("🚫 Tor channel full for socket {:?}, dropping", handle);
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
        // Clean up stale entries outside the iteration
        for handle in &stale_handles {
            bridged.remove(handle);
            tor_senders.remove(handle);
            tor_receivers.remove(handle);
        }

        // 5. Write outgoing packets to TUN
        while let Some(out_packet) = stack.output() {
            tun.write(&out_packet).await?;
        }
    }

    log::info!("👋 VPN Main Loop exiting");
    Ok(())
}
