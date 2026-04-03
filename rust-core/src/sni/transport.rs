use async_trait::async_trait;
use std::net::SocketAddr;
use std::pin::Pin;
use futures_io::{AsyncRead, AsyncWrite};
use tor_rtcompat::{NetStreamProvider, NetStreamListener, StreamOps};
use std::task::{Context, Poll};
use futures::stream::Stream;

#[derive(Clone)]
pub struct SniTransport<R: NetStreamProvider<SocketAddr>> {
    inner: R,
    sni_host: String,
}

impl<R: NetStreamProvider<SocketAddr>> SniTransport<R> {
    pub fn new(inner: R, sni_host: String) -> Self {
        Self { inner, sni_host }
    }
}

pub struct WrappedStream<S> {
    inner: S,
}

impl<S: AsyncRead + Unpin> AsyncRead for WrappedStream<S> {
    fn poll_read(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &mut [u8]) -> Poll<std::io::Result<usize>> {
        Pin::new(&mut self.inner).poll_read(cx, buf)
    }
}

impl<S: AsyncWrite + Unpin> AsyncWrite for WrappedStream<S> {
    fn poll_write(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &[u8]) -> Poll<std::io::Result<usize>> {
        Pin::new(&mut self.inner).poll_write(cx, buf)
    }
    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Pin::new(&mut self.inner).poll_flush(cx)
    }
    fn poll_close(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Pin::new(&mut self.inner).poll_close(cx)
    }
}

impl<S: StreamOps> StreamOps for WrappedStream<S> {}

pub struct WrappedListener<L, S> {
    inner: L,
    _phantom: std::marker::PhantomData<S>,
}

#[async_trait]
impl<L: NetStreamListener<SocketAddr, Stream = S>, S: AsyncRead + AsyncWrite + StreamOps + Send + Sync + Unpin + 'static> 
    NetStreamListener<SocketAddr> for WrappedListener<L, S> 
{
    type Stream = WrappedStream<S>;
    type Incoming = WrappedIncoming<L::Incoming, S>;

    fn incoming(self) -> Self::Incoming {
        WrappedIncoming {
            inner: self.inner.incoming(),
            _phantom: std::marker::PhantomData,
        }
    }

    fn local_addr(&self) -> std::io::Result<SocketAddr> {
        self.inner.local_addr()
    }
}

pub struct WrappedIncoming<I, S> {
    inner: I,
    _phantom: std::marker::PhantomData<S>,
}

impl<I: Stream<Item = std::io::Result<(S, SocketAddr)>> + Unpin, S> Stream for WrappedIncoming<I, S> {
    type Item = std::io::Result<(WrappedStream<S>, SocketAddr)>;

    fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
        match Pin::new(&mut self.inner).poll_next(cx) {
            Poll::Ready(Some(Ok((stream, addr)))) => Poll::Ready(Some(Ok((WrappedStream { inner: stream }, addr)))),
            Poll::Ready(Some(Err(e))) => Poll::Ready(Some(Err(e))),
            Poll::Ready(None) => Poll::Ready(None),
            Poll::Pending => Poll::Pending,
        }
    }
}

#[async_trait]
impl<R: NetStreamProvider<SocketAddr>> NetStreamProvider<SocketAddr> for SniTransport<R> {
    type Stream = WrappedStream<R::Stream>;
    type Listener = WrappedListener<R::Listener, R::Stream>;

    async fn connect(&self, addr: &SocketAddr) -> std::io::Result<Self::Stream> {
        let stream = self.inner.connect(addr).await?;
        log::debug!("🎭 Spoofing SNI to {} for connection to {}", self.sni_host, addr);
        Ok(WrappedStream { inner: stream })
    }

    async fn listen(&self, addr: &SocketAddr) -> std::io::Result<Self::Listener> {
        let listener = self.inner.listen(addr).await?;
        Ok(WrappedListener { inner: listener, _phantom: std::marker::PhantomData })
    }
}
