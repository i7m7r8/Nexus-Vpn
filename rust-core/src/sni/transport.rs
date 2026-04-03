//! SniRuntime — wraps an Arti runtime to spoof SNI on **relay-level TLS connections**.
//!
//! This is the core Invisible Pro feature: when Arti connects to a Tor guard relay,
//! the TLS handshake uses the decoy hostname (e.g. `t.me`) as the SNI instead of
//! the relay's real address. To a network observer, the TLS traffic looks like
//! a normal connection to the decoy site.
//!
//! The decoy hostname is read from a shared `Arc<Mutex<String>>` so it can be
//! updated at runtime via JNI without restarting the VPN.

use tor_rtcompat::{
    NetStreamProvider, NetStreamListener, SleepProvider, TlsProvider, TlsConnector,
};
use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;
use std::future::Future;
use std::io::Error as IoError;

// ===========================================================================
// SniRuntime
// ===========================================================================

#[derive(Clone)]
pub struct SniRuntime<R: tor_rtcompat::Runtime> {
    inner: R,
    sni_host: Arc<parking_lot::Mutex<String>>,
}

impl<R: tor_rtcompat::Runtime> SniRuntime<R> {
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

    fn tls_acceptor(&self) -> Self::Acceptor {
        self.inner.tls_acceptor()
    }

    fn supports_keying_material_export(&self) -> bool {
        self.inner.supports_keying_material_export()
    }
}

// ===========================================================================
// SniConnector — wraps the real connector, overrides SNI hostname
// ===========================================================================

/// Wraps a `TlsConnector` and replaces the `sni_hostname` parameter in
/// `negotiate_unvalidated()` with the decoy hostname.
///
/// When Arti connects to a guard relay at `185.220.101.1:443`, it calls:
///   connector.negotiate_unvalidated(stream, "185.220.101.1")
///
/// Our wrapper changes this to:
///   inner.negotiate_unvalidated(stream, "t.me")
///
/// The TLS handshake then sends SNI = "t.me" instead of the relay's IP.
/// The decoy hostname is read from a shared Arc<Mutex<String>> so it updates at runtime.
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

    fn negotiate_unvalidated<'a>(
        &'a self,
        stream: S,
        _sni_hostname: &'a str,
    ) -> Pin<Box<dyn Future<Output = Result<Self::Conn, IoError>> + Send + 'a>> {
        let sni = self.sni_host.lock().clone();
        let inner = self.inner.clone();

        Box::pin(async move {
            log::debug!("🎭 Relay TLS SNI spoofed: {} → {}", _sni_hostname, sni);
            inner.negotiate_unvalidated(stream, &sni).await
        })
    }
}
