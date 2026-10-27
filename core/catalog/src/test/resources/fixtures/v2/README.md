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

The `/v2/shows/feeds` answers are not live recordings: they were written on 2026-10-27 from the
route's documented contract and its own tests, field for field, with example addresses.

| File | Request |
|---|---|
| `shows-feeds.json` | `POST /v2/shows/feeds` with four IDs: a confirmed move (`registry:` source, `flags.addressRepair`), an http-only show (`fetchVia: proxy` with its `transportUrl`), a show the lookup doesn't know (`fetchUrl: null`) and one with another `epNs` |
| `shows-feeds-registry-down.json` | the same route while the address registry is unavailable (`registryAvailable: false`, dump addresses only) |
