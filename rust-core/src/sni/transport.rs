//! SniRuntime — a thin wrapper around an Arti runtime.
//!
//! Currently a passthrough: SNI rewriting is done in the main loop
//! by the `SniInterceptor` which inspects TCP payloads directly.
//! This wrapper exists only to satisfy Arti's `Runtime` trait bounds.

use tor_rtcompat::{NetStreamProvider, NetStreamListener, SleepProvider, TlsProvider};
use std::net::SocketAddr;
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

    pub fn inner(&self) -> &R {
        &self.inner
    }
}

impl<R: tor_rtcompat::Runtime> SleepProvider for SniRuntime<R> {
    type SleepFuture = R::SleepFuture;
    fn sleep(&self, duration: Duration) -> Self::SleepFuture {
        self.inner.sleep(duration)
    }
}

impl<R: tor_rtcompat::Runtime> NetStreamProvider for SniRuntime<R> {
    type Stream = <R as NetStreamProvider>::Stream;
    type Listener = <R as NetStreamProvider>::Listener;

    async fn connect(&self, addr: &SocketAddr) -> std::io::Result<Self::Stream> {
        self.inner.connect(addr).await
    }

    async fn listen(&self, addr: &SocketAddr) -> std::io::Result<Self::Listener> {
        self.inner.listen(addr).await
    }
}

impl<R: tor_rtcompat::Runtime> TlsProvider<<R as NetStreamProvider>::Stream> for SniRuntime<R> {
    type Connector = <R as TlsProvider<<R as NetStreamProvider>::Stream>>::Connector;
    type Acceptor = <R as TlsProvider<<R as NetStreamProvider>::Stream>>::Acceptor;
    type TlsStream = <R as TlsProvider<<R as NetStreamProvider>::Stream>>::TlsStream;
    type TlsServerStream = <R as TlsProvider<<R as NetStreamProvider>::Stream>>::TlsServerStream;

    fn tls_connector(&self) -> Self::Connector {
        self.inner.tls_connector()
    }

    fn tls_acceptor(&self) -> Self::Acceptor {
        self.inner.tls_acceptor()
    }

    fn supports_keying_material_export(&self) -> bool {
        self.inner.supports_keying_material_export()
    }
}
