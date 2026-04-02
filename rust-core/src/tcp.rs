//! Virtual TCP stack — synthesizes TCP handshakes and manages flow state

use std::collections::HashMap;
use std::net::Ipv4Addr;

/// TCP flags
pub const FLAG_SYN: u8 = 0x02;
pub const FLAG_ACK: u8 = 0x10;
pub const FLAG_FIN: u8 = 0x01;
pub const FLAG_RST: u8 = 0x04;
pub const FLAG_PSH: u8 = 0x08;

/// Flow states
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FlowState { Idle, SynReceived, Established, Closed }

/// Unique key for a TCP flow
#[derive(Debug, Clone, Hash, PartialEq, Eq)]
pub struct FlowKey {
    pub src: Ipv4Addr,
    pub src_port: u16,
    pub dst: Ipv4Addr,
    pub dst_port: u16,
}

/// Virtual TCP flow state tracked by the Rust core
#[derive(Debug)]
pub struct Flow {
    pub key: FlowKey,
    pub state: FlowState,
    pub my_seq: u32,
    pub my_ack: u32,
    pub tunnel_ready: bool,
}

impl Flow {
    pub fn new(key: FlowKey) -> Self {
        Self {
            key,
            state: FlowState::Idle,
            my_seq: fastrand::u32(1_000_000..4_000_000),
            my_ack: 0,
            tunnel_ready: false,
        }
    }

    /// Process an incoming TCP segment from TUN
    pub fn handle_packet(&mut self, seq: u32, ack: u32, flags: u8, payload_len: usize) -> Vec<TcpAction> {
        let mut actions = Vec::new();

        if flags & FLAG_RST != 0 {
            self.state = FlowState::Closed;
            return actions;
        }

        if flags & FLAG_SYN != 0 && flags & FLAG_ACK == 0 {
            // New connection — SYN
            self.my_ack = seq.wrapping_add(1);
            self.state = FlowState::SynReceived;
            actions.push(TcpAction::SynAck {
                seq: self.my_seq,
                ack: self.my_ack,
            });
            self.my_seq = self.my_seq.wrapping_add(1); // SYN consumes 1 seq
        } else if flags & FLAG_ACK != 0 && self.state == FlowState::SynReceived {
            // Third ACK — connection established
            self.my_ack = seq.wrapping_add(payload_len as u32);
            self.state = FlowState::Established;
            actions.push(TcpAction::Established);
        } else if flags & FLAG_ACK != 0 && self.state == FlowState::Established && payload_len > 0 {
            // Data packet — acknowledge and forward
            self.my_ack = seq.wrapping_add(payload_len as u32);
        } else if flags & FLAG_FIN != 0 {
            self.state = FlowState::Closed;
            actions.push(TcpAction::FinAck {
                seq: self.my_seq,
                ack: self.my_ack.wrapping_add(1),
            });
        }

        actions
    }
}

/// Actions the TCP stack wants the outer loop to perform
#[derive(Debug, Clone)]
pub enum TcpAction {
    /// Send SYN+ACK back to the client (TUN writer)
    SynAck { seq: u32, ack: u32 },
    /// Flow is now ESTABLISHED — open SOCKS5 tunnel to Arti
    Established,
    /// Send FIN+ACK back to the client
    FinAck { seq: u32, ack: u32 },
}

/// Parse a raw IPv4 packet and extract TCP info
/// Returns None if not a valid TCP packet.
#[must_use]
pub fn parse_tcp_packet(pkt: &[u8]) -> Option<(Ipv4Addr, u16, Ipv4Addr, u16, u32, u32, u8, usize)> {
    if pkt.len() < 20 { return None; }
    let ihl = ((pkt[0] & 0x0F) as usize) * 4;
    if ihl < 20 || pkt.len() < ihl + 20 { return None; }
    if pkt[9] != 6 { return None; } // Protocol must be TCP

    let ip_total = u16::from_be_bytes([pkt[2], pkt[3]]) as usize;
    if ip_total > pkt.len() { return None; }

    let src = Ipv4Addr::new(pkt[12], pkt[13], pkt[14], pkt[15]);
    let dst = Ipv4Addr::new(pkt[16], pkt[17], pkt[18], pkt[19]);

    let tcp_off = ihl;
    if pkt.len() < tcp_off + 20 { return None; }
    let src_port = u16::from_be_bytes([pkt[tcp_off], pkt[tcp_off + 1]]);
    let dst_port = u16::from_be_bytes([pkt[tcp_off + 2], pkt[tcp_off + 3]]);
    let seq = u32::from_be_bytes([pkt[tcp_off + 4], pkt[tcp_off + 5], pkt[tcp_off + 6], pkt[tcp_off + 7]]);
    let ack = u32::from_be_bytes([pkt[tcp_off + 8], pkt[tcp_off + 9], pkt[tcp_off + 10], pkt[tcp_off + 11]]);
    let flags = pkt[tcp_off + 13];

    let tcp_data_offset = ((pkt[tcp_off + 12] >> 4) & 0x0F) as usize * 4;
    if tcp_data_offset < 20 { return None; }
    let payload_len = ip_total - ihl - tcp_data_offset;

    Some((src, src_port, dst, dst_port, seq, ack, flags, payload_len))
}

/// Build a raw IPv4 + TCP packet to write back to TUN
#[must_use]
pub fn build_tcp_packet(
    src: Ipv4Addr, dst: Ipv4Addr,
    src_port: u16, dst_port: u16,
    seq: u32, ack: u32,
    flags: u8,
    payload: &[u8],
) -> Vec<u8> {
    let ip_hdr_len = 20;
    let tcp_hdr_len = 20;
    let total_len = ip_hdr_len + tcp_hdr_len + payload.len();
    let mut pkt = vec![0u8; total_len];

    // IPv4 header
    pkt[0] = 0x45; // Version 4, IHL 5
    pkt[8] = 64;   // TTL
    pkt[9] = 6;    // Protocol TCP
    pkt[12..16].copy_from_slice(&src.octets());
    pkt[16..20].copy_from_slice(&dst.octets());
    pkt[2] = ((total_len >> 8) & 0xFF) as u8;
    pkt[3] = (total_len & 0xFF) as u8;

    // IP checksum
    let cs = ip_checksum(&pkt[..ip_hdr_len]);
    pkt[10] = (cs >> 8) as u8;
    pkt[11] = (cs & 0xFF) as u8;

    // TCP header
    let t = ip_hdr_len;
    pkt[t]     = (src_port >> 8) as u8;
    pkt[t + 1] = (src_port & 0xFF) as u8;
    pkt[t + 2] = (dst_port >> 8) as u8;
    pkt[t + 3] = (dst_port & 0xFF) as u8;
    pkt[t + 4..t + 8].copy_from_slice(&seq.to_be_bytes());
    pkt[t + 8..t + 12].copy_from_slice(&ack.to_be_bytes());
    pkt[t + 12] = (5 << 4) as u8; // Data offset 5 (20 bytes)
    pkt[t + 13] = flags;

    // Payload
    if !payload.is_empty() {
        pkt[t + tcp_hdr_len..].copy_from_slice(payload);
    }

    pkt
}

#[must_use]
fn ip_checksum(hdr: &[u8]) -> u16 {
    let mut sum: u32 = 0;
    for chunk in hdr.chunks(2) {
        let val = if chunk.len() == 2 {
            u16::from_be_bytes([chunk[0], chunk[1]]) as u32
        } else {
            (chunk[0] as u32) << 8
        };
        sum += val;
    }
    while (sum >> 16) != 0 {
        sum = (sum & 0xFFFF) + (sum >> 16);
    }
    !sum as u16
}
