//! SniRuntime — wraps an Arti runtime for future relay-level SNI spoofing.
//!
//! In Arti 0.40.0, the `Runtime` trait requires implementing many sub-traits
//! with complex lifetime signatures. We delegate ALL trait implementations to
//! the inner runtime. The SNI spoofing is handled at the app level by
//! `SniInterceptor` instead.
//!
//! The `sni_host` field is stored here for future use when Arti exposes a
//! simpler customization API.

use tor_rtcompat::Runtime;
use std::sync::Arc;

// ===========================================================================
// SniRuntime — transparent wrapper (delegates everything to inner runtime)
// ===========================================================================

#[derive(Clone, Debug)]
pub struct SniRuntime<R: Runtime> {
    inner: R,
    sni_host: Arc<parking_lot::Mutex<String>>,
}

impl<R: Runtime> SniRuntime<R> {
    pub fn new(inner: R, sni_host: Arc<parking_lot::Mutex<String>>) -> Self {
        Self { inner, sni_host }
    }

    pub fn inner(&self) -> &R {
        &self.inner
    }

    pub fn sni_host(&self) -> String {
        self.sni_host.lock().clone()
    }
}

impl<R: Runtime> tor_rtcompat::SleepProvider for SniRuntime<R> {
    type SleepFuture = R::SleepFuture;
    fn sleep(&self, duration: std::time::Duration) -> Self::SleepFuture {
        self.inner.sleep(duration)
    }
}

impl<R: Runtime> tor_rtcompat::Blocking for SniRuntime<R> {
    type ThreadHandle<T> = R::ThreadHandle<T>;
    fn spawn_blocking<F, T>(&self, f: F) -> Self::ThreadHandle<T>
    where
        F: FnOnce() -> T + Send + 'static,
        T: Send + 'static,
    {
        self.inner.spawn_blocking(f)
    }
    fn reenter_block_on<F>(&self, fut: F) -> F::Output
    where
        F: std::future::Future + Send,
        F::Output: Send,
    {
        self.inner.reenter_block_on(fut)
    }
}

impl<R: Runtime> tor_rtcompat::CoarseTimeProvider for SniRuntime<R> {
    fn now_coarse(&self) -> tor_rtcompat::CoarseInstant {
        self.inner.now_coarse()
    }
}

impl<R: Runtime> tor_rtcompat::NetStreamProvider for SniRuntime<R> {
    type Stream = <R as tor_rtcompat::NetStreamProvider>::Stream;
    type Listener = <R as tor_rtcompat::NetStreamProvider>::Listener;

    async fn connect(&self, addr: &std::net::SocketAddr) -> std::io::Result<Self::Stream> {
        self.inner.connect(addr).await
    }

    async fn listen(&self, addr: &std::net::SocketAddr) -> std::io::Result<Self::Listener> {
        self.inner.listen(addr).await
    }
}

impl<R: Runtime> tor_rtcompat::NetStreamProvider<std::os::unix::net::SocketAddr> for SniRuntime<R> {
    type Stream = <R as tor_rtcompat::NetStreamProvider<std::os::unix::net::SocketAddr>>::Stream;
    type Listener = <R as tor_rtcompat::NetStreamProvider<std::os::unix::net::SocketAddr>>::Listener;

    async fn connect(&self, addr: &std::os::unix::net::SocketAddr) -> std::io::Result<Self::Stream> {
        self.inner.connect(addr).await
    }

    async fn listen(&self, addr: &std::os::unix::net::SocketAddr) -> std::io::Result<Self::Listener> {
        self.inner.listen(addr).await
    }
}

impl<R: Runtime> tor_rtcompat::UdpProvider for SniRuntime<R> {
    type UdpSocket = <R as tor_rtcompat::UdpProvider>::UdpSocket;

    async fn bind(&self, addr: &std::net::SocketAddr) -> std::io::Result<Self::UdpSocket> {
        self.inner.bind(addr).await
    }
}

impl<R: Runtime> futures::task::Spawn for SniRuntime<R> {
    fn spawn_obj(&self, obj: futures::task::FutureObj<'static, ()>) -> Result<(), futures::task::SpawnError> {
        self.inner.spawn_obj(obj)
    }
}

impl<R: Runtime> tor_rtcompat::TlsProvider<<R as tor_rtcompat::NetStreamProvider>::Stream> for SniRuntime<R> {
    type Connector = <R as tor_rtcompat::TlsProvider<<R as tor_rtcompat::NetStreamProvider>::Stream>>::Connector;
    type Acceptor = <R as tor_rtcompat::TlsProvider<<R as tor_rtcompat::NetStreamProvider>::Stream>>::Acceptor;
    type TlsStream = <R as tor_rtcompat::TlsProvider<<R as tor_rtcompat::NetStreamProvider>::Stream>>::TlsStream;
    type TlsServerStream = <R as tor_rtcompat::TlsProvider<<R as tor_rtcompat::NetStreamProvider>::Stream>>::TlsServerStream;

    fn tls_connector(&self) -> Self::Connector {
        self.inner.tls_connector()
    }

    fn tls_acceptor(&self, settings: tor_rtcompat::tls::TlsAcceptorSettings) -> std::io::Result<Self::Acceptor> {
        self.inner.tls_acceptor(settings)
    }

    fn supports_keying_material_export(&self) -> bool {
        self.inner.supports_keying_material_export()
    }
}

// ===========================================================================
// SniConnector — wraps a TlsConnector, overrides SNI hostname
// ===========================================================================

use tor_rtcompat::tls::TlsConnector;

#[derive(Clone)]
pub struct SniConnector<C> {
    inner: C,
    sni_host: Arc<parking_lot::Mutex<String>>,
}

impl<S, C> TlsConnector<S> for SniConnector<C>
where
    S: tor_rtcompat::StreamOps + Send + Unpin + 'static,
    C: TlsConnector<S> + Clone + Send + Sync + Unpin + 'static,
{
    type Conn = C::Conn;

    async fn negotiate_unvalidated(
        &self,
        stream: S,
        _sni_hostname: &str,
    ) -> std::io::Result<Self::Conn> {
        let sni = self.sni_host.lock().clone();
        log::debug!("🎭 Relay TLS SNI spoofed: {} → {}", _sni_hostname, sni);
        self.inner.negotiate_unvalidated(stream, &sni).await
    }
}
