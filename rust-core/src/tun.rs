// TUN Device — borrowed fd (never closes, owned by Kotlin's ParcelFileDescriptor)

use std::os::unix::io::RawFd;
use std::os::unix::io::AsRawFd;
use std::io;
use libc;

/// Borrowed fd wrapper — does NOT take ownership
struct BorrowedFd(RawFd);

impl AsRawFd for BorrowedFd {
    fn as_raw_fd(&self) -> RawFd {
        self.0
    }
}

pub struct TunDevice {
    fd: BorrowedFd,
}

impl TunDevice {
    pub fn new(fd: RawFd) -> anyhow::Result<Self> {
        unsafe {
            let flags = libc::fcntl(fd, libc::F_GETFL);
            libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
        }
        Ok(Self { fd: BorrowedFd(fd) })
    }

    pub async fn read(&self, buf: &mut [u8]) -> io::Result<usize> {
        loop {
            let n = unsafe {
                libc::read(self.fd.as_raw_fd(), buf.as_mut_ptr() as *mut libc::c_void, buf.len())
            };
            if n < 0 {
                let err = io::Error::last_os_error();
                if err.kind() == io::ErrorKind::WouldBlock {
                    tokio::time::sleep(std::time::Duration::from_millis(10)).await;
                    continue;
                }
                return Err(err);
            }
            return Ok(n as usize);
        }
    }

    pub async fn write(&self, buf: &[u8]) -> io::Result<usize> {
        loop {
            let n = unsafe {
                libc::write(self.fd.as_raw_fd(), buf.as_ptr() as *const libc::c_void, buf.len())
            };
            if n < 0 {
                let err = io::Error::last_os_error();
                if err.kind() == io::ErrorKind::WouldBlock {
                    tokio::time::sleep(std::time::Duration::from_millis(10)).await;
                    continue;
                }
                return Err(err);
            }
            return Ok(n as usize);
        }
    }
}

impl smoltcp::phy::Device for TunDevice {
    type RxToken<'a> = RxToken where Self: 'a;
    type TxToken<'a> = TxToken where Self: 'a;

    fn receive(&mut self, _timestamp: smoltcp::time::Instant) -> Option<(Self::RxToken<'_>, Self::TxToken<'_>)> {
        // We handle reading manually via read()
        None
    }

    fn transmit(&mut self, _timestamp: smoltcp::time::Instant) -> Option<Self::TxToken<'_>> {
        Some(TxToken)
    }

    fn capabilities(&self) -> smoltcp::phy::DeviceCapabilities {
        let mut caps = smoltcp::phy::DeviceCapabilities::default();
        caps.medium = smoltcp::phy::Medium::Ip;
        caps.max_transmission_unit = 1500;
        caps
    }
}

pub struct RxToken;
impl smoltcp::phy::RxToken for RxToken {
    fn consume<R, F>(self, _f: F) -> R where F: FnOnce(&[u8]) -> R {
        unreachable!()
    }
}

pub struct TxToken;
impl smoltcp::phy::TxToken for TxToken {
    fn consume<R, F>(self, len: usize, f: F) -> R where F: FnOnce(&mut [u8]) -> R {
        let mut buf = vec![0u8; len];
        let result = f(&mut buf);
        result
    }
}
