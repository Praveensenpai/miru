//! miru — extraction backend for the miru YouTube client.
//!
//! See docs/API.md for the contract this server implements.

mod cache;
mod dislike;
mod extractor;
mod models;
mod sponsorblock;

use std::time::Duration;

use axum::{
    body::Body,
    extract::{Query, State},
    http::{header, HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    routing::get,
    Json, Router,
};
use futures::TryStreamExt;
use models::HealthResponse;
use serde::Deserialize;
use serde_json::json;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Shared application state. Currently stateless; kept explicit so caches and
/// cookie jars can be added without reshaping every handler.
#[derive(Clone)]
struct AppState;

/// Standard JSON error envelope: `{ "error": { "code", "message" } }`.
fn error_response(status: StatusCode, code: &str, message: impl Into<String>) -> Response {
    let body = json!({ "error": { "code": code, "message": message.into() } });
    (status, Json(body)).into_response()
}

/// Map any backend failure to a 502 with the underlying message.
fn backend_error(err: anyhow::Error) -> Response {
    tracing::warn!(%err, "extraction failed");
    error_response(StatusCode::BAD_GATEWAY, "extraction_failed", err.to_string())
}

#[derive(Deserialize)]
struct SearchParams {
    q: String,
    #[serde(default = "default_page")]
    page: u32,
}

fn default_page() -> u32 {
    1
}

#[derive(Deserialize)]
struct ProxyParams {
    url: String,
}

async fn health() -> Json<HealthResponse> {
    Json(HealthResponse {
        status: "ok".to_owned(),
        version: VERSION.to_owned(),
        authenticated: false,
    })
}

async fn search(Query(params): Query<SearchParams>) -> Response {
    match extractor::search(&params.q, params.page).await {
        Ok(resp) => Json(resp).into_response(),
        Err(err) => backend_error(err),
    }
}

async fn trending() -> Response {
    match extractor::trending().await {
        Ok(resp) => Json(resp).into_response(),
        Err(err) => backend_error(err),
    }
}

async fn video(axum::extract::Path(id): axum::extract::Path<String>) -> Response {
    match extractor::video_detail(&id).await {
        Ok(resp) => Json(resp).into_response(),
        Err(err) => backend_error(err),
    }
}

async fn streams(axum::extract::Path(id): axum::extract::Path<String>) -> Response {
    match extractor::streams(&id).await {
        Ok(resp) => Json(resp).into_response(),
        Err(err) => backend_error(err),
    }
}

async fn channel(axum::extract::Path(id): axum::extract::Path<String>) -> Response {
    match extractor::channel(&id).await {
        Ok(resp) => Json(resp).into_response(),
        Err(err) => backend_error(err),
    }
}

/// SponsorBlock segments for a video, filtered to just that video.
async fn sponsorblock(axum::extract::Path(id): axum::extract::Path<String>) -> Response {
    match sponsorblock::segments(&id).await {
        Ok(resp) => Json(resp).into_response(),
        Err(err) => backend_error(err),
    }
}

/// Estimated like/dislike counts for a video.
async fn dislike(axum::extract::Path(id): axum::extract::Path<String>) -> Response {
    match dislike::votes(&id).await {
        Ok(resp) => Json(resp).into_response(),
        Err(err) => backend_error(err),
    }
}

/// Stream bytes from an upstream googlevideo URL back to the client.
///
/// Used only as a fallback when a direct URL is IP-locked or returns 403.
async fn proxy(
    State(_state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<ProxyParams>,
) -> Response {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .expect("reqwest client");

    let mut req = client.get(&params.url);
    // Forward Range so seeking works through the proxy.
    if let Some(range) = headers.get(header::RANGE) {
        req = req.header(header::RANGE, range);
    }

    let upstream = match req.send().await {
        Ok(r) => r,
        Err(err) => {
            return error_response(
                StatusCode::BAD_GATEWAY,
                "proxy_failed",
                err.to_string(),
            )
        }
    };

    let status = upstream.status();
    let mut builder = Response::builder().status(status);
    for name in [header::CONTENT_TYPE, header::CONTENT_LENGTH, header::CONTENT_RANGE] {
        if let Some(value) = upstream.headers().get(&name) {
            builder = builder.header(name, value);
        }
    }

    let stream = upstream
        .bytes_stream()
        .map_err(std::io::Error::other);

    builder
        .body(Body::from_stream(stream))
        .unwrap_or_else(|err| error_response(StatusCode::INTERNAL_SERVER_ERROR, "proxy_error", err.to_string()))
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::registry()
        .with(EnvFilter::try_from_default_env().unwrap_or_else(|_| "miru_server=info".into()))
        .with(tracing_subscriber::fmt::layer())
        .init();

    let app = Router::new()
        .route("/health", get(health))
        .route("/search", get(search))
        .route("/trending", get(trending))
        .route("/video/{id}", get(video))
        .route("/streams/{id}", get(streams))
        .route("/channel/{id}", get(channel))
        .route("/sponsorblock/{id}", get(sponsorblock))
        .route("/dislike/{id}", get(dislike))
        .route("/proxy", get(proxy))
        .with_state(AppState);

    let host = std::env::var("MIRU_HOST").unwrap_or_else(|_| "127.0.0.1".to_owned());
    let port = std::env::var("MIRU_PORT").unwrap_or_else(|_| "8787".to_owned());
    let addr = format!("{host}:{port}");

    let listener = tokio::net::TcpListener::bind(&addr).await?;
    tracing::info!("miru server v{VERSION} listening on http://{addr}");
    axum::serve(listener, app).await?;
    Ok(())
}