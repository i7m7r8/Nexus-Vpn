use std::os::unix::io::RawFd;
use tokio::io::unix::AsyncFd;
use std::os::unix::io::AsRawFd;
use std::fs::File;
use std::io;

/// A wrapper that holds a borrowed fd without taking ownership.
/// This is critical for Android VpnService: the TUN fd is owned by
/// Kotlin's ParcelFileDescriptor — Rust must NEVER close it.
struct BorrowedFd(RawFd);

impl AsRawFd for BorrowedFd {
    fn as_raw_fd(&self) -> RawFd {
        self.0
    }
}

pub struct TunDevice {
    async_fd: AsyncFd<BorrowedFd>,
}

impl TunDevice {
    pub fn new(fd: RawFd) -> anyhow::Result<Self> {
        // Set non-blocking mode for AsyncFd
        unsafe {
            let flags = libc::fcntl(fd, libc::F_GETFL);
            libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
        }

        // Create a borrowed fd wrapper — does NOT take ownership
        let borrowed = BorrowedFd(fd);
        let async_fd = AsyncFd::new(borrowed)?;

        Ok(Self { async_fd })
    }

    pub async fn read(&self, buf: &mut [u8]) -> io::Result<usize> {
        loop {
            let mut guard = self.async_fd.readable().await?;
            let n = unsafe {
                libc::read(
                    self.async_fd.as_raw_fd(),
                    buf.as_mut_ptr() as *mut libc::c_void,
                    buf.len(),
                )
            };
            if n < 0 {
                let err = io::Error::last_os_error();
                if err.kind() == io::ErrorKind::WouldBlock {
                    guard.clear_ready();
                    continue;
                }
                return Err(err);
            }
            return Ok(n as usize);
        }
    }

    pub async fn write(&self, buf: &[u8]) -> io::Result<usize> {
        loop {
            let mut guard = self.async_fd.writable().await?;
            let n = unsafe {
                libc::write(
                    self.async_fd.as_raw_fd(),
                    buf.as_ptr() as *const libc::c_void,
                    buf.len(),
                )
            };
            if n < 0 {
                let err = io::Error::last_os_error();
                if err.kind() == io::ErrorKind::WouldBlock {
                    guard.clear_ready();
                    continue;
                }
                return Err(err);
            }
            return Ok(n as usize);
        }
    }
}
