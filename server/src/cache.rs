//! Tiny in-process TTL cache.
//!
//! Extraction is expensive — a cold `ytsearch` can take 30-50 seconds because
//! yt-dlp has to resolve player metadata. The Home feed is requested on every
//! app launch, so without caching the user stares at a spinner for most of a
//! minute each time.
//!
//! This is deliberately the simplest thing that works: a process-wide map
//! guarded by a mutex, with per-entry expiry. It is not an LRU and it has no
//! size bound, but the key space (video ids, search terms) is small and entries
//! expire, so neither matters at v1 scale. A real cache (Redis or moka) is a
//! later concern.

use std::collections::HashMap;
use std::sync::LazyLock;
use std::sync::Mutex;
use std::time::{Duration, Instant};

struct Entry {
    value: String,
    expires_at: Instant,
}

static CACHE: LazyLock<Mutex<HashMap<String, Entry>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

/// Return a cached value if present and unexpired. Expired entries are evicted
/// on read so the map does not grow without bound.
pub fn get(key: &str) -> Option<String> {
    let mut map = CACHE.lock().ok()?;
    match map.get(key) {
        Some(entry) if entry.expires_at > Instant::now() => Some(entry.value.clone()),
        Some(_) => {
            map.remove(key);
            None
        }
        None => None,
    }
}

/// Store a value under `key` for `ttl`.
pub fn put(key: &str, value: String, ttl: Duration) {
    if let Ok(mut map) = CACHE.lock() {
        map.insert(
            key.to_owned(),
            Entry {
                value,
                expires_at: Instant::now() + ttl,
            },
        );
    }
}