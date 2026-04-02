//! SNI Rewriter — patches TLS ClientHello SNI hostname in-place

use crate::error::{SniError, SniResult};
use crate::parser::TlsParser;

pub struct SniRewriter {
    decoy: String,
    pub rewrites: u64,
}

impl Default for SniRewriter {
    fn default() -> Self { Self::new() }
}

impl SniRewriter {
    #[must_use]
    pub fn new() -> Self {
        Self { decoy: "www.cloudflare.com".into(), rewrites: 0 }
    }

    #[must_use]
    pub fn with_hostname(hostname: String) -> Self {
        Self { decoy: hostname, ..Default::default() }
    }

    pub fn set_hostname(&mut self, hostname: String) -> SniResult<()> {
        if hostname.is_empty() || hostname.len() > 255 {
            return Err(SniError::HostnameTooLong { length: hostname.len(), max: 255 });
        }
        self.decoy = hostname;
        Ok(())
    }

    #[must_use]
    pub fn hostname(&self) -> &str { &self.decoy }

    /// Rewrite SNI in TLS ClientHello. Returns modified buffer or original if skip needed.
    pub fn rewrite_sni(&mut self, tls_data: &[u8]) -> SniResult<Vec<u8>> {
        if tls_data.len() < 5 || tls_data[0] != 0x16 || tls_data[5] != 0x01 {
            return Ok(tls_data.to_vec()); // Not ClientHello — skip
        }

        let (sni_start, sni_len) = TlsParser::find_sni(tls_data)?;
        let new = self.decoy.as_bytes();
        let diff = new.len() as i32 - sni_len as i32;
        let new_size = tls_data.len() as i32 + diff;
        if new_size <= 0 { return Err(SniError::BufferOverflow); }

        let mut out = Vec::with_capacity(new_size as usize);
        out.extend_from_slice(&tls_data[..sni_start]);
        out.extend_from_slice(new);
        out.extend_from_slice(&tls_data[sni_start + sni_len as usize..]);

        // Patch TLS record length (bytes 3-4)
        let tls_len = (u16::from_be_bytes([out[3], out[4]]) as i32 + diff) as u16;
        out[3] = (tls_len >> 8) as u8;
        out[4] = (tls_len & 0xFF) as u8;

        // Patch handshake length (bytes 6-8)
        let hs_len = ((out[6] as u32) << 16) | ((out[7] as u32) << 8) | (out[8] as u32);
        let hs_len = (hs_len as i32 + diff) as u32;
        out[6] = (hs_len >> 16) as u8;
        out[7] = (hs_len >> 8) as u8;
        out[8] = (hs_len & 0xFF) as u8;

        if new.len() != sni_len as usize { self.rewrites += 1; }
        Ok(out)
    }
}
