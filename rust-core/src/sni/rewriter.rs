use crate::sni::error::SniError;

pub struct SniRewriter;

impl SniRewriter {
    /// Replaces the SNI in a TLS ClientHello buffer with a new one.
    /// This handles adjusting the lengths of the extension and the overall record.
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

        while pos + 4 <= extensions_end {
            let ext_type = ((buf[pos] as u16) << 8) | (buf[pos+1] as u16);
            let ext_len = ((buf[pos+2] as usize) << 8) | (buf[pos+3] as usize);
            
            if ext_type == 0x0000 { // Server Name Extension
                let sni_ext_start = pos;
                let sni_ext_total_len = 4 + ext_len;
                
                // Construct new SNI extension
                let mut new_ext = Vec::new();
                new_ext.extend_from_slice(&[0x00, 0x00]); // Extension Type
                
                let host_name_bytes = new_sni.as_bytes();
                let host_name_len = host_name_bytes.len();
                let server_name_list_len = host_name_len + 3;
                let ext_data_len = server_name_list_len + 2;

                new_ext.push((ext_data_len >> 8) as u8);
                new_ext.push((ext_data_len & 0xFF) as u8);
                new_ext.push((server_name_list_len >> 8) as u8);
                new_ext.push((server_name_list_len & 0xFF) as u8);
                new_ext.push(0x00); // Host Name Type (0)
                new_ext.push((host_name_len >> 8) as u8);
                new_ext.push((host_name_len & 0xFF) as u8);
                new_ext.extend_from_slice(host_name_bytes);

                // Replace in original buffer
                buf.splice(sni_ext_start..sni_ext_start + sni_ext_total_len, new_ext.clone());
                
                // Adjust Extensions Length
                let new_extensions_len = extensions_len - sni_ext_total_len + new_ext.len();
                buf[extensions_start] = (new_extensions_len >> 8) as u8;
                buf[extensions_start + 1] = (new_extensions_len & 0xFF) as u8;

                // Adjust Handshake Length
                let handshake_len_pos = 5 + 1;
                let old_handshake_len = ((buf[handshake_len_pos] as usize) << 16) | 
                                        ((buf[handshake_len_pos+1] as usize) << 8) | 
                                        (buf[handshake_len_pos+2] as usize);
                let new_handshake_len = old_handshake_len - sni_ext_total_len + new_ext.len();
                buf[handshake_len_pos] = ((new_handshake_len >> 16) & 0xFF) as u8;
                buf[handshake_len_pos+1] = ((new_handshake_len >> 8) & 0xFF) as u8;
                buf[handshake_len_pos+2] = (new_handshake_len & 0xFF) as u8;

                // Adjust Record Length
                let record_len_pos = 3;
                let old_record_len = ((buf[record_len_pos] as usize) << 8) | (buf[record_len_pos+1] as usize);
                let new_record_len = old_record_len - sni_ext_total_len + new_ext.len();
                buf[record_len_pos] = (new_record_len >> 8) as u8;
                buf[record_len_pos+1] = (new_record_len & 0xFF) as u8;

                return Ok(());
            }
            pos += 4 + ext_len;
        }

        Err(SniError::NoSniFound)
    }
}
