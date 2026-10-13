# kiku API contract

Base URL (v1, local): `http://10.0.2.2:8787` from the Android emulator (maps to host `127.0.0.1:8787`).

All responses are JSON. All errors use:

```json
{ "error": { "code": "string", "message": "string" } }
```

## Health

`GET /health`

```json
{ "status": "ok", "version": "0.0.1", "authenticated": false }
```

## Search

`GET /search?q={query}&page={n}`

```json
{
  "query": "lofi",
  "page": 1,
  "items": [ VideoCard ]
}
```

## Trending

`GET /trending`

```json
{ "items": [ VideoCard ] }
```

## Video detail

`GET /video/{id}`

```json
{
  "id": "dQw4w9WgXcQ",
  "title": "...",
  "description": "...",
  "duration": 213,
  "channel": { "id": "UC...", "name": "...", "avatar": "https://..." },
  "thumbnail": "https://...",
  "viewCount": 123,
  "uploadDate": "2009-10-25",
  "ageLimit": 0,
  "isLive": false,
  "chapters": [],
  "recommended": [ VideoCard ]
}
```

## Streams

`GET /streams/{id}`

Returns playable media. `kind` is `"direct"` (URL is googlevideo, may be IP-locked) or
`"proxy"` (URL points back at this server, safe fallback).

```json
{
  "id": "dQw4w9WgXcQ",
  "kind": "direct",
  "video": [
    { "itag": "401", "url": "https://...", "ext": "mp4", "vcodec": "av01", "acodec": "none", "height": 2160, "fps": 30, "bitrate": null, "size": 240334643 }
  ],
  "audio": [
    { "itag": "140", "url": "https://...", "ext": "m4a", "vcodec": "none", "acodec": "mp4a", "height": null, "fps": null, "bitrate": 128, "size": 3400000 }
  ],
  "combined": []
}
```

## Channel

`GET /channel/{id}`

```json
{
  "id": "UC...",
  "name": "...",
  "avatar": "https://...",
  "banner": "https://...",
  "subscriberCount": 123,
  "items": [ VideoCard ]
}
```

## Proxy (fallback)

`GET /proxy?url={urlencoded}`

Streams bytes with `Content-Range` passthrough. Used when a direct URL fails
(IP lock, 403). Not used by default.

## SponsorBlock

`GET /sponsorblock/{id}`

Crowd-sourced segments to auto-skip, proxied and cached server-side (1 hour).
`category` is one of `sponsor`, `selfpromo`, `interaction`, `intro`, `outro`,
`music_offtopic`, `preview`, `filler`. Only `skip`-action segments are returned;
`mute` segments are dropped. A video with no segments returns an empty list, not
an error.

```json
{
  "id": "dQw4w9WgXcQ",
  "segments": [
    { "category": "sponsor", "start": 12.5, "end": 48.0 }
  ]
}
```

## Return YouTube Dislike

`GET /dislike/{id}`

Crowd-sourced like/dislike estimates from the Return YouTube Dislike project,
proxied and cached server-side (1 hour). A video the upstream has never seen
returns zeroed counts, not an error.

```json
{
  "id": "dQw4w9WgXcQ",
  "likes": 41234,
  "dislikes": 812,
  "rating": 4.8
}
```

## Shared types

`VideoCard`

```json
{
  "id": "dQw4w9WgXcQ",
  "title": "...",
  "thumbnail": "https://...",
  "duration": 213,
  "channel": "Rick Astley",
  "channelId": "UC...",
  "viewCount": 123,
  "uploaded": "14 years ago",
  "isLive": false
}
```