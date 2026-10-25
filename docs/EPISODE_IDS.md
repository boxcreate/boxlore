# Episode identity contract

The rules every boxlore component follows to identify an episode. Server components follow the same rules. Change them together, and never change a rule marked **fixed**.

**Status:** sections 1–3 are implemented: `EpisodeRecipeId` in `:core:model` and `scripts/sync/lib/episode-recipe-id.js` with the shared fixture, and the explicit trim in `StickyEpisodeIdentity.catalogKey`. The app's feed parsers record each item's document position, so a duplicate key keeps the first item in the document (section 3), and key GUID-less items on the raw `<enclosure url>` (section 1); stored rows keep their IDs through the keys older builds used. Sections 4 and 5 bind the app code that stores or resolves recipe IDs once it adopts them.

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

`episode_aliases(aliasId PK, podcastId, catalogKey, recipeId, state, owner, provenance)` keeps every stored ID of an episode. The key and provenance survive catalog eviction.

| State | When | Reads | Writes |
| :--- | :--- | :--- | :--- |
| trusted | The stored ID is proven to identify this (show, key). Several IDs can be trusted for one episode. | Part of the projected state | Absolute writes |
| ambiguous | Competing identities can't be told apart | Not used to resolve | Never moves state |
| unresolved | No usable key yet (synthetic, history-only, evicted) | Not used | Retried when the show's feed is read |

- **One owner per episode** holds listener state: the ID with the most recent listener activity, else one with a completed download, else the oldest row. It is stored and enforced in a transaction and changes only if the owner holds no state.
- **Reads use one projected state:** progress and position from the owner; liked and completed from `episode_group_state` plus the aliases' rows. Ranking, history, filters and dedupe count the episode once.
- **Writes:** progress goes to the owner. Like and completion compute an absolute target from the projected state and write it to every trusted alias and the group state. A queue removal deletes every trusted alias's rows. Nothing changes ID.
- **Downloads are separate:** playback uses the alias that holds the completed download (its request, URI and cache key); history still goes to the owner. Creating a download checks every alias first; removing one covers every alias with files.

## 6. Validation

- Recipe fields and recipe API parameters must be safe integers in `[2^52, 2^53 − 1]`.
- Legacy episode IDs stay opaque decimal strings, including 63-bit negative RSS IDs. Never pass them through a JS `Number`.

## 7. Changing this contract

- Section 2 is fixed. Anything else changes together with the server components, with the shared fixture and its hash updated in the same change.
- A change that could move an issued ID is not allowed. Add a new handle and alias it instead.
