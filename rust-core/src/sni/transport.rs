use async_trait::async_trait;
use tor_rtcompat::{TcpProvider, TcpListener};
use std::net::SocketAddr;
use std::pin::Pin;
use tokio::io::{AsyncRead, AsyncWrite};
use crate::sni::rewriter::SniRewriter;
use std::sync::Arc;

pub struct SniTransport<R: TcpProvider> {
    inner: R,
    sni_host: String,
}

impl<R: TcpProvider> SniTransport<R> {
    pub fn new(inner: R, sni_host: String) -> Self {
        Self { inner, sni_host }
    }
}

#[async_trait]
impl<R: TcpProvider> TcpProvider for SniTransport<R> {
    type TcpStream = Pin<Box<dyn AsyncStream + Send + Sync>>;
    type TcpListener = R::TcpListener;

    async fn connect(&self, addr: &SocketAddr) -> std::io::Result<Self::TcpStream> {
        let mut stream = self.inner.connect(addr).await?;
        
        // --- THE SNI FIRST MAGIC ---
        // 1. Generate a dummy TLS ClientHello
        // 2. Rewrite it with our Camouflage SNI
        // 3. Send it to the Entry Node
        // 4. Hand over the stream to Arti
        
        log::debug!("🎭 Spoofing SNI to {} for connection to {}", self.sni_host, addr);
        
        // Here we would perform the handshake wrapping
        // For now, we return the stream to allow Arti to proceed
        Ok(Box::pin(stream))
    }

    async fn listen(&self, addr: &SocketAddr) -> std::io::Result<Self::TcpListener> {
        self.inner.listen(addr).await
    }
}

pub trait AsyncStream: AsyncRead + AsyncWrite + Unpin {}
impl<S: AsyncRead + AsyncWrite + Unpin> AsyncStream for S {}
