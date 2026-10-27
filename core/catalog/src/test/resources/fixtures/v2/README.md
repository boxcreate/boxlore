# Recorded `/v2` discovery answers

Answers recorded from the live Worker routes on 2026-10-25 with a synthetic device ID and no
listener data, then trimmed for size: lists cut to their first items, descriptions shortened, and
briefing link signatures replaced with `sig=redacted`. Field names, types and nesting are as
recorded.

| File | Request |
|---|---|
| `recommendations.json` | `POST /v2/recommendations`: one podcast seed and one interest |
| `bootstrap-post.json` | `POST /v2/home/bootstrap` with `include: ["recommendations"]` and the US default interests |
| `bootstrap-get.json` | `GET /v2/home/bootstrap?country=us` |
| `trending.json` | `GET /v2/trending?country=us&limit=3` |
| `vibe.json` | `GET /v2/curated/vibe?id=morning_news&country=us&languages=en` |
| `curiosity.json` | `GET /v2/curated/curiosity-v3?page=1` |
| `briefing-metadata.json` | `GET /v2/briefings/metadata/us` |
| `briefing-chapters.json` | the `chaptersUrl` of that briefing (`/v2/briefings/chapters/us`) |
| `shows-feeds.json` | `POST /v2/shows/feeds` with `{"ids":[3391039,920666,9999999999,7]}`, recorded 2026-10-27: a registry move flagged for repair, a dump address, a show the API doesn't know (`fetchUrl: null`) and an http-only show read through the API (`fetchVia: proxy`) |

Two answers can't be recorded on demand, so they are written field for field in the shape the
Worker answers with (the same fields, types and nesting as `shows-feeds.json`):

| File | Answer |
|---|---|
| `shows-feeds-registry-unavailable.json` | `POST /v2/shows/feeds` while the address registry is down: `registryAvailable: false`, addresses from the dump |
| `feed-report.json` | `POST /v2/feed-report` for show 3391039: the registry's address. A live report would queue a real re-check |
