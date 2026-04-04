// TUN Device — borrowed fd (never closes, owned by Kotlin's ParcelFileDescriptor)

use std::os::unix::io::RawFd;
use std::os::unix::io::AsRawFd;
use std::io;
use libc;
use std::sync::atomic::AtomicI32;

static TUN_FD: AtomicI32 = AtomicI32::new(-1);

/// Borrowed fd wrapper — does NOT take ownership
struct BorrowedFd(RawFd);

impl AsRawFd for BorrowedFd {
    fn as_raw_fd(&self) -> RawFd {
        self.0
    }
}

pub struct TunDevice {
    fd: BorrowedFd,
    rx_buf: Vec<u8>,
}

impl TunDevice {
    pub fn new(fd: RawFd) -> anyhow::Result<Self> {
        unsafe {
            let flags = libc::fcntl(fd, libc::F_GETFL);
            libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
        }
        TUN_FD.store(fd, std::sync::atomic::Ordering::SeqCst);
        Ok(Self {
            fd: BorrowedFd(fd),
            rx_buf: vec![0u8; 4096],
        })
    }

    fn read_raw(&mut self) -> io::Result<usize> {
        let n = unsafe {
            libc::read(self.fd.as_raw_fd(), self.rx_buf.as_mut_ptr() as *mut libc::c_void, self.rx_buf.len())
        };
        if n < 0 {
            return Err(io::Error::last_os_error());
        }
        Ok(n as usize)
    }
}

impl smoltcp::phy::Device for TunDevice {
    type RxToken<'a> = RxToken<'a> where Self: 'a;
    type TxToken<'a> = TxToken where Self: 'a;

    fn receive(&mut self, _timestamp: smoltcp::time::Instant) -> Option<(Self::RxToken<'_>, Self::TxToken<'_>)> {
        // Try to read from TUN fd
        match self.read_raw() {
            Ok(n) if n > 0 => {
                Some((RxToken { buf: &self.rx_buf[..n] }, TxToken))
            }
            _ => None,
        }
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

pub struct RxToken<'a> {
    buf: &'a [u8],
}

impl smoltcp::phy::RxToken for RxToken<'_> {
    fn consume<R, F>(self, f: F) -> R where F: FnOnce(&[u8]) -> R {
        f(self.buf)
    }
}

pub struct TxToken;

impl smoltcp::phy::TxToken for TxToken {
    fn consume<R, F>(self, len: usize, f: F) -> R where F: FnOnce(&mut [u8]) -> R {
        let mut buf = vec![0u8; len];
        let result = f(&mut buf);
        // Write packet to TUN fd
        let fd = TUN_FD.load(std::sync::atomic::Ordering::SeqCst);
        if fd >= 0 {
            unsafe {
                libc::write(fd, buf.as_ptr() as *const libc::c_void, len);
            }
        }
        result
    }
}
