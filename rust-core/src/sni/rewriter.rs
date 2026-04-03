use crate::sni::error::SniError;

pub struct SniRewriter;

impl SniRewriter {
    /// Replaces the SNI in a TLS ClientHello buffer with a new one.
    /// If no SNI extension exists, injects one into the extensions list.
    pub fn rewrite_sni(buf: &mut Vec<u8>, new_sni: &str) -> Result<(), SniError> {
        if buf.len() < 5 { return Err(SniError::BufferTooSmall); }
        if buf[0] != 0x16 { return Err(SniError::ParseError); } // Handshake only

        let mut pos = 5; // Skip Handshake header
        if buf.len() < pos + 4 { return Err(SniError::BufferTooSmall); }
        if buf[pos] != 0x01 { return Err(SniError::ParseError); } // ClientHello only

        pos += 4; // Skip ClientHello header
        pos += 2; // Version
        pos += 32; // Random

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
        let extensions_start = pos;
        let extensions_len = ((buf[pos] as usize) << 8) | (buf[pos+1] as usize);
        pos += 2;

        let extensions_end = pos + extensions_len;
        if buf.len() < extensions_end { return Err(SniError::BufferTooSmall); }

        // Search for existing SNI extension
        let mut search_pos = pos;
        while search_pos + 4 <= extensions_end {
            let ext_type = ((buf[search_pos] as u16) << 8) | (buf[search_pos+1] as u16);
            let ext_len = ((buf[search_pos+2] as usize) << 8) | (buf[search_pos+3] as usize);

            if ext_type == 0x0000 { // Server Name Extension
                let sni_ext_start = search_pos;
                let sni_ext_total_len = 4 + ext_len;

                // Construct new SNI extension
                let new_ext = Self::build_sni_extension(new_sni);

                // Replace in original buffer
                buf.splice(sni_ext_start..sni_ext_start + sni_ext_total_len, new_ext.clone());

                // Adjust lengths
                Self::adjust_lengths(buf, extensions_start, sni_ext_total_len, new_ext.len());

                return Ok(());
            }
            search_pos += 4 + ext_len;
        }

        // No SNI extension found — inject one at the end of extensions
        Self::inject_sni_extension(buf, extensions_start, extensions_len, new_sni)
    }

    /// Build a new SNI extension payload (without type/length prefix)
    fn build_sni_extension(hostname: &str) -> Vec<u8> {
        let host_name_bytes = hostname.as_bytes();
        let host_name_len = host_name_bytes.len();
        let server_name_list_len = host_name_len + 3;
        let ext_data_len = server_name_list_len + 2;

        let mut ext = Vec::with_capacity(4 + ext_data_len);
        ext.extend_from_slice(&[0x00, 0x00]); // Extension Type: server_name
        ext.extend_from_slice(&[(ext_data_len >> 8) as u8, (ext_data_len & 0xFF) as u8]);
        ext.extend_from_slice(&[(server_name_list_len >> 8) as u8, (server_name_list_len & 0xFF) as u8]);
        ext.push(0x00); // Name Type: host_name
        ext.extend_from_slice(&[(host_name_len >> 8) as u8, (host_name_len & 0xFF) as u8]);
        ext.extend_from_slice(host_name_bytes);
        ext
    }

    /// Adjust TLS record, handshake, and extensions lengths after SNI replacement
    fn adjust_lengths(buf: &mut Vec<u8>, extensions_start: usize, old_ext_len: usize, new_ext_len: usize) {
        let delta = new_ext_len as isize - old_ext_len as isize;

        // Adjust Extensions Length
        let new_extensions_len = ((buf[extensions_start] as usize) << 8) | (buf[extensions_start + 1] as usize);
        let new_extensions_len = (new_extensions_len as isize + delta) as usize;
        buf[extensions_start] = (new_extensions_len >> 8) as u8;
        buf[extensions_start + 1] = (new_extensions_len & 0xFF) as u8;

        // Adjust Handshake Length (3 bytes at offset 6)
        let handshake_len_pos = 5 + 1;
        let old_handshake_len = ((buf[handshake_len_pos] as usize) << 16) |
                                ((buf[handshake_len_pos+1] as usize) << 8) |
                                (buf[handshake_len_pos+2] as usize);
        let new_handshake_len = (old_handshake_len as isize + delta) as usize;
        buf[handshake_len_pos] = ((new_handshake_len >> 16) & 0xFF) as u8;
        buf[handshake_len_pos+1] = ((new_handshake_len >> 8) & 0xFF) as u8;
        buf[handshake_len_pos+2] = (new_handshake_len & 0xFF) as u8;

        // Adjust Record Length (2 bytes at offset 3)
        let record_len_pos = 3;
        let old_record_len = ((buf[record_len_pos] as usize) << 8) | (buf[record_len_pos+1] as usize);
        let new_record_len = (old_record_len as isize + delta) as usize;
        buf[record_len_pos] = (new_record_len >> 8) as u8;
        buf[record_len_pos+1] = (new_record_len & 0xFF) as u8;
    }

    /// Inject a new SNI extension at the end of the extensions list
    fn inject_sni_extension(
        buf: &mut Vec<u8>,
        extensions_start: usize,
        extensions_len: usize,
        hostname: &str,
    ) -> Result<(), SniError> {
        let new_ext = Self::build_sni_extension(hostname);
        let new_ext_len = new_ext.len();

        // Append new SNI extension at the end of extensions
        buf.splice(extensions_start + 2 + extensions_len..extensions_start + 2 + extensions_len, new_ext);

        // Adjust lengths
        Self::adjust_lengths(buf, extensions_start, extensions_len, extensions_len + new_ext_len);

        log::info!("🎭 SNI extension injected for {}", hostname);
        Ok(())
    }
}
