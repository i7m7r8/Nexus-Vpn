//! Nexus VPN — Pure Rust core with JNI bindings for Android
//!
//! Architecture:
//!   Android TUN fd → Rust reads raw IPv4 packets
//!   → Virtual TCP stack synthesises handshakes
//!   → SNI rewriter patches TLS ClientHello
//!   → Per-flow SOCKS5 tunnel through Arti (Tor)
//!   → Response re-packaged as TCP/IP and written back to TUN
//!
//! Flow state:
//!   Idle → SynReceived (got SYN) → Established (got third ACK) → Closed (FIN/RST)

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong};
use jni::JNIEnv;
use std::cell::RefCell;
use std::collections::HashMap;
use std::net::Ipv4Addr;
use std::os::unix::io::RawFd;

mod sni;
mod tcp;
mod tun;
mod proxy;

use sni::SniRewriter;
use tcp::{Flow, FlowKey, FlowState, TcpAction, parse_tcp_packet, build_tcp_packet,
          FLAG_SYN, FLAG_ACK, FLAG_FIN, FLAG_RST, FLAG_PSH};
use tun::TunDevice;
use proxy::Tunnel;

// ---------------------------------------------------------------------------
// Global state — one instance per VPN session
// ---------------------------------------------------------------------------

struct VpnSession {
    tun: Option<TunDevice>,
    rewriter: SniRewriter,
    flows: HashMap<String, (Flow, Option<Tunnel>)>,
    running: bool,
}

thread_local! {
    static SESSION: RefCell<Option<VpnSession>> = const { RefCell::new(None) };
}

// ---------------------------------------------------------------------------
// JNI — public API consumed by NexusVpnService.kt
// ---------------------------------------------------------------------------

/// Initialise the Rust backend.
/// Called once when the user presses CONNECT in the Android UI.
///
/// # Safety
/// `tun_fd` must be a valid, open TUN file descriptor from Android
/// `VpnService.Builder.establish()`.
#[no_mangle]
pub unsafe extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_initVpnNative(
    mut env: JNIEnv,
    _class: JClass,
    tun_fd: jint,
    sni_hostname: JString,
) -> jboolean {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("NexusVpn")
            .with_min_level(log::LevelFilter::Debug),
    );

    let hostname: String = match env.get_string(&sni_hostname) {
        Ok(s) => s.into(),
        Err(e) => {
            log::error!("Failed to read SNI hostname: {e}");
            "www.cloudflare.com".into()
        }
    };

    let tun = match TunDevice::from_fd(tun_fd as RawFd, 1500) {
        Ok(t) => Some(t),
        Err(e) => {
            log::error!("Failed to open TUN device: {e}");
            return false as jboolean;
        }
    };

    SESSION.with(|cell| {
        *cell.borrow_mut() = Some(VpnSession {
            tun,
            rewriter: SniRewriter::with_hostname(hostname.clone()),
            flows: HashMap::new(),
            running: true,
        });
    });

    log::info!("✅ VPN native init complete — SNI hostname = {hostname}");
    true as jboolean
}

/// Main packet-processing loop — called from a background thread in
/// `NexusVpnService`.  Runs until `running` is set to `false` or the
/// TUN fd is closed.
///
/// This blocks the calling thread.
#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_runPacketLoopNative(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    loop {
        // --- Read session or bail out ---
        let should_continue = SESSION.with(|cell| {
            let mut guard = cell.borrow_mut();
            let session = match guard.as_mut() {
                Some(s) if s.running => s,
                _ => return false,
            };
            let tun = match session.tun.as_mut() {
                Some(t) => t,
                None => return false,
            };

            let mut buf = [0u8; 65535];

            // --- read one raw IP packet from TUN ---
            let pkt_len = match tun.read(&mut buf) {
                Ok(n) => n,
                Err(e) => {
                    log::warn!("TUN read error: {e}");
                    session.running = false;
                    return false;
                }
            };
            let pkt = &buf[..pkt_len];

            // --- parse TCP (drop UDP/ICMP/etc.) ---
            let Some((_src, src_port, _dst, dst_port, seq, ack, flags, _payload_len)) =
                parse_tcp_packet(pkt)
            else {
                return true; // not TCP — just ignore
            };

            // --- skip traffic to our own TUN address ---
            // (10.8.0.1 is the TUN gateway, we don't route it)
            
            let flow_key = format!("{}:{}", src_port, dst_port);

            // --- handle flow ---
            let entry = session.flows.entry(flow_key.clone()).or_insert_with(|| {
                let key = FlowKey { src_port, dst_port };
                log::debug!("New flow: {flow_key}");
                (Flow::new(key), None)
            });

            let flow = &mut entry.0;
            let actions = flow.handle_packet(seq, ack, flags, 0);

            // TODO: Execute TCP actions (SYN+ACK, FIN+ACK, payload forwarding)
            // For now, we establish connections. Full forwarding comes in next phase.
            for action in actions {
                match action {
                    TcpAction::SynAck { seq: s, ack: a } => {
                        log::debug!("[{flow_key}] SYN+ACK → seq={s} ack={a}");
                    }
                    TcpAction::Established => {
                        flow.tunnel_ready = true;
                        log::debug!("[{flow_key}] ESTABLISHED");
                    }
                    TcpAction::FinAck { .. } => {
                        flow.state = FlowState::Closed;
                        log::debug!("[{flow_key}] CLOSED");
                    }
                }
            }

            true
        });

        if !should_continue {
            break;
        }
    }

    log::info!("🔌 Packet loop exited");
    true as jboolean
}

/// Stop the packet loop and release the TUN fd.
#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_stopVpnNative(
    _env: JNIEnv,
    _class: JClass,
) {
    SESSION.with(|cell| {
        if let Some(mut session) = cell.borrow_mut().take() {
            session.running = false;
            log::info!("🧹 Native VPN stopped");
        }
    });
}

/// Update SNI hostname during a running session.
#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_setSniHostnameNative(
    mut env: JNIEnv,
    _class: JClass,
    hostname: JString,
) -> jboolean {
    if let Ok(s) = env.get_string(&hostname) {
        let h: String = s.into();
        SESSION.with(|cell| {
            if let Some(session) = cell.borrow_mut().as_mut() {
                let _ = session.rewriter.set_hostname(h.clone());
            }
        });
        log::info!("📝 SNI hostname updated: {h}");
        true as jboolean
    } else {
        false as jboolean
    }
}

/// Read global byte counter — bytes sent.
#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_getTrafficStatsSentNative(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    proxy::take_bytes_sent() as jlong
}

/// Read global byte counter — bytes received.
#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_getTrafficStatsRecvNative(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    proxy::take_bytes_received() as jlong
}
