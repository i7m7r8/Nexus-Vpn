use std::os::unix::io::RawFd;
use tokio::io::unix::AsyncFd;
use std::io::{Read, Write};
use std::fs::File;
use std::os::unix::io::FromRawFd;

pub struct TunDevice {
    async_fd: AsyncFd<File>,
}

impl TunDevice {
    pub fn new(fd: RawFd) -> anyhow::Result<Self> {
        let file = unsafe { File::from_raw_fd(fd) };
        // Set non-blocking mode for AsyncFd
        unsafe {
            let flags = libc::fcntl(fd, libc::F_GETFL);
            libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
        }
        Ok(Self {
            async_fd: AsyncFd::new(file)?,
        })
    }

    pub async fn read(&self, buf: &mut [u8]) -> std::io::Result<usize> {
        loop {
            let mut guard = self.async_fd.readable().await?;
            match guard.get_inner().read(buf) {
                Ok(n) => return Ok(n),
                Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                    guard.clear_ready();
                }
                Err(e) => return Err(e),
            }
        }
    }

    pub async fn write(&self, buf: &[u8]) -> std::io::Result<usize> {
        loop {
            let mut guard = self.async_fd.writable().await?;
            match guard.get_inner().write(buf) {
                Ok(n) => return Ok(n),
                Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                    guard.clear_ready();
                }
                Err(e) => return Err(e),
            }
        }
    }
}
