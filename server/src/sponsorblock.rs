//! SponsorBlock client.
//!
//! SponsorBlock exposes crowd-sourced segments (sponsor reads, intros, outros,
//! self-promos…) that a player can skip automatically. Its legacy
//! `?videoID=` endpoint is gone; the only supported lookup is by a *hash
//! prefix*: the first four hex characters of `sha256(videoID)`. The server
//! returns every video whose hash shares that prefix, so we filter to the one
//! we asked about.
//!
//! We proxy this rather than letting the app talk to SponsorBlock directly, so
//! results are cached and the client stays simple.

use std::sync::LazyLock;
use std::time::Duration;

use anyhow::{Context, Result};
use sha2::{Digest, Sha256};

use crate::models::{SponsorBlockResponse, SponsorSegment};

/// SponsorBlock's public API root.
const API_BASE: &str = "https://sponsor.ajay.app/api/skipSegments";

/// SponsorBlock data changes slowly; an hour is safe and cheap.
const CACHE_TTL: Duration = Duration::from_secs(3600);

/// Shared HTTP client.
static CLIENT: LazyLock<reqwest::Client> = LazyLock::new(|| {
    reqwest::Client::builder()
        .timeout(Duration::from_secs(15))
        .user_agent("miru-server")
        .build()
        .expect("reqwest client")
});

/// First four hex characters of `sha256(video_id)`.
fn hash_prefix(video_id: &str) -> String {
    let digest = Sha256::digest(video_id.as_bytes());
    let hex = format!("{digest:x}");
    hex[..4].to_owned()
}

/// The `segments` array for one entry, mapped to our own model.
///
/// Only `actionType == "skip"` segments are returned; "mute" segments are
/// deliberately dropped since v1 has no volume ramping.
fn segments_of(entry: &serde_json::Value) -> Vec<SponsorSegment> {
    entry
        .get("segments")
        .and_then(|s| s.as_array())
        .map(|arr| {
            arr.iter()
                .filter(|s| {
                    s.get("actionType")
                        .and_then(|a| a.as_str())
                        .unwrap_or("skip")
                        == "skip"
                })
                .filter_map(|s| {
                    let segment = s.get("segment")?.as_array()?;
                    let start = segment.first()?.as_f64()?;
                    let end = segment.get(1)?.as_f64()?;
                    Some(SponsorSegment {
                        category: s
                            .get("category")
                            .and_then(|c| c.as_str())
                            .unwrap_or("sponsor")
                            .to_owned(),
                        start,
                        end,
                    })
                })
                .collect()
        })
        .unwrap_or_default()
}

/// Fetch SponsorBlock segments for a video.
///
/// Returns an empty list when the video has no segments (the API 404s for an
/// unknown prefix) — that is a normal outcome, not an error.
pub async fn segments(video_id: &str) -> Result<SponsorBlockResponse> {
    let prefix = hash_prefix(video_id);
    let key = format!("sponsorblock:{prefix}");

    let body = if let Some(hit) = crate::cache::get(&key) {
        hit
    } else {
        let url = format!("{API_BASE}/{prefix}");
        let resp = CLIENT
            .get(&url)
            .send()
            .await
            .context("sponsorblock request failed")?;

        // 404 means no video with this hash prefix has segments. Cache the
        // empty result too, so we do not hammer the API for unsponsored videos.
        let text = if resp.status() == reqwest::StatusCode::NOT_FOUND {
            "[]".to_owned()
        } else {
            resp.error_for_status()
                .context("sponsorblock returned an error")?
                .text()
                .await
                .context("sponsorblock body was unreadable")?
        };

        crate::cache::put(&key, text.clone(), CACHE_TTL);
        text
    };

    let parsed: serde_json::Value =
        serde_json::from_str(&body).context("sponsorblock returned invalid JSON")?;

    let segments = parsed
        .as_array()
        .and_then(|entries| {
            entries.iter().find(|e| {
                e.get("videoID").and_then(|v| v.as_str()) == Some(video_id)
            })
        })
        .map(segments_of)
        .unwrap_or_default();

    Ok(SponsorBlockResponse {
        id: video_id.to_owned(),
        segments,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hash_prefix_matches_sponsorblock_example() {
        // SponsorBlock's own docs example: dQw4w9WgXcQ -> 5f6b.
        assert_eq!(hash_prefix("dQw4w9WgXcQ"), "5f6b");
    }

    #[test]
    fn filters_to_requested_video_and_skip_action() {
        let entry = serde_json::json!({
            "videoID": "abc",
            "segments": [
                { "category": "sponsor", "actionType": "skip", "segment": [1.0, 5.5] },
                { "category": "music_offtopic", "actionType": "mute", "segment": [6.0, 9.0] }
            ]
        });
        let segs = segments_of(&entry);
        assert_eq!(segs.len(), 1);
        assert_eq!(segs[0].category, "sponsor");
        assert!((segs[0].start - 1.0).abs() < f64::EPSILON);
        assert!((segs[0].end - 5.5).abs() < f64::EPSILON);
    }
}