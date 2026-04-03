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
            .with_tag("NexusVpn")
            .with_min_level(log::LevelFilter::Debug),
    );

    let host: String = env
        .get_string(&sni_hostname)
        .map(|h| h.into())
        .unwrap_or_else(|_| String::from("www.cloudflare.com"));

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

use smoltcp::wire::IpAddress;
use smoltcp::socket::udp::{Socket as UdpSocket, SocketBuffer as UdpSocketBuffer};
use smoltcp::socket::udp::PacketBuffer as UdpPacketBuffer;
use arti_client::{TorClient, TorClientConfig};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

struct Bridge {
    handle: smoltcp::iface::SocketHandle,
}

impl Bridge {
    async fn copy(
        handle: smoltcp::iface::SocketHandle,
        mut tor_stream: arti_client::DataStream,
        // We need a way to access the stack from this task
        // In a real implementation, we'd use a channel or a shared Arc<Mutex<NetStack>>
    ) {
        let mut tor_buf = vec![0u8; 8192];
        
        loop {
            tokio::select! {
                // Read from Tor -> Write to smoltcp
                res = tor_stream.read(&mut tor_buf) => {
                    match res {
                        Ok(0) => break,
                        Ok(n) => {
                            log::debug!("📥 Tor -> smoltcp: {} bytes", n);
                            // Push to stack's TCP socket send buffer
                        }
                        Err(e) => {
                            log::error!("❌ Tor read error: {}", e);
                            break;
                        }
                    }
                }
                // Read from smoltcp -> Write to Tor
                // (Logic to poll smoltcp socket and write to tor_stream)
            }
        }
    }
}

async fn vpn_main_loop(tun_fd: jint, _sni_host: String) -> anyhow::Result<()> {
    let tun = Arc::new(TunDevice::new(tun_fd)?);
    let mut stack = NetStack::new();
    
    // Setup UDP DNS interceptor
    let udp_rx_buffer = UdpSocketBuffer::new(vec![UdpPacketBuffer::new(vec![0; 1024]); 16], vec![0; 16384]);
    let udp_tx_buffer = UdpSocketBuffer::new(vec![UdpPacketBuffer::new(vec![0; 1024]); 16], vec![0; 16384]);
    let mut udp_socket = UdpSocket::new(udp_rx_buffer, udp_tx_buffer);
    // Bind to the virtual interface IP
    let _ = udp_socket.bind(smoltcp::wire::IpListenEndpoint::from(53));
    let dns_handle = stack.socket_set.add(udp_socket);

    log::info!("🔄 Bootstrapping Arti (Tor) v0.40.0...");
    let config = TorClientConfig::default();
    let tor_client = match TorClient::create_bootstrapped(config).await {
        Ok((client, _)) => {
            log::info!("✅ Arti bootstrapped — Tor connected!");
            client
        }
        Err(e) => {
            log::error!("❌ Arti bootstrap failed: {e}");
            return Err(e.into());
        }
    };

    log::info!("✅ TUN Device and NetStack initialized");

    let mut buf = vec![0u8; 16384];

    while RUNNING.load(Ordering::SeqCst) {
        tokio::select! {
            // Read from TUN
            res = tun.read(&mut buf) => {
                let n = res?;
                if n == 0 { break; }
                stack.input(buf[..n].to_vec());
            }
            // Wait for stack to be ready for polling or timeout
            _ = tokio::time::sleep(std::time::Duration::from_millis(2)) => {
                stack.poll();
            }
        }

        // 1. Process DNS queries
        let mut dns_socket = stack.socket_set.get_mut::<UdpSocket>(dns_handle);
        if let Ok((data, endpoint)) = dns_socket.recv() {
            let tor_clone = tor_client.clone();
            let query_data = data.to_vec();
            let mut response_socket = stack.socket_set.get_mut::<UdpSocket>(dns_handle);
            
            tokio::spawn(async move {
                // Simplified DNS parsing to extract the domain
                if query_data.len() > 12 {
                    let mut pos = 12;
                    let mut domain = String::new();
                    while pos < query_data.len() {
                        let len = query_data[pos] as usize;
                        if len == 0 { break; }
                        if pos + 1 + len > query_data.len() { break; }
                        if !domain.is_empty() { domain.push('.'); }
                        domain.push_str(&String::from_utf8_lossy(&query_data[pos+1..pos+1+len]));
                        pos += 1 + len;
                    }

                    if !domain.is_empty() {
                        log::info!("🌐 Resolving via Tor: {}", domain);
                        match tor_clone.resolve(&domain).await {
                            Ok(ips) => {
                                if let Some(ip) = ips.first() {
                                    log::debug!("✅ Resolved {} -> {}", domain, ip);
                                    // In a full implementation, we'd craft a DNS response packet
                                    // and send it back via response_socket.
                                }
                            }
                            Err(e) => log::error!("❌ DNS resolution failed for {}: {}", domain, e),
                        }
                    }
                }
            });
        }
        drop(dns_socket);

        // 2. Process TCP connections
        let mut sockets_to_bridge = Vec::new();
        for (handle, socket) in stack.socket_set.iter_mut() {
            if let Some(tcp_socket) = smoltcp::socket::tcp::Socket::downcast_mut(socket) {
                if tcp_socket.is_active() && tcp_socket.state() == smoltcp::socket::tcp::State::Established {
                    // This is an established virtual connection that needs bridging to Tor
                    if let Some(endpoint) = tcp_socket.remote_endpoint() {
                        sockets_to_bridge.push((handle, endpoint));
                    }
                }
            }
        }

        for (handle, endpoint) in sockets_to_bridge {
            let tor_clone = tor_client.clone();
            let target_host = endpoint.addr.to_string();
            let target_port = endpoint.port;
            
            log::info!("🔗 Bridging TCP connection to Tor: {}:{}", target_host, target_port);
            
            tokio::spawn(async move {
                match tor_clone.connect((target_host.clone(), target_port)).await {
                    Ok(mut tor_stream) => {
                        log::debug!("✅ Connected to {} via Tor", target_host);
                        // Bi-directional copy logic would go here
                        // Mapping smoltcp socket read/write to tor_stream
                    }
                    Err(e) => log::error!("❌ Failed to connect to {} via Tor: {}", target_host, e),
                }
            });
        }
        while let Some(out_packet) = stack.output() {
            tun.write(&out_packet).await?;
        }
    }

    log::info!("👋 VPN Main Loop exiting");
    Ok(())
}
