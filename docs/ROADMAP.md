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

## v0.3.0–v0.3.5 — serverless (current)

Replaced the Rust backend with on-device InnerTube extraction. No server to
deploy, no IP to get blocked, no account.

- **Single InnerTube profile** (`v0.3.5`) — `ANDROID_VR 1.65.10` for both catalog
  and player, with a one-time `visitorData` that clears the bot gate. Replaced
  the `IOS` player client, whose URLs now need a PO token and returned HTTP 403 on
  every range.
- **Bounded-range streaming** — a custom `DataSource` requests bounded windows and
  stitches them into one stream. googlevideo rejects oversized/open-ended ranges
  and its `rqh=1` URLs stop serving a rendition after a while, so a refused read
  past the first window re-mints a fresh URL for the *same* rendition and resumes
  at the same offset; playback survives a URL expiring mid-video.
- **Audio selection** — original-language, DRC-free, AAC/mp4a preferred.
- **Settings/About screen** — a real `You` tab with a persisted quality ceiling.
- **Bounded refresh budget** (`v0.3.2`+) — a re-open of the *same* load keeps its
  remaining re-mint budget instead of resetting it, so a throttled endpoint is
  not hammered by the player's retry loop.
- **Visible playback errors** (`v0.3.4`) — a fatal stream failure shows a
  "Couldn't play this video" overlay with the error code and a Retry that
  re-extracts fresh URLs, instead of a silent black frame.

## v2 — the differentiators

1. **SponsorBlock** — done. Hash-prefix lookup against `sponsor.ajay.app`; the
   player auto-skips each segment once with a toggle and a toast.
2. **Background + PiP audio** — done. `PlaybackService` owns the ExoPlayer and a
   `MediaSession`; the UI attaches via `MediaController`.
3. **Age-restricted (18+) playback** — deferred. Needs burner-cookie handling.
4. **Subscriptions + history** — done. Local Room only (no account).
5. **Return YouTube Dislike** — done. Queried directly from the community API.

## Known issue — audio served only as a ~1 MB prefix (environmental)

On the current test host, googlevideo serves adaptive **audio** URLs
(itags 139/140/251) only as a prefix of roughly the first **1 MB**: a ranged
read whose offset is under ~1 MB returns 206 with real bytes, and any offset at
or past ~1 MB returns 403. This is deterministic — 12 freshly-minted URLs all
capped at the same point, swapping to the other `mn` node did not help, and
`yt-dlp` reproduces it (`HTTP Error 403`). Adaptive **video** URLs (itags
137/136/135/134) are *not* capped: they serve real bytes at offsets past 16 MB.
The muxed itag 18 is refused even at offset 0.

Because the cap is a prefix anchored at byte 0, re-minting a URL for the same
rendition (Miru's refresh) cannot resume past it — every fresh URL carries the
same ~1 MB prefix. This is a server-side per-format limit on this egress IP,
not a client-selection or window-logic bug.

The robust fix is **SABR** (`streamingData.serverAbrStreamingUrl`), the
segment-based streaming path official clients use; the endpoint answers here
(HTTP 200, `sabr.malformed_config` for an invalid UMP body), so it is reachable
even though direct-URL audio is capped. Implementing the UMP protocol is the
next milestone. Until then, re-test full long-form playback from a network whose
IP is not capped.

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
