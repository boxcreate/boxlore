# Episode identity contract

The rules every boxlore component follows to identify an episode. Server components follow the same rules. Change them together, and never change a rule marked **fixed**.

**Status:** sections 1–3 are implemented: `EpisodeRecipeId` in `:core:model` and `scripts/sync/lib/episode-recipe-id.js` with the shared fixture, and the explicit trim in `StickyEpisodeIdentity.catalogKey`. The app's feed parsers record each item's document position, so a duplicate key keeps the first item in the document (section 3), and key GUID-less items on the first playable raw `<enclosure url>` (section 1); stored rows keep their IDs through the keys older builds used. An item with no GUID and no playable enclosure keeps a phone-only key and never takes a recipe ID. Room v39 creates the section 5 tables and, on upgrade, records an alias for every stored episode ID of a Podcast Index show ([`core/database/README.md`](../core/database/README.md)). New episodes of Podcast Index shows take their recipe ID, minted in the show's stored namespace (a namespace the server reports differently is never adopted); stored rows, and evicted rows through their trusted aliases, keep their IDs. When a show's feed is read, a new item keeps an ID that only the show's history, queue, downloads or unresolved aliases hold if its own phone mint equals it or a unique listener match points at it, so that listener state is not re-keyed, and the unresolved aliases the read can attribute become trusted. Inserts are checked against catalog rows and aliases; a reused ID that another episode holds by then falls back to the recipe ID, else a reserved local ID with an alias in the same transaction, and a healed legacy key is recorded on its alias. The section 4 collision tests run through Room with completed downloads and queue entries on both clashing episodes ([`core/rss/README.md`](../core/rss/README.md)). Recipe-range IDs no local row holds resolve through `/v2/episode`, never Podcast Index. The app keeps section 5's one listener state per episode (`EpisodeListenerStore` in `:core:database`): owners, projected reads, absolute like and completion writes, queue removals across aliases, playback from the alias that holds the download, Smart Queue, Smart Downloads and ranking counting an episode once, and the orphan rematch rules below. Switching a Podcast Index show to its RSS feed moves nothing and links the feed's episodes by alias (below); the links are written again after a restore, a re-follow and each read of the feed. Downloads are shown, started and removed across an episode's IDs, and per-show reads (show scores, Most listened, listening insights, serial next-up, auto-download limits) count the rows of the Podcast Index show an RSS show replaced as the RSS show's.

## 1. What an episode is

- An episode is **(show ID, catalog key)**. Everything else (Podcast Index episode IDs, recipe IDs, server-issued IDs, local negative IDs) is a handle that points at that pair.
- **Show ID:** the Podcast Index feed ID, an integer in `1..2^53 − 1` (`Number.MAX_SAFE_INTEGER`, so every implementation agrees on it). It is the episode namespace (`epNs`) and never changes once an ID has been issued under it.
- **Catalog key:** `trim(guid)` if non-empty, else `trim(enclosureUrl)` if non-empty, else the item has no identity and is skipped.
- A changed GUID is a new episode. No alias is made across keys without evidence.

## 2. Recipe ID (fixed)

```text
TRIM_SET  = U+0009..U+000D, U+001C..U+0020, U+00A0, U+1680, U+180E, U+2000..U+200A,
            U+2028, U+2029, U+202F, U+205F, U+3000
trim(s)   = s with leading and trailing TRIM_SET characters removed
epNs      = the show ID as a decimal string
digest    = SHA-256( UTF-8("boxlore-ep1" + "\u0000" + epNs + "\u0000" + key) )
recipeId  = 2^52 + ( first 8 bytes of digest as a big-endian unsigned 64-bit number, AND (2^52 - 1) )
range     = 4,503,599,627,370,496 .. 9,007,199,254,740,991   (all safe JS integers)
```

- `TRIM_SET` is exactly what Kotlin's `String.trim()` removes on Android (Android treats U+180E as whitespace; JDK 17 does not). JS must use the explicit set: `String.prototype.trim()` also strips U+FEFF and keeps U+001C–U+001F and U+180E.
- U+FEFF, U+0001 and U+200B are kept.
- "First 8 bytes, then the low 52 bits" is the only reading. For show 3391039 and key `https://api.spreaker.com/episode/75827097` the answer is `8825297254050678`.
- Recipe IDs are minted only for numeric show IDs in that range; implementations reject anything else. A show ID read as text must be canonical: `^[1-9][0-9]{0,15}$` in ASCII, then at most `2^53 − 1`. Signs, leading zeros, whitespace and other digit scripts are rejected, never normalized. `rss:` shows (added by URL) never get recipe IDs and keep their negative IDs.
- The tag `boxlore-ep1` is never reused for a different recipe. A new recipe gets a new tag and its own range.

**Shared fixture.** `episode-recipe-vectors.json` (18 cases, SHA-256 `e272d820972c4f9d7cd215c510bfdb7effa382ceb1982617419279189a5ae292`) is the source of truth. Every implementation has a test that matches every case and asserts the fixture's hash, so a changed copy fails CI.

| Implementation | Where |
| :--- | :--- |
| Kotlin `EpisodeRecipeId` | `:core:model` (fixture and its test at `core/rss/src/test/resources/fixtures/` and `core/rss/src/test`) |
| JS | `scripts/sync/lib/episode-recipe-id.js` |

## 3. Keys from stored data

- **Legacy keys are re-trimmed before hashing.** Room migration 31→32 keyed rows with SQLite `TRIM()`, which strips only U+0020, so a migrated key can keep a trailing newline.
- **Synthetic keys are never hashed.** Migration 31→32 gave rows it couldn't key the key `id:<episodeId>`. A row that exists when the alias tables are added is marked `legacy`; for legacy rows only, a key equal to `"id:"` plus the row's own ID is synthetic, and the row stays unresolved until its feed is read again. New rows record where their key came from.
- **Duplicate key in one feed:** the first occurrence in the raw feed document wins, so the parser must know each item's document position before it sorts.
- **SQL binding:** catalog keys are always bound as text (`turso.text()` in the catalog sync), never as integers.

## 4. Collisions

The recipe ID is a compact handle. (show, recipe ID) resolves only when exactly one key matches. Nothing is ever re-keyed, and no store overwrites a different episode.

| Store | Rule |
| :--- | :--- |
| Phone (Room) | Every episode reference from the server and every alias carries the catalog key. A new row takes the recipe ID as its local ID only if no row holds that ID for another (show, key); otherwise Room reserves a distinct local ID in the same transaction and records an alias. Inserts check before `REPLACE`. Same-show collisions never share an owner or state. |
| Keyless lookups | A bare episode ID resolves only when exactly one (show, key) holds it. |

Collision tests force clashes with a test-only hash override, both cross-show and same-show, through Room, downloads and the queue. Both episodes must survive.

## 5. Aliases on the phone

`episode_aliases(aliasId PK, podcastId, catalogKey, recipeId, state, owner, provenance, createdAt)` keeps every stored ID of an episode. `owner` marks the one ID per (show, key) that holds listener state. The key and provenance survive catalog eviction.

| State | When | Reads | Writes |
| :--- | :--- | :--- | :--- |
| trusted | The stored ID is proven to identify this (show, key). Several IDs can be trusted for one episode. | Part of the projected state | Absolute writes |
| ambiguous | Competing identities can't be told apart | Not used to resolve | Never moves state |
| unresolved | No usable key yet (synthetic, history-only, evicted) | Not used | Retried when the show's feed is read |

- **One owner per episode** holds listener state: the ID with the most recent listener activity (a play or like time), else one with a completed download, else the ID that equals its recipe ID (a served ID), else the oldest row. Listener activity decides first, so a served ID owns the episode only when no other ID holds listening. It is stored and enforced in a transaction and changes only if the owner holds no state. Only trusted IDs own. The v39 upgrade seeds a catalog ID that history, the queue or downloads hold under another show as ambiguous, so it never owns.
- **Reads use one projected state:** progress and position from the owner, or from another ID's row when that ID was played later (a pull, a restore or older-build data; the next write goes to the owner again); liked and completed from `episode_group_state` plus the aliases' rows. Each is the latest decision: a row's like decision is its like time, its completion decision its play time, and a group setting wins a tie with a row. Ranking, history, filters and dedupe count the episode once. An episode with one stored ID reads its row as it is.
- **Writes:** progress goes to the owner. Like and completion compute an absolute target from the projected state and write it to every trusted alias and the group state. A queue removal deletes the queued rows of every trusted alias except the one that is playing. Removing an episode from history removes its group state with its rows. Nothing changes ID, except the orphan rematch below.
- **Downloads are separate:** playback uses the alias that holds the completed download (its request, URI and cache key); history still goes to the owner. Every ID of an episode reads as downloaded, or downloading, when any of them holds the download. Creating a download checks every alias first; removing one covers every alias with files.
- **Orphan rematch:** after a feed read, a phone-minted ID is never moved when a catalog row of any show holds it or its alias is trusted or ambiguous. When a catalog row holds a trusted alias's (show, key) under another ID, that ID joins the episode as a trusted alias (provenance `catalog_key_match`) and nothing moves. Only phone-minted IDs with no alias or an unresolved one are rematched and moved: the like merges by the latest like decision and the progress into the episode's owner, and only a match by the exact enclosure address records the moved ID as a trusted alias (provenance `orphan_rematch`).
- **Switching a show to its RSS feed** moves nothing: every ID and listener row stays where it is, under the Podcast Index show. Each RSS episode that matches an episode the Podcast Index show's history, queue or downloads hold gets an alias under the Podcast Index show (provenance `rss_source_link`), recorded as a non-owner; ownership then follows the owner rule like any trusted ID. The link is trusted, so the RSS episode reads the episode's projected state, writes progress to its owner and plays the alias that holds the download, when:
  - the old ID's trusted key is the RSS episode's key and no other RSS episode has it. When the listener confirmed a show that only looks the same (not the same feed address or podcast GUID), one of the old ID's rows must also have the RSS episode's audio address or title, so feeds with generic GUIDs never link unrelated episodes; or
  - the old ID has no alias or an unresolved one (played before the show was followed, or a v39 listener-only or address-keyed ID), its rows name exactly one RSS episode by exact audio address, that episode has a GUID and no other old ID points at it. The old ID's alias is first settled as trusted with that key under the Podcast Index show (provenance `rss_source_audio_match`, the owner only when the episode has none).

  Any weaker match (title or date only, a key several RSS episodes share) is ambiguous. An old ID whose alias is ambiguous links nothing. Linking runs again after a restore brings the RSS show back with its link, after the RSS show is followed again and after each read of its feed: it adds links for rows that arrived since and turns an ambiguous `rss_source_link` alias trusted once exact evidence exists; it never downgrades a trusted alias or touches an alias of another provenance. A catalog read never reuses a linked RSS ID for the Podcast Index show. Writes through a linked ID keep the owner's row under its own show and episode metadata. Links exist only on the phone. A library backup carries the RSS show's link, so a restore from it links again, but without the old IDs' keys only exact audio addresses prove an episode there; cloud sync carries neither the link nor the keys yet, so a device restored from the cloud links nothing.

## 6. Validation

- Recipe fields and recipe API parameters must be safe integers in `[2^52, 2^53 − 1]`.
- Legacy episode IDs stay opaque decimal strings, including 63-bit negative RSS IDs. Never pass them through a JS `Number`.

## 7. Changing this contract

- Section 2 is fixed. Anything else changes together with the server components, with the shared fixture and its hash updated in the same change.
- A change that could move an issued ID is not allowed. Add a new handle and alias it instead.
