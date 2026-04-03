use crate::sni::error::SniError;

pub struct TlsParser;

impl TlsParser {
    pub fn parse_sni(buf: &[u8]) -> Result<String, SniError> {
        if buf.len() < 5 { return Err(SniError::BufferTooSmall); }

        // Check if it's a Handshake (0x16)
        if buf[0] != 0x16 { return Err(SniError::ParseError); }

        // Check TLS version (at least TLS 1.0)
        if buf[1] < 3 { return Err(SniError::ParseError); }

        let mut pos = 5; // Skip Handshake header (Type, Version, Length)
        if buf.len() < pos + 4 { return Err(SniError::BufferTooSmall); }

        // Check Handshake Type (ClientHello = 0x01)
        if buf[pos] != 0x01 { return Err(SniError::ParseError); }
        
        let length = ((buf[pos+1] as usize) << 16) | ((buf[pos+2] as usize) << 8) | (buf[pos+3] as usize);
        if buf.len() < pos + 4 + length { return Err(SniError::BufferTooSmall); }

        pos += 4; // Skip ClientHello header

        // Version (2 bytes)
        pos += 2;
        // Random (32 bytes)
        pos += 32;

        // Session ID
        if buf.len() < pos + 1 { return Err(SniError::BufferTooSmall); }
        let session_id_len = buf[pos] as usize;
        pos += 1 + session_id_len;

        // Cipher Suites
        if buf.len() < pos + 2 { return Err(SniError::BufferTooSmall); }
        let cipher_suites_len = ((buf[pos] as usize) << 8) | (buf[pos+1] as usize);
        pos += 2 + cipher_suites_len;

        // Compression Methods
        if buf.len() < pos + 1 { return Err(SniError::BufferTooSmall); }
        let compression_methods_len = buf[pos] as usize;
        pos += 1 + compression_methods_len;

        // Extensions
        if buf.len() < pos + 2 { return Err(SniError::BufferTooSmall); }
        let extensions_len = ((buf[pos] as usize) << 8) | (buf[pos+1] as usize);
        pos += 2;

        let extensions_end = pos + extensions_len;
        if buf.len() < extensions_end { return Err(SniError::BufferTooSmall); }

        while pos + 4 <= extensions_end {
            let ext_type = ((buf[pos] as u16) << 8) | (buf[pos+1] as u16);
            let ext_len = ((buf[pos+2] as usize) << 8) | (buf[pos+3] as usize);
            pos += 4;

            if ext_type == 0x0000 { // Server Name Extension
                if pos + ext_len > extensions_end { return Err(SniError::ParseError); }
                
                // Server Name List Length (2 bytes)
                // Server Name Type (1 byte) - Host Name is 0
                // Host Name Length (2 bytes)
                if ext_len < 5 { return Err(SniError::ParseError); }
                let host_name_len = ((buf[pos+3] as usize) << 8) | (buf[pos+4] as usize);
                if pos + 5 + host_name_len > extensions_end { return Err(SniError::ParseError); }
                
                let host_name = &buf[pos+5..pos+5+host_name_len];
                return String::from_utf8(host_name.to_vec()).map_err(|_| SniError::ParseError);
            }
            pos += ext_len;
        }

        Err(SniError::NoSniFound)
    }
}
