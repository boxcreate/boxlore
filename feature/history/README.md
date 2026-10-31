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
  insights/                 # A4–A6: time, listening type, top shows, clock, weekdays, month mosaic, records, genres, milestones
  story/                    # A7–A14: the monthly story, its Echo, palette, motion, share card, entry card and earlier stories
```

- Timeline (boards A1–A3): `HistoryTimelineLogic` builds months of days or shows from the whole history and the listening per day; rows play from their cover or open the episode, and their ⋮ menu goes to the show or removes the row (Undo). Jump to a day is a calendar of listened days (darker is longer).
- The Insights | Timeline switcher is the floating one Explore uses: centred 16dp above the navigation bar and mini player (`adaptivePlayerOverlayOffset`), with both tabs' lists padded to scroll past it.
- Insights (A4–A6): `InsightsLogic` / `InsightsParts` turn the period's listening into stories, not stat tiles. With fewer than five timed days, what history alone can say (A6), with a "Limited insights for now" note (when listening time began; check back after a few days of listening) only when the episode history goes back before the listening time does.
- Monthly story (A7–A12, with A14's motion and A13's Echo actions): from the 1st of a month, the month before gets a story when it has timed listening. `StoryLogic.months` lists them and `StoryLogic.build` makes one: hello (days, shows, busiest weekday, top five covers), time listened (counting up, against the month before, a bar a day with Echo on the biggest), top show (share, finishes, Resume), listening type (with the listening clock; a night card for evenings, late nights and all day, a light day card otherwise), records (best run with a pill a day, biggest day, most episodes of a show in a day) and a share card. Cards without their data are left out (`StoryLogic.cards`). The Insights tab opens last month's story from a card that says New until it is opened, older months from Earlier stories, and Share your listening (top bar) opens the latest at its share card.
  - The story is the boards' 400 × 890 frame, scaled as a whole to fit the window, with the card's colour filling around it. A card every 8 seconds; tap the left 30% for the previous, the rest for the next; hold to pause. With TalkBack on, cards wait to be moved on; with animations off, each card shows its final state and Echo its rest pose.
  - `StoryPalette` keeps the boards' colours on boxlore's own theme and turns them to any other theme's hue, each colour keeping its tone. Echo keeps its own indigo where it suits the card, deep purple on the blue time card and seafoam on the green top-show card (`EchoAccents`).
  - `StoryEcho` places Echo as the boards do (its outline plus 3% in the board's box), hops it from card to card (0.7s overshoot), then plays the card's act (a genre or time-of-day outfit) and rests with scripted eyes (`EchoIdles`).
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

- JVM tests: `HistoryTimelineLogicTest`, `TimelinePiecesTest`, `InsightsLogicTest`, `StoryLogicTest` (months, the story's facts, records, outfits, card list, palette on boxlore's and another theme, Echo placement, the time chart), `StoryEchoAssetsTest`, and `HistoryViewModelTest` (selections survive recreation, tabs work only while open, stories are New until opened, Share your listening opens the share card).
- Captures for side-by-side review with the boards: `./gradlew :app:testDebugUnitTest --tests 'cx.aswin.boxlore.ui.HistoryTabletAuditCaptureTest' -PtabletCaptures=after` writes the Timeline (days, shows), Jump to a day, empty, Insights (full and little data) and every story card to `build/reports/tablet-captures/after/` at 400dp phone, 1280 × 800 tablet and 840 × 1280 unfolded sizes.
- Text uses `historyTextStyle`: the theme's styles carry their weight in their font, so copying a style with another `fontWeight` keeps the old weight. One-line story headlines sit in a box of the board's line height (`storyLine`), since a single line keeps its natural height.
- Adaptive: the tabs share the 720dp Reading column on tablets and unfolded phones; the story frame scales to any window and keeps the card on show through resizing and rotation (`rememberSaveable`), and the open story lives in the ViewModel.

## CI relevance

- General CI pipeline (unit tests, detekt, Kover, lint).

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`core/designsystem/README.md`](../../core/designsystem/README.md) (Echo, `DiscoveryTabSwitcher`, `ShareManager`)
- [`core/playback/README.md`](../../core/playback/README.md) (the history and listening the screen reads)
