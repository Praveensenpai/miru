# miru roadmap

Versioning: `0.0.x` for minor changes, `0.x` for major milestones.

## v1 — clean playback (in progress)

The smallest thing that is genuinely useful: a clean Material 3 client that
plays YouTube ad-free and can download for offline.

| # | Milestone | State |
|---|---|---|
| M0 | API contract (`docs/API.md`) | done |
| M1 | Rust extraction backend | done — `v0.0.1` |
| M2 | Compose shell: 5-tab nav + Home feed | done |
| M3 | Player (Media3, gesture controls) | done — verified on emulator |
| M4 | Search | done |
| M5 | Downloads (WorkManager + muxing) | done (single-file; muxing is v2) |
| M6 | Polish pass + emulator verification | done |

## v2 — the differentiators

Ordered by value-to-effort. Each is independent and can ship on its own.

1. **SponsorBlock** — fetch segments from `sponsor.ajay.app` and auto-skip.
   Done — server proxies + caches `/sponsorblock/{id}`; the player polls position
   and seeks past each segment once, with a toggle and a "Skipped sponsor" toast.
2. **Background + PiP audio** — done. `PlaybackService` now owns the ExoPlayer
   and a `MediaSession`; the UI attaches via `MediaController` and renders into a
   `SurfaceView`, so audio keeps playing with a system media notification and the
   video keeps showing in picture-in-picture when the screen is left. A custom
   `DataSource` adds the server-proxy fallback for 403'd streams below the player.
3. **Age-restricted (18+) playback** — burner cookie rotation on the server.
   Highest maintenance cost; deliberately deferred until the base is solid.
4. **Subscriptions + history** — done. Local Room only (no account). The player
   records every watch and offers a subscribe toggle on the channel row; the
   Subscriptions tab lists subscribed channels (tap to browse the channel's
   recent videos via `/channel/{id}`), and the Library tab shows watch history
   newest-first with a clear action. `MiruDatabase` moved to v2 with a real
   `1 -> 2` migration so existing downloads survive the upgrade.
5. **Return YouTube Dislike** — done. Server proxies and caches
   `/dislike/{id}`; the player shows estimated likes and dislikes under the
   title when the data is available.

## Deliberately not planned

- A custom extractor. `yt-dlp` is maintained by more people than this project
  will ever have.
- Play Store distribution. This is an open-source, GitHub-only project.
- Desktop/web clients. Android only; scope discipline is what keeps v1 alive.

## Design rules

- Five bottom-nav items, maximum.
- No nested menus, no tabs-on-tabs.
- Settings live behind a single "Advanced" section, not scattered.
- The thumbnail is the card — no borders, no chrome around content.
- Material icons only.