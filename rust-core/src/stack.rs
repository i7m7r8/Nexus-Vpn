use smoltcp::iface::{Config, Interface, SocketSet};
use smoltcp::phy::{Device, DeviceCapabilities, Medium};
use smoltcp::time::Instant;
use smoltcp::wire::{IpAddress, IpCidr};
use std::collections::VecDeque;

pub struct VirtualDevice {
    pub rx_queue: VecDeque<Vec<u8>>,
    pub tx_queue: VecDeque<Vec<u8>>,
}

impl Device for VirtualDevice {
    type RxToken<'a> = RxToken;
    type TxToken<'a> = TxToken<'a>;

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
    fn consume<R, F>(mut self, f: F) -> R
    where
        F: FnOnce(&mut [u8]) -> R,
    {
        f(&mut self.buffer)
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
        
        interface.routes_mut().add_default_ipv4_route(IpAddress::v4(10, 8, 0, 1)).unwrap();

        Self {
            interface,
            socket_set: SocketSet::new(vec![]),
            device,
        }
    }

    pub fn add_tcp_listener(&mut self, port: u16) -> smoltcp::iface::SocketHandle {
        let tcp_rx_buffer = TcpSocketBuffer::new(vec![0; 65535]);
        let tcp_tx_buffer = TcpSocketBuffer::new(vec![0; 65535]);
        let mut tcp_socket = TcpSocket::new(tcp_rx_buffer, tcp_tx_buffer);
        tcp_socket.listen(port).unwrap();
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
