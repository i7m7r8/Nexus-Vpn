//! SniRuntime — wraps an Arti runtime to spoof SNI on **relay-level TLS connections**.
//!
//! NOTE: In Arti 0.40.0, the `Runtime` trait requires implementing many sub-traits
//! (Spawn, Blocking, CoarseTimeProvider, UdpProvider, Debug, NetStreamProvider for
//! both SocketAddr and UnixSocketAddr). Rather than reimplement all of these, we
//! delegate to the inner runtime for everything except TLS, where we intercept the
//! SNI hostname.
//!
//! The decoy hostname is read from a shared `Arc<Mutex<String>>` so it can be
//! updated at runtime via JNI without restarting the VPN.

use tor_rtcompat::{
    NetStreamProvider, SleepProvider, TlsProvider, Runtime, Blocking, CoarseTimeProvider,
    UdpProvider,
};
use tor_rtcompat::tls::{TlsConnector, TlsAcceptorSettings};
use std::sync::Arc;
use std::time::Duration;
use std::future::Future;
use std::io::Error as IoError;
use std::pin::Pin;
use std::net::SocketAddr;
use std::os::unix::net::SocketAddr as UnixSocketAddr;
use std::fmt;

// ===========================================================================
// SniRuntime
// ===========================================================================

#[derive(Clone)]
pub struct SniRuntime<R: Runtime> {
    inner: R,
    sni_host: Arc<parking_lot::Mutex<String>>,
}

impl<R: Runtime> fmt::Debug for SniRuntime<R> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("SniRuntime")
            .field("sni_host", &self.sni_host.lock())
            .finish_non_exhaustive()
    }
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

impl<R: Runtime> SleepProvider for SniRuntime<R> {
    type SleepFuture = R::SleepFuture;
    fn sleep(&self, duration: Duration) -> Self::SleepFuture {
        self.inner.sleep(duration)
    }
}

impl<R: Runtime> Blocking for SniRuntime<R> {
    fn spawn_blocking<F, T>(&self, f: F) -> Self::SleepFuture
    where
        F: FnOnce() -> T + Send + 'static,
        T: Send + 'static,
    {
        self.inner.spawn_blocking(f)
    }
}

impl<R: Runtime> CoarseTimeProvider for SniRuntime<R> {
    fn now(&self) -> std::time::SystemTime {
        self.inner.now()
    }
}

impl<R: Runtime> NetStreamProvider for SniRuntime<R> {
    type Stream = <R as NetStreamProvider>::Stream;
    type Listener = <R as NetStreamProvider>::Listener;

    async fn connect(&self, addr: &SocketAddr) -> std::io::Result<Self::Stream> {
        self.inner.connect(addr).await
    }

    async fn listen(&self, addr: &SocketAddr) -> std::io::Result<Self::Listener> {
        self.inner.listen(addr).await
    }
}

impl<R: Runtime> NetStreamProvider<UnixSocketAddr> for SniRuntime<R> {
    type Stream = <R as NetStreamProvider<UnixSocketAddr>>::Stream;
    type Listener = <R as NetStreamProvider<UnixSocketAddr>>::Listener;

    async fn connect(&self, addr: &UnixSocketAddr) -> std::io::Result<Self::Stream> {
        self.inner.connect(addr).await
    }

    async fn listen(&self, addr: &UnixSocketAddr) -> std::io::Result<Self::Listener> {
        self.inner.listen(addr).await
    }
}

impl<R: Runtime> UdpProvider for SniRuntime<R> {
    type UdpSocket = <R as UdpProvider>::UdpSocket;

    fn udp_socket(&self) -> std::io::Result<Self::UdpSocket> {
        self.inner.udp_socket()
    }
}

impl<R: Runtime> futures::task::Spawn for SniRuntime<R> {
    fn spawn_obj(&self, obj: futures::task::FutureObj<'static, ()>) -> Result<(), futures::task::SpawnError> {
        self.inner.spawn_obj(obj)
    }
}

impl<R: Runtime> TlsProvider<<R as NetStreamProvider>::Stream> for SniRuntime<R> {
    type Connector = SniConnector<<R as TlsProvider<<R as NetStreamProvider>::Stream>>::Connector>;
    type Acceptor = <R as TlsProvider<<R as NetStreamProvider>::Stream>>::Acceptor;
    type TlsStream = <R as TlsProvider<<R as NetStreamProvider>::Stream>>::TlsStream;
    type TlsServerStream = <R as TlsProvider<<R as NetStreamProvider>::Stream>>::TlsServerStream;

    fn tls_connector(&self) -> Self::Connector {
        SniConnector {
            inner: self.inner.tls_connector(),
            sni_host: self.sni_host.clone(),
        }
    }

    fn tls_acceptor(&self, settings: TlsAcceptorSettings) -> std::io::Result<Self::Acceptor> {
        self.inner.tls_acceptor(settings)
    }

    fn supports_keying_material_export(&self) -> bool {
        self.inner.supports_keying_material_export()
    }
}

// ===========================================================================
// SniConnector — wraps the real connector, overrides SNI hostname
// ===========================================================================

#[derive(Clone)]
pub struct SniConnector<C> {
    inner: C,
    sni_host: Arc<parking_lot::Mutex<String>>,
}

#[tor_rtcompat::async_trait]
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
