//! SniRuntime — placeholder for future relay-level SNI spoofing.
//!
//! In Arti 0.40.0, the `Runtime` trait requires implementing many sub-traits
//! with complex async lifetime signatures that cannot be reliably wrapped.
//!
//! SNI spoofing is handled at the **app level** by `SniInterceptor` instead,
//! which inspects and rewrites TLS ClientHello packets before they go to Tor.
//!
//! This module is kept as a stub for future compatibility.

use tor_rtcompat::Runtime;
use std::sync::Arc;

/// Stub type — not currently used. Use the inner `R` directly as the Arti runtime.
#[derive(Clone, Debug)]
pub struct SniRuntime<R: Runtime> {
    inner: R,
    _sni_host: Arc<parking_lot::Mutex<String>>,
}

impl<R: Runtime> SniRuntime<R> {
    pub fn new(inner: R, sni_host: Arc<parking_lot::Mutex<String>>) -> Self {
        Self { inner, _sni_host: sni_host }
    }

    /// Returns the inner runtime — use this directly with Arti.
    pub fn inner(&self) -> &R {
        &self.inner
    }

    pub fn sni_host(&self) -> String {
        self._sni_host.lock().clone()
    }
}

/// Stub connector — not currently used.
#[derive(Clone)]
pub struct SniConnector<C> {
    inner: C,
    _sni_host: Arc<parking_lot::Mutex<String>>,
}
