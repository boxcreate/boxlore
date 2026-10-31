# `:feature:history`

## Purpose

Listening history: an Insights tab (it opens on it), a Timeline tab, and the monthly story with Echo. It reads the whole history and every phone's timed listening through `ListeningHistoryPort`; it does not own storage, sync, playback or the history's analytics, which stay in `:core:playback`, `:core:database` and `:core:catalog`.

## Public API

- `HistoryScreen(viewModel, onBack, onEpisodeClick, onExploreClick, onPlayEpisode, onOpenShow)` with `HistoryViewModel(port, zoneId, now, storySeen, markStorySeen)`. The app wires the port to `PlaybackRepository` and the story's seen month to `UserPreferencesRepository.seenListeningStoryStream` / `setSeenListeningStory`.
- `HistoryTab` (`INSIGHTS`, `TIMELINE`), `TimelineUiState`, `InsightsUiState` (with its `StoryShelf`) and `StoryUiState`.
- `story.StoryPlayer` / `story.StoryDialog`: the monthly story, built from a `story.MonthStory` (`story.StoryLogic.build`).
- `timeline.JumpToDayContent` / `JumpToDayHandle` / `JumpToDayState`, public so captures can draw the sheet's content (modal windows don't draw offline).

## Internal structure

```text
src/main/java/.../history/
  HistoryScreen.kt          # large collapsing title, Insights | Timeline switcher, the story dialog
  HistoryViewModel.kt       # tabs, filter, grouping, search, period, stories; each tab works only while open
  HistoryText.kt            # historyTextStyle: Google Sans at a real weight (see Testing notes)
  timeline/                 # A1–A3: search, Jump back in, filters, days or shows, Jump to a day, fast scroller
  insights/                 # A4–A6: time, listening type, top shows, clock, where you listen, weekdays, month mosaic, records, genres, new or catching up, milestones
  story/                    # A7–A14: the monthly story, its Echo, palette, motion, share card, entry card and earlier stories
```

- Each tab keeps its place while the other is open: Insights' list position and Earlier stories' folding are held by the screen, as Timeline's are (`HistoryTabsStateTest`).
- Timeline (boards A1–A3): `HistoryTimelineLogic` builds months of days or shows from the whole history and the listening per day; rows play from their cover or open the episode, and their ⋮ menu goes to the show or removes the row (Undo). Jump to a day is a calendar of listened days (darker is longer) that scrolls in short windows; a day goes to its own day in the Timeline, else the nearest later one (an episode heard that day shows on the day it was last played).
- The Insights | Timeline switcher is the floating one Explore uses: centred 16dp above the navigation bar and mini player (`adaptivePlayerOverlayOffset`), with both tabs' lists padded to scroll past it.
- Insights (A4–A6): `InsightsLogic` / `InsightsParts` turn the period's listening into stories, not stat tiles. Two cards come from each day's details (`InsightsDetails`, `InsightsDetailCards`): Where you listen (time by output: headphones, Bluetooth, car, phone speaker, Cast) after When you listen, and New or catching up (episodes out a week or less, and what Lore and Smart queue started) after the genres. Listening from before details were recorded has none, so both show only once half the period's listening, and at least 30 minutes of it, was recorded with details (that time, not the whole of a day that has some: the time on a known output, or more where a known release date, new releases, Lore or Smart queue account for more); Where you listen's shares are of the time on a known output, Lore's and Smart queue's of the time with details. New or catching up is a share of the time whose episode release date is known (`ListeningDayEpisode.releaseKnownMs`, kept in summaries since Room v42; `InsightsDetails.newShare`): an episode without a date is neither new nor older, and a summary from before v42 counts as neither. Its card shows only once half the period's listening, and at least 30 minutes of it, has a known release date (`ListeningSources.newShare` is null until then). When you listen says "Mostly evenings" only with half the listening or more, "Evenings lead" from 35% (`InsightsWording.partLead`), else "All through the day". A longest sitting is left out of the records when a day whose sitting isn't known (summed by an older build) holds more listening than it, so could hide a longer one (`InsightsParts.longestSitting`). With fewer than five timed days, what history alone can say (A6), with a "Limited insights for now" note (when listening time began; check back after a few days of listening) only when the episode history goes back before the listening time does.
- Monthly story (A7–A12, refreshed on the S0–S8 boards on 2026-10-31, with A14's motion and A13's Echo actions): from the 1st of a month, the month before gets a story when it has timed listening. `StoryLogic.months` lists them and `StoryLogic.build` makes one: hello (the month's name as large as fits, then listened time, days, episodes and finished episodes counting up, and the top three covers), time listened (counting up, against the month before as two bars, a bar a day with Echo on the biggest and that day's chip on the side away from it), top show (share and time counting up, finishes, Resume, and the next two shows), how you listened (time by output, new or catching up, and what Lore and Smart queue started; only when the month has those details, and new or catching up only once half the month's listening has a known release date), listening type (with the listening clock and how many episodes were finished; only with half an hour timed by part of the day, and finisher or sampler only across three episodes or more; the share card names the type only when the story has this card), records (best run counting up against the best ever, biggest day, longest sitting, most episodes of a show in a day, busiest weekday) and a share card. Cards without their data are left out (`StoryLogic.cards`). The Insights tab opens last month's story from a card that says New until it is opened (its card count, total, top show, covers and a small Echo), older months from Earlier stories (two rows of three, then Show all N stories / Show fewer stories, kept through rotation and resizing), and Share your listening (top bar) opens the latest at its share card.
  - Echo says one line a card (`StoryEchoLines`, `res/values/story_echo_lines.xml`, 81 situations of twelve lines). A special situation speaks whenever it fits (the first month on record, a month after a gap, a single episode or day, a tie at the top, a tie on the listening clock, a new or tied best run, December and January wraps); otherwise the seed picked when the story opens chooses among the situations that fit (more or less than the month before, by how much, more or fewer days, the top show's genre (`lineGenre`, stricter than Echo's outfits: an education show isn't science, a books show isn't fiction), a dominant show or a wide mix, the top three's genres, a genre change, the output with most of the listening, a close mix of outputs, new or catching up, Lore, Smart queue, the time of day, finishing or sampling, runs against last month and the best, sittings, a binge, the busiest weekday), and then one of its lines. Facts the cards' builders don't keep come with the story (`StoryEchoFacts`: the month before's days, run and biggest day, the best run before the month, ties, genres, how much of the month has details). Lines fill in facts (`{show}`, `{diff}`, `{run}`…); a line naming a fact the month lacks, a count under two, or running past 70 characters once filled gives way to the next, then to the card's usual lines.
  - The story is the boards' 400 × 890 frame, scaled as a whole to fit the window, with the card's colour filling around it. It never shrinks below 0.8 (its buttons stay near touch size): in a short window (a phone on its side) it scrolls instead. On a spanned foldable it keeps to one side of the hinge (`boxloreFoldSafeViewport`). Its type is artwork and doesn't follow the font scale. A card every 8 seconds; tap the left 30% for the previous, the rest for the next; hold to pause (letting go of a hold resumes the same card). With TalkBack on, cards wait to be moved on, following TalkBack being turned on or off mid-story; with animations off, each card shows its final state and Echo its rest pose.
  - Echo on the story entry card and the Earlier stories tiles is a still: drawn once, with no frame clock; each row of tiles is its own list item.
  - `StoryPalette` keeps the boards' colours on boxlore's own theme and turns them to any other theme's hue, each colour keeping its tone. Echo keeps its own indigo where it suits the card, deep purple on the blue time card and seafoam on the green top-show card (`EchoAccents`).
  - `StoryEcho` places Echo as the boards do (its outline plus 3% in the board's box, smaller than before so the facts lead), hops it from card to card (the hop clip plays whole, 1.7s, and Echo moves only while airborne), then plays the card's act (a genre or time-of-day outfit) and rests with scripted eyes (`EchoIdles`). Its line pops in 2s into the card, after the facts have counted up.
  - Share renders the share card as drawn (Echo included) and sends it through `ShareManager.shareImage`.
- The story ships silent: no music or sound button.

## Dependencies

- → `:core:model`, `:core:domain`, `:core:designsystem`, `:core:analytics`
- Material Color Utilities (story palette turns), Coil (artwork through `OptimizedImage`).
- No feature → feature edges; nothing here touches PostHog directly.

## Threading / lifecycle

- `HistoryViewModel` builds each tab off the main thread (`Dispatchers.Default`) and only while that tab is open (`whileOpen`); `PlaybackHistoryStore` throttles whole-history reads to one a second, so playback ticks don't rebuild the screen.
- A story is built once when it opens, from the history as it is then.
- Story motion runs on one clock per card that stops once the card has settled; Echo's own clock pauses with the story and in the background.

## Persistence & identity

- `seen_listening_story` (`UserPreferencesRepository`, `:core:prefs`): the last story month opened, as `yyyy-MM`.
- Echo sequence ids called by name from `assets/echo-character/manifest.json` (`:core:designsystem`): `story-hello`, `story-hop`, `story-entry`, `story-<outfit>` and `story-<outfit>-still` (`EchoOutfit`); `StoryEchoAssetsTest` checks they ship.
- Episode playback from the history uses entry point `library_history`.

## Testing notes

- JVM tests: `HistoryTimelineLogicTest`, `TimelinePiecesTest`, `InsightsLogicTest` (including when Where you listen and New or catching up show, and their shares: new of only the time with a known release date, undated episodes and summaries from before v42 counted as neither), `StoryLogicTest` (months, Earlier stories folding to two rows, the story's facts, records, runners-up, finishes, outputs, new or catching up left out without known release dates, the entry card's teaser, outfits, card list, palette on boxlore's and another theme, Echo placement, the time chart), `StoryEchoLinesTest` (special situations first, the seed choosing among the rest, time changes large, small and level, genres and ties, new and tied records, lines giving way, and every situation twelve short lines with known placeholders and usual lines naming only facts every month has), `StoryEchoAssetsTest`, and `HistoryViewModelTest` (selections survive recreation, tabs work only while open, stories are New until opened, Share your listening opens the share card).
- Captures for side-by-side review with the boards: `./gradlew :app:testDebugUnitTest --tests 'cx.aswin.boxlore.ui.HistoryTabletAuditCaptureTest' -PtabletCaptures=after` writes the Timeline (days, shows), Jump to a day, empty, Insights (full and little data) and every story card to `build/reports/tablet-captures/after/` at 400dp phone, 1280 × 800 tablet and 840 × 1280 unfolded sizes.
- Text uses `historyTextStyle`: the theme's styles carry their weight in their font, so copying a style with another `fontWeight` keeps the old weight. One-line story headlines sit in a box of the board's line height (`storyLine`), since a single line keeps its natural height.
- Adaptive: the tabs share the 720dp Reading column on tablets and unfolded phones; the story frame scales to any window and keeps the card on show through resizing and rotation (`rememberSaveable`), and the open story lives in the ViewModel.

## CI relevance

- General CI pipeline (unit tests, detekt, Kover, lint).

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`core/designsystem/README.md`](../../core/designsystem/README.md) (Echo, `DiscoveryTabSwitcher`, `ShareManager`)
- [`core/playback/README.md`](../../core/playback/README.md) (the history and listening the screen reads)
