use crate::sni::error::SniError;

pub struct TlsParser;

impl TlsParser {
    pub fn parse_sni(buf: &[u8]) -> Result<String, SniError> {
        if buf.len() < 5 { return Err(SniError::BufferTooSmall); }

        // Check if it's a Handshake (0x16)
        if buf[0] != 0x16 { return Err(SniError::ParseError); }

        // Check TLS version (at least TLS 1.0)
        if buf[1] < 3 { return Err(SniError::ParseError); }

        let mut pos = 5; // Skip TLS record header (Type=1, Version=2, Length=2)

        // Check Handshake Type (ClientHello = 0x01)
        if buf.len() < pos + 4 { return Err(SniError::BufferTooSmall); }
        if buf[pos] != 0x01 { return Err(SniError::ParseError); }

        let handshake_len = ((buf[pos+1] as usize) << 16) | ((buf[pos+2] as usize) << 8) | (buf[pos+3] as usize);
        pos = pos.checked_add(4).ok_or(SniError::BufferTooSmall)?;
        let handshake_end = pos.checked_add(handshake_len).ok_or(SniError::BufferTooSmall)?;
        if buf.len() < handshake_end { return Err(SniError::BufferTooSmall); }

        // Version (2 bytes) + Random (32 bytes)
        pos = pos.checked_add(34).ok_or(SniError::BufferTooSmall)?;

        // Session ID (length-prefixed)
        if buf.len() < pos + 1 { return Err(SniError::BufferTooSmall); }
        let session_id_len = buf[pos] as usize;
        pos = pos.checked_add(1 + session_id_len).ok_or(SniError::BufferTooSmall)?;
        if buf.len() < pos { return Err(SniError::BufferTooSmall); }

        // Cipher Suites (length-prefixed)
        if buf.len() < pos + 2 { return Err(SniError::BufferTooSmall); }
        let cipher_suites_len = ((buf[pos] as usize) << 8) | (buf[pos+1] as usize);
        pos = pos.checked_add(2 + cipher_suites_len).ok_or(SniError::BufferTooSmall)?;
        if buf.len() < pos { return Err(SniError::BufferTooSmall); }

        // Compression Methods (length-prefixed)
        if buf.len() < pos + 1 { return Err(SniError::BufferTooSmall); }
        let compression_methods_len = buf[pos] as usize;
        pos = pos.checked_add(1 + compression_methods_len).ok_or(SniError::BufferTooSmall)?;
        if buf.len() < pos { return Err(SniError::BufferTooSmall); }

        // Extensions (length-prefixed)
        if buf.len() < pos + 2 { return Err(SniError::BufferTooSmall); }
        let extensions_len = ((buf[pos] as usize) << 8) | (buf[pos+1] as usize);
        pos = pos.checked_add(2).ok_or(SniError::BufferTooSmall)?;

        let extensions_end = pos.checked_add(extensions_len).ok_or(SniError::BufferTooSmall)?;
        if buf.len() < extensions_end { return Err(SniError::BufferTooSmall); }

        // Iterate extensions looking for SNI (type 0x0000)
        while pos + 4 <= extensions_end {
            let ext_type = ((buf[pos] as u16) << 8) | (buf[pos+1] as u16);
            let ext_len = ((buf[pos+2] as usize) << 8) | (buf[pos+3] as usize);
            pos = pos.checked_add(4).ok_or(SniError::BufferTooSmall)?;

            if ext_type == 0x0000 { // Server Name Indication
                if pos + ext_len > extensions_end { return Err(SniError::ParseError); }
                // SNI extension structure:
                //   Server Name List Length (2 bytes)
                //   Server Name Type (1 byte) — Host Name = 0
                //   Host Name Length (2 bytes)
                //   Host Name (variable)
                if ext_len < 5 { return Err(SniError::ParseError); }
                let host_name_len = ((buf[pos+3] as usize) << 8) | (buf[pos+4] as usize);
                if pos + 5 + host_name_len > extensions_end { return Err(SniError::ParseError); }

                let host_name = &buf[pos+5..pos+5+host_name_len];
                return String::from_utf8(host_name.to_vec()).map_err(|_| SniError::ParseError);
            }
            pos = pos.checked_add(ext_len).ok_or(SniError::BufferTooSmall)?;
        }

        Err(SniError::NoSniFound)
    }
}
