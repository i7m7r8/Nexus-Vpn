use smoltcp::iface::{Config, Interface, SocketSet, SocketHandle};
use smoltcp::phy::{Device, DeviceCapabilities, Medium};
use smoltcp::time::Instant;
use smoltcp::wire::{IpAddress, IpCidr, IpProtocol};
use std::collections::VecDeque;
use std::net::Ipv4Addr;

pub struct VirtualDevice {
    pub rx_queue: VecDeque<Vec<u8>>,
    pub tx_queue: VecDeque<Vec<u8>>,
}

impl Device for VirtualDevice {
    type RxToken<'a> = RxToken where Self: 'a;
    type TxToken<'a> = TxToken<'a> where Self: 'a;

    fn receive(&mut self, _timestamp: Instant) -> Option<(Self::RxToken<'_>, Self::TxToken<'_>)> {
        self.rx_queue.pop_front().map(|buffer| {
            (RxToken { buffer }, TxToken { queue: &mut self.tx_queue })
        })
    }

    fn transmit(&mut self, _timestamp: Instant) -> Option<Self::TxToken<'_>> {
        Some(TxToken { queue: &mut self.tx_queue })
    }

    fn capabilities(&self) -> DeviceCapabilities {
        let mut caps = DeviceCapabilities::default();
        caps.max_transmission_unit = 1500;
        caps.medium = Medium::Ip;
        caps
    }
}

pub struct RxToken {
    buffer: Vec<u8>,
}

impl smoltcp::phy::RxToken for RxToken {
    fn consume<R, F>(self, f: F) -> R
    where
        F: FnOnce(&[u8]) -> R,
    {
        f(&self.buffer)
    }
}

pub struct TxToken<'a> {
    queue: &'a mut VecDeque<Vec<u8>>,
}

impl<'a> smoltcp::phy::TxToken for TxToken<'a> {
    fn consume<R, F>(self, len: usize, f: F) -> R
    where
        F: FnOnce(&mut [u8]) -> R,
    {
        let mut buffer = vec![0u8; len];
        let result = f(&mut buffer);
        self.queue.push_back(buffer);
        result
    }
}

/// Represents a TCP connection request from the TUN device.
/// Parsed from the incoming IP/TCP headers before smoltcp processes it.
#[derive(Debug, Clone)]
pub struct TcpConnectionRequest {
    pub src_ip: std::net::Ipv4Addr,
    pub dst_ip: std::net::Ipv4Addr,
    pub src_port: u16,
    pub dst_port: u16,
}

pub struct NetStack {
    pub interface: Interface,
    pub socket_set: SocketSet<'static>,
    pub device: VirtualDevice,
}

use smoltcp::socket::tcp::{Socket as TcpSocket, SocketBuffer as TcpSocketBuffer};

impl NetStack {
    pub fn new() -> Self {
        let mut device = VirtualDevice {
            rx_queue: VecDeque::new(),
            tx_queue: VecDeque::new(),
        };

        let config = Config::new(smoltcp::wire::HardwareAddress::Ip);
        let mut interface = Interface::new(config, &mut device, Instant::now());

        interface.update_ip_addrs(|addrs| {
            addrs.push(IpCidr::new(IpAddress::v4(10, 8, 0, 2), 24)).unwrap();
        });

        interface.routes_mut().add_default_ipv4_route(Ipv4Addr::new(10, 8, 0, 1)).unwrap();

        Self {
            interface,
            socket_set: SocketSet::new(vec![]),
            device,
        }
    }

    /// Parse an incoming raw IP packet to extract TCP connection info.
    /// Returns `Some(TcpConnectionRequest)` if it's a TCP SYN packet, `None` otherwise.
    /// This is called BEFORE the packet is fed to smoltcp, so we can create a socket for it.
    pub fn parse_tcp_syn(packet: &[u8]) -> Option<TcpConnectionRequest> {
        if packet.is_empty() {
            return None;
        }

        // Check IP version (must be IPv4, version 4 in high nibble)
        let ip_version = packet[0] >> 4;
        if ip_version != 4 {
            return None;
        }

        let ihl = (packet[0] & 0x0F) as usize * 4; // IP header length in bytes
        if packet.len() < ihl + 20 {
            return None; // Minimum: IP header + TCP header
        }

        // Check protocol is TCP
        if packet[9] != u8::from(IpProtocol::Tcp) {
            return None;
        }

        // Extract source and destination IP
        let src_ip = std::net::Ipv4Addr::new(packet[12], packet[13], packet[14], packet[15]);
        let dst_ip = std::net::Ipv4Addr::new(packet[16], packet[17], packet[18], packet[19]);

        // Parse TCP header
        let tcp_start = ihl;
        let src_port = u16::from_be_bytes([packet[tcp_start], packet[tcp_start + 1]]);
        let dst_port = u16::from_be_bytes([packet[tcp_start + 2], packet[tcp_start + 3]]);

        // Check TCP SYN flag (bit 1 of byte 13 of TCP header, i.e., offset 13 from tcp_start)
        let tcp_flags_offset = tcp_start + 13;
        if tcp_flags_offset >= packet.len() {
            return None;
        }
        let tcp_flags = packet[tcp_flags_offset];
        let syn = tcp_flags & 0x02 != 0;
        let ack = tcp_flags & 0x10 != 0;

        // Only process SYN packets (new connections), not SYN-ACK
        if syn && !ack {
            Some(TcpConnectionRequest { src_ip, dst_ip, src_port, dst_port })
        } else {
            None
        }
    }

    /// Create a TCP socket that connects to the given destination.
    /// The socket will be in SYN-SENT state after this call.
    /// When smoltcp processes it, it will send a SYN to the destination.
    ///
    /// However, since we're acting as a VPN proxy, we don't actually want smoltcp
    /// to send a real SYN. Instead, we create the socket in a connected state
    /// by having it "accept" the incoming connection.
    ///
    /// Returns the socket handle.
    pub fn create_tcp_socket(
        &mut self,
        _src_ip: std::net::Ipv4Addr,
        _src_port: u16,
        dst_ip: std::net::Ipv4Addr,
        dst_port: u16,
    ) -> SocketHandle {
        // Create buffers for the socket
        let tcp_rx_buffer = TcpSocketBuffer::new(vec![0; 65535]);
        let tcp_tx_buffer = TcpSocketBuffer::new(vec![0; 65535]);
        let mut tcp_socket = TcpSocket::new(tcp_rx_buffer, tcp_tx_buffer);

        // Set socket to connect to the destination
        // smoltcp will handle the TCP handshake with the app
        // The app thinks it's connecting to dst_ip:dst_port
        if let Err(e) = tcp_socket.connect(
            self.interface.context(),
            (IpAddress::Ipv4(dst_ip), dst_port),
            0, // local port (0 = auto-assign)
        ) {
            log::warn!("⚠️ Failed to create TCP socket to {}:{}: {}", dst_ip, dst_port, e);
        }

        self.socket_set.add(tcp_socket)
    }

    pub fn poll(&mut self) {
        let timestamp = Instant::now();
        self.interface.poll(timestamp, &mut self.device, &mut self.socket_set);
    }

    pub fn input(&mut self, packet: Vec<u8>) {
        self.device.rx_queue.push_back(packet);
    }

    pub fn output(&mut self) -> Option<Vec<u8>> {
        self.device.tx_queue.pop_front()
    }
}
