#[derive(thiserror::Error, Debug)]
pub enum SniError {
    #[error("Failed to parse TLS ClientHello")]
    ParseError,
    #[error("No SNI found in ClientHello")]
    NoSniFound,
    #[error("Buffer too small")]
    BufferTooSmall,
}
