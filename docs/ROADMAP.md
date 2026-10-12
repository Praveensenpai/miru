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
2. **Background + PiP audio** — the `PlaybackService` is already wired; this is
   mostly notification and lifecycle polish.
3. **Age-restricted (18+) playback** — burner cookie rotation on the server.
   Highest maintenance cost; deliberately deferred until the base is solid.
4. **Subscriptions + history** — local Room first, optional server sync later.
5. **Return YouTube Dislike** — a single extra API call.

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