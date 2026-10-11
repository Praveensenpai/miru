//! yt-dlp process wrapper.
//!
//! Extraction is delegated to the system `yt-dlp` binary. It is the most
//! actively maintained YouTube extractor in existence, so when YouTube breaks
//! its player, a `yt-dlp` version bump is usually all that is required — no
//! app update, no server rewrite.

use std::process::Stdio;
use std::time::Duration;

use anyhow::{bail, Context, Result};
use tokio::process::Command;

use crate::models::{
    ChannelRef, ChannelResponse, SearchResponse, StreamFormat, StreamsResponse, TrendingResponse,
    VideoCard, VideoDetail,
};

/// How long an extraction result stays cached.
///
/// Stream URLs expire after a few hours, so this must stay comfortably below
/// that. Ten minutes is long enough that navigating away and back is instant,
/// and short enough that a video's metadata never goes meaningfully stale.
const CACHE_TTL: Duration = Duration::from_secs(600);

/// Extractor args passed to yt-dlp on every invocation.
///
/// `android_vr` currently slips past several client-side gates that the web
/// client enforces; `web` is kept as a fallback client.
const PLAYER_CLIENTS: &str = "android_vr,web";

/// Run `yt-dlp -J` (single JSON dump) against a target and return parsed JSON.
///
/// Results are cached for [`CACHE_TTL`]. Extraction is slow enough (tens of
/// seconds for a cold search) that caching is the difference between a usable
/// app and one that spins for a minute on every launch.
async fn dump_json(target: &str, extra: &[&str]) -> Result<serde_json::Value> {
    // The cache key must capture every input that changes the output.
    let key = format!("{target}|{}", extra.join(","));
    if let Some(hit) = crate::cache::get(&key) {
        if let Ok(value) = serde_json::from_str(&hit) {
            tracing::debug!(%target, "cache hit");
            return Ok(value);
        }
    }

    let mut cmd = Command::new("yt-dlp");
    cmd.arg("-J")
        .arg("--no-warnings")
        .arg("--no-playlist")
        .arg("--extractor-args")
        .arg(format!("youtube:player_client={PLAYER_CLIENTS}"))
        .args(extra)
        .arg(target)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());

    let output = cmd
        .output()
        .await
        .context("failed to spawn yt-dlp — is it installed and on PATH?")?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        bail!("yt-dlp failed: {}", stderr.trim());
    }

    let value: serde_json::Value =
        serde_json::from_slice(&output.stdout).context("yt-dlp returned invalid JSON")?;

    // Only successful extractions are cached, so a transient failure does not
    // get pinned for the whole TTL.
    if let Ok(encoded) = serde_json::to_string(&value) {
        crate::cache::put(&key, encoded, CACHE_TTL);
    }

    Ok(value)
}

/// Pick the best thumbnail URL from a yt-dlp JSON object.
fn thumbnail_of(v: &serde_json::Value) -> Option<String> {
    v.get("thumbnail")
        .and_then(|t| t.as_str())
        .map(str::to_owned)
}

/// Build a `VideoCard` from a flat yt-dlp JSON entry.
fn card_from(v: &serde_json::Value) -> Option<VideoCard> {
    let id = v.get("id")?.as_str()?.to_owned();
    let title = v.get("title")?.as_str()?.to_owned();
    Some(VideoCard {
        id,
        title,
        thumbnail: thumbnail_of(v),
        duration: v.get("duration").and_then(serde_json::Value::as_i64),
        channel: v
            .get("channel")
            .or_else(|| v.get("uploader"))
            .and_then(|c| c.as_str())
            .map(str::to_owned),
        channel_id: v
            .get("channel_id")
            .and_then(|c| c.as_str())
            .map(str::to_owned),
        view_count: v.get("view_count").and_then(serde_json::Value::as_i64),
        uploaded: v
            .get("upload_date")
            .and_then(|c| c.as_str())
            .map(str::to_owned),
        is_live: v
            .get("is_live")
            .and_then(serde_json::Value::as_bool)
            .unwrap_or(false),
    })
}

/// Extract video cards from a yt-dlp playlist/search result.
fn cards_from_entries(v: &serde_json::Value) -> Vec<VideoCard> {
    v.get("entries")
        .and_then(|e| e.as_array())
        .map(|entries| entries.iter().filter_map(card_from).collect())
        .unwrap_or_default()
}

/// `ytsearchN:<query>` — search YouTube and return matching cards.
pub async fn search(query: &str, page: u32) -> Result<SearchResponse> {
    // yt-dlp search is 1-indexed and returns a flat list; page size 20.
    let count = 20;
    let target = format!("ytsearch{count}:{query}");
    let v = dump_json(&target, &[]).await?;
    // yt-dlp ignores offset for ytsearch; page is surfaced for API symmetry.
    Ok(SearchResponse {
        query: query.to_owned(),
        page,
        items: cards_from_entries(&v),
    })
}

/// Trending feed.
///
/// YouTube's `/feed/trending` page redirects unauthenticated clients to the
/// home page, so yt-dlp cannot extract it without cookies. We first try the
/// real feed (in case authenticated cookies are configured later) and fall
/// back to a discovery search so the Home tab is never empty.
pub async fn trending() -> Result<TrendingResponse> {
    if let Ok(v) = dump_json("https://www.youtube.com/feed/trending", &[]).await {
        let items = cards_from_entries(&v);
        if !items.is_empty() {
            return Ok(TrendingResponse { items });
        }
    }

    // Fallback: discovery via search keeps Home useful without auth.
    let v = dump_json("ytsearch20:trending this week", &[]).await?;
    Ok(TrendingResponse {
        items: cards_from_entries(&v),
    })
}

/// Full metadata for a single video, including chapters and related items.
pub async fn video_detail(id: &str) -> Result<VideoDetail> {
    let url = format!("https://www.youtube.com/watch?v={id}");
    let v = dump_json(&url, &[]).await?;

    let chapters = v
        .get("chapters")
        .and_then(|c| c.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|c| {
                    Some(crate::models::Chapter {
                        title: c.get("title")?.as_str()?.to_owned(),
                        start: c.get("start_time")?.as_f64()?,
                    })
                })
                .collect()
        })
        .unwrap_or_default();

    let channel = ChannelRef {
        id: v
            .get("channel_id")
            .and_then(|c| c.as_str())
            .map(str::to_owned),
        name: v
            .get("channel")
            .or_else(|| v.get("uploader"))
            .and_then(|c| c.as_str())
            .map(str::to_owned),
        avatar: None,
    };

    Ok(VideoDetail {
        id: id.to_owned(),
        title: v
            .get("title")
            .and_then(|t| t.as_str())
            .unwrap_or_default()
            .to_owned(),
        description: v
            .get("description")
            .and_then(|t| t.as_str())
            .unwrap_or_default()
            .to_owned(),
        duration: v.get("duration").and_then(serde_json::Value::as_i64),
        channel,
        thumbnail: thumbnail_of(&v),
        view_count: v.get("view_count").and_then(serde_json::Value::as_i64),
        upload_date: v
            .get("upload_date")
            .and_then(|c| c.as_str())
            .map(str::to_owned),
        age_limit: v
            .get("age_limit")
            .and_then(serde_json::Value::as_i64)
            .unwrap_or(0),
        is_live: v
            .get("is_live")
            .and_then(serde_json::Value::as_bool)
            .unwrap_or(false),
        chapters,
        recommended: vec![],
    })
}

/// Classify a yt-dlp format entry into video-only, audio-only, or combined.
fn format_of(f: &serde_json::Value) -> Option<StreamFormat> {
    let url = f.get("url")?.as_str()?.to_owned();
    let vcodec = f
        .get("vcodec")
        .and_then(|c| c.as_str())
        .unwrap_or("none")
        .to_owned();
    let acodec = f
        .get("acodec")
        .and_then(|c| c.as_str())
        .unwrap_or("none")
        .to_owned();
    Some(StreamFormat {
        itag: f
            .get("format_id")
            .and_then(|c| c.as_str())
            .unwrap_or_default()
            .to_owned(),
        url,
        ext: f.get("ext").and_then(|c| c.as_str()).map(str::to_owned),
        vcodec: Some(vcodec),
        acodec: Some(acodec),
        height: f.get("height").and_then(serde_json::Value::as_i64),
        fps: f.get("fps").and_then(serde_json::Value::as_f64),
        bitrate: f.get("tbr").and_then(serde_json::Value::as_f64),
        size: f
            .get("filesize")
            .or_else(|| f.get("filesize_approx"))
            .and_then(serde_json::Value::as_i64),
    })
}

/// Resolve playable streams for a video, split by kind.
///
/// Returns `kind: "direct"` — URLs are handed to the client as-is. The proxy
/// path is a fallback the client can request per-format later.
pub async fn streams(id: &str) -> Result<StreamsResponse> {
    let url = format!("https://www.youtube.com/watch?v={id}");
    let v = dump_json(&url, &[]).await?;

    let formats = v
        .get("formats")
        .and_then(|f| f.as_array())
        .cloned()
        .unwrap_or_default();

    let mut video = Vec::new();
    let mut audio = Vec::new();
    let mut combined = Vec::new();

    for f in &formats {
        let Some(fmt) = format_of(f) else { continue };
        let has_video = fmt.vcodec.as_deref().unwrap_or("none") != "none";
        let has_audio = fmt.acodec.as_deref().unwrap_or("none") != "none";
        match (has_video, has_audio) {
            (true, true) => combined.push(fmt),
            (true, false) => video.push(fmt),
            (false, true) => audio.push(fmt),
            _ => {}
        }
    }

    // Highest resolution first for video; highest bitrate first for audio.
    video.sort_by(|a, b| b.height.cmp(&a.height));
    audio.sort_by(|a, b| {
        b.bitrate
            .partial_cmp(&a.bitrate)
            .unwrap_or(std::cmp::Ordering::Equal)
    });

    Ok(StreamsResponse {
        id: id.to_owned(),
        kind: "direct".to_owned(),
        video,
        audio,
        combined,
    })
}

/// Channel metadata plus recent uploads.
///
/// Capped at 30 entries: yt-dlp otherwise walks the entire channel backlog,
/// which can take minutes on large channels and block the request.
pub async fn channel(id: &str) -> Result<ChannelResponse> {
    let url = format!("https://www.youtube.com/channel/{id}/videos");
    let v = dump_json(&url, &["--playlist-end", "30"]).await?;
    Ok(ChannelResponse {
        id: id.to_owned(),
        name: v
            .get("channel")
            .or_else(|| v.get("title"))
            .and_then(|c| c.as_str())
            .unwrap_or_default()
            .to_owned(),
        avatar: None,
        banner: None,
        subscriber_count: v
            .get("channel_follower_count")
            .and_then(serde_json::Value::as_i64),
        items: cards_from_entries(&v),
    })
}