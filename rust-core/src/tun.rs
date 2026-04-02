//! TUN interface — reads/writes raw IPv4 packets from Android TUN fd

use anyhow::{Result, bail};
use std::fs::File;
use std::os::unix::io::{FromRawFd, RawFd};

pub struct TunDevice {
    file: File,
    pub mtu: u16,
}

impl TunDevice {
    /// Wrap an existing TUN file descriptor.
    /// # Safety
    /// `fd` must be a valid, open TUN fd from Android VpnService.
    pub unsafe fn from_fd(fd: RawFd, mtu: u16) -> Result<Self> {
        let file = File::from_raw_fd(fd);
        Ok(Self { file, mtu })
    }

    /// Read one raw IP packet. Blocks until data arrives.
    pub fn read(&mut self, buf: &mut [u8]) -> Result<usize> {
        use std::io::Read;
        let n = self.file.read(buf)?;
        if n == 0 { bail!("TUN fd closed (EOF)"); }
        Ok(n)
    }

    /// Write one raw IP packet back to the TUN device.
    pub fn write(&mut self, pkt: &[u8]) -> Result<()> {
        use std::io::Write;
        self.file.write_all(pkt)?;
        Ok(())
    }

    /// Close the TUN fd (dropped on Drop).
    pub fn into_fd(self) -> RawFd {
        let f = self.file;
        let fd = f.as_raw_fd();
        std::mem::forget(f);
        fd
    }
}

impl Drop for TunDevice {
    fn drop(&mut self) {
        // File is auto-closed; nothing extra needed.
    }
}
