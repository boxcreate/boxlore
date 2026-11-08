# `:feature:history`

## Purpose

Owns Listening history on the app's `library/history` route: an Insights tab (where it opens), a Timeline tab, and the monthly story with Echo. It reads the whole history and the timed listening per day through `ListeningHistoryPort` (`:core:domain`); it does not own history storage, sync, playback or listening analytics, which stay in `:core:playback`, `:core:database` and `:core:catalog`.

## Public API

- `HistoryScreen(viewModel, onBack, onEpisodeClick, onExploreClick, onPlayEpisode, onOpenShow)`: the screen with its Insights | Timeline switcher and the story dialog.
- `HistoryViewModel(port, zoneId, now, storySeen, markStorySeen)`. The app wires the port to `PlaybackRepository` and the story's seen month to `UserPreferencesRepository.seenListeningStoryStream` / `setSeenListeningStory`.
- `HistoryTab` (`INSIGHTS`, `TIMELINE`), `TimelineUiState`, `InsightsUiState` (with its `StoryShelf`) and `StoryUiState`.
- `story.StoryPlayer` / `story.StoryDialog`: the monthly story, built from a `story.MonthStory`.
- `timeline.JumpToDayContent`, `JumpToDayHandle` and `JumpToDayState`: public so review captures can draw the Jump to a day sheet's content.

## Timeline

- `HistoryTimelineLogic` builds months of days or shows from the whole history and the listening per day, with search and filters.
- Rows play from their cover or open the episode; their menu opens the show or removes the row with Undo.
- Jump to a day is a calendar of listened days. A day goes to its own place in the Timeline, else the nearest later one.
- Scroll requests (`TimelineScrollTarget`: a day, or the In progress filter) go through `followTimelineScrolls`; a newer request replaces one still waiting.

## Insights

- `InsightsLogic` and `InsightsParts` turn the selected period's listening into cards: time, listening type, top shows, the listening clock, weekdays, records, genres and milestones.
- Where you listen and New or catching up come from each day's details (`InsightsDetails`). Each shows only once half the period's listening, and at least 30 minutes of it, has the details it needs.
- New or catching up is a share of the listening whose release date is known (`InsightsDetails.newShare`); an episode without a date counts as neither new nor older.
- With few timed days, Insights falls back to what the episode history alone can say.

## Monthly story

- From the first of a month, the month before gets a story when it has timed listening. `StoryLogic.months` lists them, `StoryLogic.build` makes one, and `StoryLogic.cards` leaves out cards without their data (`StoryCard`).
- Insights opens last month's story from an entry card that says New until opened; older months come from Earlier stories; Share your listening opens the latest at its share card.
- Echo says one line a card (`StoryEchoLines`, `res/values/story_echo_lines.xml`). Special situations speak when they fit; otherwise a seed picked when the story opens chooses among the situations that fit. Facts the cards don't keep come in `StoryEchoFacts`.
- Lines fill facts through `StoryEchoLines.PLACEHOLDER`, which escapes both braces because Android's ICU regex rejects a bare `}`. A line naming a missing fact, or too long once filled, gives way to the next.
- The story is a fixed frame scaled to fit the window, with a minimum scale below which it scrolls. On a spanned foldable it keeps to one side of the hinge (`boxloreFoldSafeViewport`).
- Cards advance on a timer; tapping moves back or forward and holding pauses. With TalkBack on, cards wait to be moved on; with animations off, each card shows its final state.
- The story dialog draws under the system bars (`StoryWindowBars`), choosing bar icon colours that read on the card (`StoryWindowLogic`).
- `StoryPalette` turns the story's colours to the current theme's hue; Echo's accents come from `EchoAccents`.
- `StoryEcho` hops Echo between cards and plays each card's act; the hop and the act are separate players and `StoryEchoHandoff` swaps them so Echo never jumps in size.
- Sharing renders the share card and sends it through `ShareManager.shareImage`. The story has no sound.

## Adaptive layouts

Window gates and state retention follow [`ARCHITECTURE.md#adaptive-ui-and-state`](../../ARCHITECTURE.md#adaptive-ui-and-state). Both tabs share the Reading card column on wide windows, each tab keeps its scroll position while the other is open, the open story lives in the ViewModel, and the story frame keeps its card through resizing and rotation.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/history/
  HistoryScreen.kt, HistoryScreenEffects.kt  # screen, tab switcher, screen effects and Timeline scrolls
  HistoryViewModel.kt                        # tabs, filter, grouping, search, period, stories
  HistoryDialogs.kt, HistoryEmptyState.kt
  HistoryText.kt                             # historyTextStyle
  timeline/                                  # Timeline tab, rows, show cards, Jump to a day, fast scroller
  insights/                                  # Insights tab, logic, details, cards, wording
  story/                                     # monthly story: logic, cards, Echo lines and motion,
                                             #   palette, window, entry card
src/main/res/values/story_echo_lines.xml
```

## Dependencies

- → `:core:model`, `:core:domain`, `:core:designsystem`, `:core:analytics`.
- Libraries: Compose, Material 3, lifecycle, Material Color Utilities (story palette), Coil through `OptimizedImage`.
- Feature modules never depend on other features, and never call PostHog directly; analytics go through `:core:analytics`.

## Threading / lifecycle

- `HistoryViewModel` is scoped to the History navigation entry. It builds each tab on `Dispatchers.Default` and only while that tab is open (`whileOpen`).
- The port's whole-history reads are throttled in `:core:playback`, so playback progress does not rebuild the screen on every tick.
- A story is built once when it opens, from the history as it is then.
- Story motion runs on one clock per card that stops once the card settles; Echo's clock pauses with the story and in the background.

## Persistence & identity

- `seen_listening_story` (`:core:prefs`): the last story month opened, as `yyyy-MM`.
- Echo sequence ids read by name from `assets/echo-character/manifest.json` (`:core:designsystem`): `story-hello`, `story-hop`, `story-entry`, `story-<outfit>` and `story-<outfit>-still` (`EchoOutfit`).
- Episodes played from the history use entry point `library_history`.
- Analytics: the screen sends `library_destination_viewed`; tabs, Jump to a day, the clear confirmation, the story and each story card send `screen_viewed`; sharing sends `share_content`. Events are listed in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Testing notes

Hermetic JVM tests live in `feature/history/src/test`, using fakes from [`:core:testing`](../../core/testing/README.md).

| Area | Tests |
| :--- | :--- |
| ViewModel, tabs and stories shelf | `HistoryViewModelTest` |
| Timeline | `HistoryTimelineLogicTest`, `TimelinePiecesTest`, `TimelineScrollFollowTest` |
| Insights | `InsightsLogicTest` |
| Story content and entry card | `StoryLogicTest`, `StoryEntryLayoutTest` |
| Echo lines, assets and handoff | `StoryEchoLinesTest`, `StoryEchoAssetsTest`, `StoryEchoHandoffTest` |
| Screen in the app shell (`:app`) | `HistoryTabsStateTest`, `HistoryTimelineGroupingTest` |

Text uses `historyTextStyle`, because copying a theme style with another `fontWeight` keeps the font's own weight. `HistoryTabletAuditCaptureTest` in `:app` writes local review captures; see [`docs/TESTING.md#screenshots`](../../docs/TESTING.md#screenshots).

```bash
./gradlew :feature:history:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs History JVM tests in the features shard and includes the module in merged coverage verification; the `:app` History tests run in the app shard.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`:feature:library` README](../library/README.md)
- [`:core:designsystem` README](../../core/designsystem/README.md)
- [`:core:playback` README](../../core/playback/README.md)
