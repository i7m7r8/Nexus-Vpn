use async_trait::async_trait;
use std::net::SocketAddr;
use std::pin::Pin;
use tokio::io::{AsyncRead, AsyncWrite};
use tor_rtcompat::{NetStreamProvider, TcpListener};

pub struct SniTransport<R: NetStreamProvider<SocketAddr>> {
    inner: R,
    sni_host: String,
}

impl<R: NetStreamProvider<SocketAddr>> SniTransport<R> {
    pub fn new(inner: R, sni_host: String) -> Self {
        Self { inner, sni_host }
    }
}

#[async_trait]
impl<R: NetStreamProvider<SocketAddr>> NetStreamProvider<SocketAddr> for SniTransport<R> {
    type Stream = Pin<Box<dyn AsyncStream + Send + Sync>>;
    type Listener = R::Listener;

    async fn connect(&self, addr: &SocketAddr) -> std::io::Result<Self::Stream> {
        let stream = self.inner.connect(addr).await?;
        log::debug!("🎭 Spoofing SNI to {} for connection to {}", self.sni_host, addr);
        // Real SNI wrapping logic would go here.
        // For now, return the stream pinned and boxed.
        Ok(Box::pin(stream))
    }

    async fn listen(&self, addr: &SocketAddr) -> std::io::Result<Self::Listener> {
        self.inner.listen(addr).await
    }
}

pub trait AsyncStream: AsyncRead + AsyncWrite + Unpin {}
impl<S: AsyncRead + AsyncWrite + Unpin> AsyncStream for S {}
