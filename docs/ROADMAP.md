# miru roadmap

Versioning: `0.0.x` for minor changes, `0.x` for major milestones.

## v1 — clean playback (done)

The smallest thing that is genuinely useful: a clean Material 3 client that
plays YouTube ad-free and can download for offline.

| # | Milestone | State |
|---|---|---|
| M0 | API contract | superseded by serverless architecture |
| M1 | Rust extraction backend | removed in `v0.3.0` |
| M7 | Release pipeline (CI + tags) | done — `v0.2.0` |
| M2 | Compose shell: 5-tab nav + Home feed | done |
| M3 | Player (Media3, gesture controls) | done — verified on emulator |
| M4 | Search | done |
| M5 | Downloads (WorkManager + muxing) | done (single-file; muxing is v2) |
| M6 | Polish pass + emulator verification | done |

## v0.3.0 — serverless (current)

Replaced the Rust backend with on-device InnerTube extraction. No server to
deploy, no IP to get blocked, no account.

- **Dual InnerTube profiles** — `ANDROID_VR` for catalog, `IOS` for the player
  (plain un-ciphered URLs up to 2160p, no JS runtime needed).
- **Bounded-range streaming** — a custom `DataSource` requests bounded windows and
  stitches them into one stream. googlevideo rejects oversized/open-ended ranges
  and its `rqh=1` URLs stop serving a rendition after a while, so a refused read
  past the first window re-mints a fresh URL for the *same* rendition and resumes
  at the same offset; playback survives a URL expiring mid-video.
- **Audio selection** — original-language, DRC-free, AAC/mp4a preferred.
- **Settings/About screen** — a real `You` tab with a persisted quality ceiling.

## v2 — the differentiators

1. **SponsorBlock** — done. Hash-prefix lookup against `sponsor.ajay.app`; the
   player auto-skips each segment once with a toggle and a toast.
2. **Background + PiP audio** — done. `PlaybackService` owns the ExoPlayer and a
   `MediaSession`; the UI attaches via `MediaController`.
3. **Age-restricted (18+) playback** — deferred. Needs burner-cookie handling.
4. **Subscriptions + history** — done. Local Room only (no account).
5. **Return YouTube Dislike** — done. Queried directly from the community API.

## Deliberately not planned

- A server component. Extraction is on-device by design.
- Play Store distribution. Open-source, GitHub-only project.
- Desktop/web clients. Android only.

## Design rules

- Five bottom-nav items, maximum.
- No nested menus, no tabs-on-tabs.
- Settings live in one place, not scattered.
- The thumbnail is the card — no borders, no chrome around content.
- Material icons only.
