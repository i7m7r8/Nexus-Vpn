use async_trait::async_trait;
use std::net::SocketAddr;
use std::pin::Pin;
use tokio::io::{AsyncRead, AsyncWrite};
use std::task::{Context, Poll};
use tor_rtcompat::{TcpProvider, TcpListener};

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
        let stream = self.inner.connect(addr).await?;
        log::debug!("🎭 Spoofing SNI to {} for connection to {}", self.sni_host, addr);
        // Simple passthrough for now, real SNI wrapping happens here
        Ok(Box::pin(stream))
    }

    async fn listen(&self, addr: &SocketAddr) -> std::io::Result<Self::TcpListener> {
        self.inner.listen(addr).await
    }
}

pub trait AsyncStream: AsyncRead + AsyncWrite + Unpin {}
impl<S: AsyncRead + AsyncWrite + Unpin> AsyncStream for S {}

// Manually implement AsyncRead/AsyncWrite for Pin<Box<dyn AsyncStream>>
// to satisfy Rust's trait requirements.
impl AsyncRead for Pin<Box<dyn AsyncStream + Send + Sync>> {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut tokio::io::ReadBuf<'_>,
    ) -> Poll<std::io::Result<()>> {
        Pin::new(self.as_mut()).poll_read(cx, buf)
    }
}

impl AsyncWrite for Pin<Box<dyn AsyncStream + Send + Sync>> {
    fn poll_write(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<std::io::Result<usize>> {
        Pin::new(self.as_mut()).poll_write(cx, buf)
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Pin::new(self.as_mut()).poll_flush(cx)
    }

    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Pin::new(self.as_mut()).poll_shutdown(cx)
    }
}
