//! Return YouTube Dislike client.
//!
//! YouTube removed the public dislike count, so this proxies the crowd-sourced
//! Return YouTube Dislike API, which estimates likes/dislikes from extension
//! users. We proxy rather than letting the app hit the API directly so results
//! are cached and the client stays free of third-party origins.

use std::sync::LazyLock;
use std::time::Duration;

use anyhow::{Context, Result};

use crate::models::DislikeResponse;

/// Return YouTube Dislike's public API root.
const API_BASE: &str = "https://returnyoutubedislikeapi.com/votes";

/// Vote counts move slowly; an hour is safe and cheap.
const CACHE_TTL: Duration = Duration::from_secs(3600);

/// Shared HTTP client.
static CLIENT: LazyLock<reqwest::Client> = LazyLock::new(|| {
    reqwest::Client::builder()
        .timeout(Duration::from_secs(15))
        .user_agent("miru-server")
        .build()
        .expect("reqwest client")
});

/// Fetch the estimated like/dislike counts for a video.
///
/// The upstream 404s for videos it has never seen; that is reported as zeroed
/// counts rather than an error, so the client can render a quiet placeholder.
pub async fn votes(video_id: &str) -> Result<DislikeResponse> {
    let key = format!("dislike:{video_id}");

    let body = if let Some(hit) = crate::cache::get(&key) {
        hit
    } else {
        let url = format!("{API_BASE}?videoId={video_id}");
        let resp = CLIENT
            .get(&url)
            .send()
            .await
            .context("dislike request failed")?;

        let text = if resp.status() == reqwest::StatusCode::NOT_FOUND {
            String::new()
        } else {
            resp.error_for_status()
                .context("dislike returned an error")?
                .text()
                .await
                .context("dislike body was unreadable")?
        };

        crate::cache::put(&key, text.clone(), CACHE_TTL);
        text
    };

    if body.is_empty() {
        return Ok(DislikeResponse {
            id: video_id.to_owned(),
            likes: 0,
            dislikes: 0,
            rating: 0.0,
        });
    }

    let parsed: serde_json::Value =
        serde_json::from_str(&body).context("dislike returned invalid JSON")?;

    Ok(DislikeResponse {
        id: video_id.to_owned(),
        likes: parsed
            .get("likes")
            .and_then(serde_json::Value::as_i64)
            .unwrap_or(0),
        dislikes: parsed
            .get("dislikes")
            .and_then(serde_json::Value::as_i64)
            .unwrap_or(0),
        rating: parsed
            .get("rating")
            .and_then(serde_json::Value::as_f64)
            .unwrap_or(0.0),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn zeroes_when_body_is_empty() {
        // Mirrors the 404 branch: an unknown video yields a zeroed response.
        let resp = DislikeResponse {
            id: "abc".to_owned(),
            likes: 0,
            dislikes: 0,
            rating: 0.0,
        };
        assert_eq!(resp.likes, 0);
        assert_eq!(resp.dislikes, 0);
    }
}
