//! TLS ClientHello parser — locates and extracts SNI extension

use crate::error::{SniError, SniResult};

pub struct TlsParser;

impl TlsParser {
    /// Parse TLS record header: returns (content_type, version, length)
    #[must_use]
    pub fn parse_record_header(data: &[u8]) -> SniResult<(u8, u16, u16)> {
        if data.len() < 5 {
            return Err(SniError::InvalidTlsRecord("Too short".into()));
        }
        Ok((data[0], u16::from_be_bytes([data[1], data[2]]), u16::from_be_bytes([data[3], data[4]])))
    }

    /// Parse TLS handshake header: returns (handshake_type, length)
    #[must_use]
    pub fn parse_handshake_header(data: &[u8]) -> SniResult<(u8, u32)> {
        if data.len() < 4 {
            return Err(SniError::InvalidTlsRecord("Too short for handshake".into()));
        }
        let hs_type = data[0];
        let hs_len = ((data[1] as u32) << 16) | ((data[2] as u32) << 8) | (data[3] as u32);
        Ok((hs_type, hs_len))
    }

    /// Find SNI extension data inside a TLS ClientHello.
    /// Returns: `(sni_hostname_start_offset, sni_hostname_length, extensions_total_length)`
    #[must_use]
    pub fn find_sni(data: &[u8]) -> SniResult<(usize, u16)> {
        // Validate TLS 1.x record
        if data[0] != 0x16 {
            return Err(SniError::InvalidTlsRecord(format!("Not handshake: 0x{:02x}", data[0])));
        }
        if data.len() < 44 {
            return Err(SniError::InvalidTlsRecord("Too short for ClientHello".into()));
        }
        if data[5] != 0x01 {
            return Err(SniError::InvalidHandshakeType(data[5]));
        }

        let mut pos = 43; // skip record(5) + handshake_type(1) + handshake_length(3) + version(2) + random(32)
        let session_len = data[pos] as usize;
        pos += 1 + session_len;

        if pos + 2 > data.len() { return Err(SniError::InvalidTlsRecord("No cipher length".into())); }
        let cipher_len = u16::from_be_bytes([data[pos], data[pos + 1]]) as usize;
        pos += 2 + cipher_len;

        if pos >= data.len() { return Err(SniError::InvalidTlsRecord("No comp length".into())); }
        let comp_len = data[pos] as usize;
        pos += 1 + comp_len;

        if pos + 2 > data.len() { return Err(SniError::InvalidTlsRecord("No ext length".into())); }
        let ext_total = u16::from_be_bytes([data[pos], data[pos + 1]]) as usize;
        pos += 2;

        let ext_end = pos + ext_total;
        if ext_end > data.len() { return Err(SniError::InvalidTlsRecord("Extensions overflow".into())); }

        while pos + 4 <= ext_end {
            let ext_type = u16::from_be_bytes([data[pos], data[pos + 1]]);
            let ext_len = u16::from_be_bytes([data[pos + 2], data[pos + 3]]) as usize;
            pos += 4;
            if pos + ext_len > ext_end { break; }
            if ext_type == 0x0000 {
                // SNI: +2 (name_type + name_len) to reach hostname bytes
                return Ok((pos + 3, u16::from_be_bytes([data[pos + 2], data[pos + 3]])));
            }
            pos += ext_len;
        }
        Err(SniError::SniNotFound)
    }
}
