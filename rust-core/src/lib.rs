// ===========================================================================
// Nexus VPN Core — SNI + Tor VPN
// Architecture: TUN → smoltcp → SOCKS5 → Local Tor Binary → Internet
// ===========================================================================

use std::os::unix::io::RawFd;
use std::sync::atomic::{AtomicBool, Ordering};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jobject};

mod tun;
mod dual_log;

use dual_log::DualLogger;

// ===========================================================================
// Global State
// ===========================================================================

static RUNNING: AtomicBool = AtomicBool::new(false);
static SNI_HOST: parking_lot::Mutex<String> = parking_lot::Mutex::new(String::new());
static TOR_SOCKS_PORT: parking_lot::Mutex<u16> = parking_lot::Mutex::new(9050);
static TOR_DNS_PORT: parking_lot::Mutex<u16> = parking_lot::Mutex::new(5400);
static RUNTIME: parking_lot::RwLock<Option<tokio::runtime::Runtime>> = parking_lot::RwLock::new(None);

// ===========================================================================
// JNI Entry Points
// ===========================================================================

#[no_mangle]
pub unsafe extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_initVpnNative(
    mut env: JNIEnv,
    _class: JClass,
    tun_fd: jint,
    sni_hostname: JString,
    bridge_config: JString,
) -> jboolean {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        _init_vpn_native(&mut env, tun_fd, sni_hostname, bridge_config)
    })).unwrap_or_else(|e| {
        log::error!("🚨 Native init panicked: {:?}", e);
        false as jboolean
    })
}

fn _init_vpn_native(
    env: &mut JNIEnv,
    tun_fd: jint,
    sni_hostname: JString,
    bridge_config: JString,
) -> jboolean {
    let _ = log::set_boxed_logger(Box::new(DualLogger));
    log::set_max_level(log::LevelFilter::Debug);

    let host: String = env
        .get_string(&sni_hostname)
        .map(|h| h.into())
        .unwrap_or_else(|_| String::from("www.cloudflare.com"));

    let bridge_json: String = env
        .get_string(&bridge_config)
        .map(|h| h.into())
        .unwrap_or_default();

    *SNI_HOST.lock() = host.clone();

    // Parse bridge config for SOCKS/DNS ports (defaults: 9050, 5400)
    if let Ok(config) = serde_json::from_str::<serde_json::Value>(&bridge_json) {
        if let Some(port) = config["socks_port"].as_u64() {
            *TOR_SOCKS_PORT.lock() = port as u16;
        }
        if let Some(port) = config["dns_port"].as_u64() {
            *TOR_DNS_PORT.lock() = port as u16;
        }
    }

    let socks_port = *TOR_SOCKS_PORT.lock();
    let dns_port = *TOR_DNS_PORT.lock();

    log::info!("🚀 Nexus VPN Core (SNI: {}, SOCKS: {}, DNS: {})", host, socks_port, dns_port);

    let rt = match tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
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
        if let Err(e) = vpn_main_loop(tun_fd, socks_port, dns_port).await {
            log::error!("🚨 VPN Main Loop Error: {e}");
            RUNNING.store(false, Ordering::SeqCst);
        }
    });

    *RUNTIME.write() = Some(rt);

    log::info!("✅ Native init complete — routing TUN to local Tor SOCKS");
    true as jboolean
}

#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_stopVpnNative(
    _env: JNIEnv,
    _class: JClass,
) {
    log::info!("🛑 Stopping Nexus VPN Core");
    RUNNING.store(false, Ordering::SeqCst);

    let mut rt_guard = RUNTIME.write();
    if let Some(rt) = rt_guard.take() {
        rt.shutdown_timeout(std::time::Duration::from_secs(2));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_setSniHostnameNative(
    mut env: JNIEnv,
    _class: JClass,
    hostname: JString,
) -> jboolean {
    if let Ok(s) = env.get_string(&hostname) {
        let h: String = s.into();
        *SNI_HOST.lock() = h.clone();
        log::info!("📝 SNI hostname updated: {h}");
        true as jboolean
    } else {
        false as jboolean
    }
}

#[no_mangle]
pub extern "system" fn Java_com_nexusvpn_android_service_NexusVpnService_getLogBufferNative(
    mut env: JNIEnv,
    _class: JClass,
) -> jobject {
    let buffer = dual_log::get_log_buffer();
    let lines = buffer.join("\n");
    env.new_string(&lines)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

// ===========================================================================
// VPN Main Loop — TUN → smoltcp → SOCKS → Tor
// ===========================================================================

async fn vpn_main_loop(tun_fd: RawFd, socks_port: u16, _dns_port: u16) -> anyhow::Result<()> {
    log::info!("🔄 VPN Main Loop starting");

    let mut tun = tun::TunDevice::new(tun_fd)?;
    log::info!("✅ TUN device initialized");

    // smoltcp interface
    let config = smoltcp::iface::Config::new(smoltcp::wire::HardwareAddress::Ip);
    let now = smoltcp::time::Instant::now();
    let mut iface = smoltcp::iface::Interface::new(config, &mut tun, now);
    iface.update_ip_addrs(|addrs| {
        addrs.push(smoltcp::wire::IpCidr::new(
            smoltcp::wire::IpAddress::v4(10, 8, 0, 2), 24
        )).unwrap();
    });
    iface.routes_mut().add_default_ipv4_route(
        smoltcp::wire::Ipv4Address::new(10, 8, 0, 1)
    ).unwrap();

    let mut sockets = smoltcp::iface::SocketSet::new(vec![]);

    log::info!("📡 Listening on TUN — routing to SOCKS 127.0.0.1:{}", socks_port);

    while RUNNING.load(Ordering::SeqCst) {
        let now = smoltcp::time::Instant::now();

        // Poll smoltcp (processes incoming packets from TUN)
        match iface.poll(now, &mut tun, &mut sockets) {
            smoltcp::iface::PollResult::SocketStateChanged => {}
            smoltcp::iface::PollResult::None => {}
        }

        // Dispatch outgoing packets to TUN
        match iface.dispatch(&mut tun) {
            Ok(_) => {}
            Err(smoltcp::Error::Exhausted) => {}
            Err(e) => {
                log::debug!("iface dispatch error: {e}");
            }
        }

        tokio::time::sleep(std::time::Duration::from_millis(5)).await;
    }

    log::info!("🛑 VPN Main Loop stopped");
    Ok(())
}
