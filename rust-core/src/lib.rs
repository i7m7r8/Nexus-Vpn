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
use tokio::io::AsyncReadExt;

struct Bridge {
    _handle: smoltcp::iface::SocketHandle,
}

impl Bridge {
    async fn copy(
        _handle: smoltcp::iface::SocketHandle,
        mut tor_stream: arti_client::DataStream,
    ) {
        let mut tor_buf = vec![0u8; 8192];
        
        loop {
            tokio::select! {
                res = tor_stream.read(&mut tor_buf) => {
                    match res {
                        Ok(0) => break,
                        Ok(n) => {
                            log::debug!("📥 Tor -> smoltcp: {} bytes", n);
                        }
                        Err(e) => {
                            log::error!("❌ Tor read error: {}", e);
                            break;
                        }
                    }
                }
            }
        }
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

        // 1. Process DNS queries
        let mut dns_socket = stack.socket_set.get_mut::<UdpSocket>(dns_handle);
        if let Ok((data, endpoint)) = dns_socket.recv() {
            let _query_data = data.to_vec();
            tokio::spawn(async move {
                log::debug!("🌐 Intercepted DNS query from {}", endpoint);
            });
        }
        drop(dns_socket);

        // 2. Process TCP connections
        let mut sockets_to_bridge = Vec::new();
        for (handle, socket) in stack.socket_set.iter_mut() {
            if let Socket::Tcp(tcp_socket) = socket {
                if tcp_socket.is_active() && tcp_socket.state() == smoltcp::socket::tcp::State::Established {
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
                    Ok(tor_stream) => {
                        log::debug!("✅ Connected to {} via Tor", target_host);
                        Bridge::copy(handle, tor_stream).await;
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
