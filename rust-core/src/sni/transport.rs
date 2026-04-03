use async_trait::async_trait;
use std::net::SocketAddr;
use std::pin::Pin;
use futures_io::{AsyncRead, AsyncWrite};
use tor_rtcompat::{NetStreamProvider, NetStreamListener, StreamOps, SleepProvider, SpawnProvider, TlsProvider};
use std::task::{Context, Poll};
use futures::stream::Stream;
use std::time::Duration;

#[derive(Clone)]
pub struct SniRuntime<R: tor_rtcompat::Runtime> {
    inner: R,
    sni_host: String,
}

impl<R: tor_rtcompat::Runtime> SniRuntime<R> {
    pub fn new(inner: R, sni_host: String) -> Self {
        Self { inner, sni_host }
    }
}

impl<R: tor_rtcompat::Runtime> SpawnProvider for SniRuntime<R> {
    fn spawn_obj(&self, future: futures::future::FutureObj<'static, ()>) -> Result<(), futures::task::SpawnError> {
        self.inner.spawn_obj(future)
    }
}

impl<R: tor_rtcompat::Runtime> SleepProvider for SniRuntime<R> {
    type SleepFuture = R::SleepFuture;
    fn sleep(&self, duration: Duration) -> Self::SleepFuture {
        self.inner.sleep(duration)
    }
}

#[async_trait]
impl<R: tor_rtcompat::Runtime> NetStreamProvider<SocketAddr> for SniRuntime<R> {
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

impl<R: tor_rtcompat::Runtime> TlsProvider<WrappedStream<R::Stream>> for SniRuntime<R> {
    type Connector = R::TlsConnector;
    type TlsStream = R::TlsStream;
    fn tls_connector(&self) -> Self::Connector {
        self.inner.tls_connector()
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

impl<S: StreamOps + Send + Unpin + 'static> StreamOps for WrappedStream<S> {
    fn set_tcp_notsent_lowat(&self, notsent_lowat: u32) -> std::io::Result<()> {
        self.inner.set_tcp_notsent_lowat(notsent_lowat)
    }
    fn new_handle(&self) -> Box<dyn StreamOps + Send + Unpin> {
        self.inner.new_handle()
    }
}

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

impl<I: Stream<Item = std::io::Result<(S, SocketAddr)>> + Unpin, S: AsyncRead + AsyncWrite + StreamOps + Send + Sync + Unpin + 'static> Stream for WrappedIncoming<I, S> {
    type Item = std::io::Result<(WrappedStream<S>, SocketAddr)>;

    fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
        match Pin::new(&mut self.get_mut().inner).poll_next(cx) {
            Poll::Ready(Some(Ok((stream, addr)))) => Poll::Ready(Some(Ok((WrappedStream { inner: stream }, addr)))),
            Poll::Ready(Some(Err(e))) => Poll::Ready(Some(Err(e))),
            Poll::Ready(None) => Poll::Ready(None),
            Poll::Pending => Poll::Pending,
        }
    }
}
