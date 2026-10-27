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

Show page routes, recorded the same way on 2026-10-27 (episode lists cut to two items, long text
shortened, nested lists cut to their first entry):

| File | Request |
|---|---|
| `episodes.json` | `GET /v2/episodes?id=920666&limit=3&offset=0&sort=newest` (`source: pi`, items with `servedId` and `catalogKey`) |
| `podcast-direct.json` | `GET /v2/podcast?id=920666` (`fetchVia: direct`) |
| `podcast-proxy.json` | `GET /v2/podcast?id=1031614` (`fetchVia: proxy` with `transportUrl`) |
| `feed-not-proxied.json` | `GET /v2/feed/920666`, a show that isn't http-only: 404 `not_proxied` with `fetchUrl` |

`POST /v2/feed-report` isn't recorded: each report queues a re-check on the live service and counts
against the phone's daily quota. Its tests use the answer shape in `V2-ROUTES.md`.
