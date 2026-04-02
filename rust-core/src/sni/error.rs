use thiserror::Error;

#[derive(Error, Debug)]
pub enum SniError {
    #[error("Invalid TLS record: {0}")]
    InvalidTlsRecord(String),
    #[error("Invalid handshake type: 0x{0:02x}")]
    InvalidHandshakeType(u8),
    #[error("SNI extension not found")]
    SniNotFound,
    #[error("Buffer overflow")]
    BufferOverflow,
    #[error("Invalid hostname: {0}")]
    InvalidHostname(String),
    #[error("Hostname too long: {length} (max {max})")]
    HostnameTooLong { length: usize, max: usize },
}

pub type SniResult<T> = Result<T, SniError>;
