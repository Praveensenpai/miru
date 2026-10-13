# miru (見る)

A clean, ad-free YouTube client for Android with a self-hosted extraction backend.

- **App:** Kotlin + Jetpack Compose (Material 3)
- **Backend:** Rust (axum) wrapping `yt-dlp`
- **License:** MIT

## Why

Most open-source YouTube clients (NewPipe, PipePipe) are powerful but dense and
dated. LibreTube proved a clean Material 3 client is possible. `miru` aims for
LibreTube's polish with a server-backed extractor so breakages are fixed
server-side without shipping a new app build.

## v1 scope

| Feature | Status |
|---|---|
| Clean Compose Material 3 UI | done |
| Ad-free playback | done |
| Search | done |
| Downloads | done (single-file; track muxing is v2) |
| SponsorBlock | done |
| Return YouTube Dislike | done |
| Background / PiP audio | done |
| Subscriptions + history | done (local; sync is later) |
| Age-restricted (18+) content | v2 (deferred) |

Bottom navigation: Home · Subs · Library · Downloads · You

## Architecture

```
Android app (Compose M3, Media3, Room, WorkManager, Coil)
        |  clean JSON over HTTP
Rust server (axum + yt-dlp subprocess + optional stream proxy)
```

The Android emulator reaches a locally-running server at `http://10.0.2.2:8787`.

See [`docs/API.md`](docs/API.md) for the API contract.

## Running the server

Requires `yt-dlp` on `PATH` and a Rust toolchain.

```sh
cd server
cargo run
# listens on http://127.0.0.1:8787
```

Override host/port with `MIRU_HOST` and `MIRU_PORT`.

Verify:

```sh
curl http://127.0.0.1:8787/health
curl 'http://127.0.0.1:8787/search?q=lofi'
```

## Building the app

Requires Android SDK 34 and JDK 17.

```sh
cd android
./gradlew assembleDebug
```

## Legal

`miru` is an unofficial client and is not affiliated with YouTube. It is
provided for personal, educational use. Streaming and downloading content may
violate YouTube's Terms of Service; you are responsible for how you use it.
