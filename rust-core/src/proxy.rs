//! Per-flow Arti (Tor) forwarder
//!
//! For each TCP flow opened by the virtual TCP stack, this module opens a
//! SOCKS5 connection through Arti and forwards data in both directions.

use anyhow::{Result, bail, Context as _};
use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::atomic::{AtomicU64, Ordering};

static BYTES_SENT: AtomicU64 = AtomicU64::new(0);
static BYTES_RECV: AtomicU64 = AtomicU64::new(0);

/// One active tunnel to Arti/Tor (SOCKS5 on 127.0.0.1:9050).
pub struct Tunnel {
    stream: TcpStream,
}

impl Tunnel {
    /// Connect to the local Arti SOCKS5 port and issue a CONNECT to
    /// `target:port`.  Returns a ready `Tunnel`.
    pub fn connect(target: &str, port: u16) -> Result<Self> {
        // Connect to Arti SOCKS5 listener (localhost:9050)
        let mut stream = TcpStream::connect("127.0.0.1:9050")
            .with_context(|| "Failed to connect to Arti SOCKS5 port")?;
        stream.set_nodelay(true)?;
        stream.set_read_timeout(Some(std::time::Duration::from_secs(30)))?;
        stream.set_write_timeout(Some(std::time::Duration::from_secs(30)))?;

        // SOCKS5 greeting — no auth
        stream.write_all(&[0x05, 0x01, 0x00])?;
        let mut resp = [0u8; 2];
        stream.read_exact(&mut resp)?;
        if resp[0] != 0x05 || resp[1] != 0x00 {
            bail!("SOCKS5 greeting failed: ver={}, method={}", resp[0], resp[1]);
        }

        // SOCKS5 CONNECT
        let mut req = vec![0x05, 0x01, 0x00]; // VER=5, CMD=CONNECT, RSV=0
        // ATYP + destination
        if let Ok(ip) = target.parse::<std::net::Ipv4Addr>() {
            req.push(0x01); // IPv4
            req.extend_from_slice(&ip.octets());
        } else {
            req.push(0x03); // domain
            req.push(target.len() as u8);
            req.extend_from_slice(target.as_bytes());
        }
        req.push((port >> 8) as u8);
        req.push((port & 0xFF) as u8);
        stream.write_all(&req)?;

        // Read response: VER(1) REP(1) RSV(1) ATYP(1)
        let mut hdr = [0u8; 4];
        stream.read_exact(&mut hdr)?;
        if hdr[0] != 0x05 { bail!("Bad SOCKS5 response version"); }
        if hdr[1] != 0x00 { bail!("SOCKS5 connect refused: reply={}", hdr[1]); }

        // Skip BND.ADDR + BND.PORT
        let bnd_len = match hdr[3] {
            0x01 => 4 + 2,        // IPv4 + port
            0x04 => 16 + 2,       // IPv6 + port
            0x03 => {             // domain
                let mut d = [0u8; 1];
                stream.read_exact(&mut d)?;
                d[0] as usize + 2
            }
            _ => bail!("Unknown ATYP in SOCKS5 response"),
        };
        let mut skip = vec![0u8; bnd_len];
        let _ = stream.read_exact(&mut skip); // best-effort

        Ok(Self { stream })
    }

    #[inline]
    pub fn write(&mut self, buf: &[u8]) -> Result<usize> {
        let n = self.stream.write(buf)?;
        BYTES_SENT.fetch_add(n as u64, Ordering::Relaxed);
        Ok(n)
    }

    #[inline]
    pub fn read(&mut self, buf: &mut [u8]) -> Result<usize> {
        let n = self.stream.read(buf)?;
        BYTES_RECV.fetch_add(n as u64, Ordering::Relaxed);
        Ok(n)
    }

    pub fn shutdown(&mut self) {
        let _ = self.stream.shutdown(std::net::Shutdown::Both);
    }
}

/// Global stats consumed by the Kotlin UI via JNI
pub fn take_bytes_sent() -> u64   { BYTES_SENT.swap(0, Ordering::Relaxed) }
pub fn take_bytes_received() -> u64 { BYTES_RECV.swap(0, Ordering::Relaxed) }
