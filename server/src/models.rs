//! Response models for the miru API.
//!
//! These mirror the shapes documented in docs/API.md. Keeping them explicit
//! means the contract is validated at compile time rather than discovered by
//! the client at runtime.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChannelRef {
    pub id: Option<String>,
    pub name: Option<String>,
    pub avatar: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VideoCard {
    pub id: String,
    pub title: String,
    pub thumbnail: Option<String>,
    pub duration: Option<i64>,
    pub channel: Option<String>,
    pub channel_id: Option<String>,
    pub view_count: Option<i64>,
    pub uploaded: Option<String>,
    pub is_live: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SearchResponse {
    pub query: String,
    pub page: u32,
    pub items: Vec<VideoCard>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrendingResponse {
    pub items: Vec<VideoCard>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Chapter {
    pub title: String,
    pub start: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VideoDetail {
    pub id: String,
    pub title: String,
    pub description: String,
    pub duration: Option<i64>,
    pub channel: ChannelRef,
    pub thumbnail: Option<String>,
    pub view_count: Option<i64>,
    pub upload_date: Option<String>,
    pub age_limit: i64,
    pub is_live: bool,
    pub chapters: Vec<Chapter>,
    pub recommended: Vec<VideoCard>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StreamFormat {
    pub itag: String,
    pub url: String,
    pub ext: Option<String>,
    pub vcodec: Option<String>,
    pub acodec: Option<String>,
    pub height: Option<i64>,
    pub fps: Option<f64>,
    pub bitrate: Option<f64>,
    pub size: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StreamsResponse {
    pub id: String,
    /// "direct" (googlevideo, may be IP-locked) or "proxy" (points back here).
    pub kind: String,
    pub video: Vec<StreamFormat>,
    pub audio: Vec<StreamFormat>,
    pub combined: Vec<StreamFormat>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChannelResponse {
    pub id: String,
    pub name: String,
    pub avatar: Option<String>,
    pub banner: Option<String>,
    pub subscriber_count: Option<i64>,
    pub items: Vec<VideoCard>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HealthResponse {
    pub status: String,
    pub version: String,
    pub authenticated: bool,
}

/// A single SponsorBlock segment to skip.
///
/// `category` is one of SponsorBlock's lower-case names (`sponsor`,
/// `selfpromo`, `interaction`, `intro`, `outro`, `music_offtopic`, `preview`,
/// `filler`). The client decides which categories it honours.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SponsorSegment {
    pub category: String,
    pub start: f64,
    pub end: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SponsorBlockResponse {
    pub id: String,
    pub segments: Vec<SponsorSegment>,
}