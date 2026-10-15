# `:feature:briefing`

## Purpose

Owns the Daily Briefing presentation flow: region-aware briefing content, story pager UI, transcript and chapter presentation, and playback actions delegated to injected core dependencies. It does not own catalog retrieval, queue persistence, playback services, or navigation registration.

## Public API

- Related episode artwork retains show-art alternatives through the shared loader. Brief cover and player artwork are bundled resources, with no image network request.

- Briefing cards and source rows stay still with shape-clipped Material feedback. Story play and retry controls use the shared restrained control response; native buttons keep their own indications and playback callbacks.

- `BriefingScreen` is the route-level composable used by `:app`.
- Section headers in briefing UI use `rememberSectionHeaderFontFamily()` from `:core:designsystem` so lettering roundness follows Appearance settings.
- Briefing emphasis uses centralized Google Sans Flex weight tokens from `:core:designsystem`.
- `BriefingViewModel` exposes briefing state and actions using injected catalog, playback, queue, and preference dependencies. It resolves the saved default edition before fetching; explicit route regions win. Temporary edition changes never write the default. Selecting a default in Brief preferences persists it and opens that edition. Retrying the same failed edition starts a new request; cancelled older requests cannot publish stale results.
- Brief playback hands the shared `briefingShowPodcast` show from `:core:designsystem` to the queue, and saved sessions use the same masthead title, so the player and Android Auto show "The boxlore brief" by boxlore.
- Daily edition uses a crisp edge-to-edge weekday cover behind the status bar, clipped at its lower corners. The masthead stays in the reserved left area with a soft local dark scrim for contrast; Back and Brief preferences sit at the upper right. Below the cover, the page uses the listener’s solid Material 3 background, with opaque themed story and reporting panels. No artwork wash, image fade or glass layer continues behind the article. System icons stay white over the cover and follow the selected theme after scrolling past it or leaving the page. Headline, metadata, edition selection, playback, story paging, related episodes and sources retain their behavior. Player artwork remains the fixed branded regional square.
- Briefing audio start tags `entry_point=briefing` on the play source bundle (glossary value) so
  `playback_*` can attribute Brief listening without changing `daily_briefing_action`.
- Playback uses a thin 3 dp wavy seek track with a small round handle and remaining time on the same row. The wave moves only during playback; native Slider tap, drag, RTL and accessibility seeking remain available with a full touch target. Unknown duration hides the seek row.
- `BriefingUiState` models screen state.
- `BriefingIdentity` contains pure identity helpers for tests and UI mapping.
- Episode id stays `briefing_{region}_{date}` for session continuity; Media3 audio cache keys include the signed URL `v=` hash (see `EpisodeMediaCacheKey`) so same-day regenerations do not replay stale bytes.
- Briefing markets: `us`, `in`, `gb`, `global` (ISO `gb` for the UK; legacy API path `/uk` still aliases to `gb` on the Worker).
- `BriefingPlaybackLogic` resolves whether a briefing interaction pauses, resumes, starts, or seeks; choosing another story seeks directly instead of pausing.
- `BriefingStoriesPager` and `BriefingStoryComponents` provide extracted story-presentation pieces.
- Story cards share one height: the pager measures every card at the page width and current text size and takes the tallest, capped at 70% of the screen height so a card stays a comfortable read. Stories longer than the cap scroll inside their text area, and the last line fades while more text is below; shorter stories keep related episodes and Play pinned to the bottom. In a window shorter than 480dp (a phone on its side, a split screen) the whole card scrolls instead (`storyCardScrollsAsOne`), so the heading, story, related episodes and Play all stay reachable. Measured copies are never placed and are hidden from accessibility. Story text uses body-large type in the full on-surface colour.
- While an edition loads, the page shows its own layout (`BriefingLoadingContent`): the bundled cover for the chosen edition at once, with shimmering placeholders for the date line, headline, Listen button and first story in their final places, so the edition settles in place rather than replacing a spinner. The status bar treats the loading cover like the edition's.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/briefing/
  BriefingIdentity.kt
  BriefingScreen.kt
  BriefingStoriesPager.kt
  BriefingStoryComponents.kt
  BriefingPlaybackLogic.kt
  BriefingUiState.kt
  BriefingViewModel.kt
  BriefingLoader.kt
```

## Dependencies

- Project dependencies: `:core:designsystem`, `:core:catalog`, `:core:playback`, `:core:model`, `:core:network`, `:core:analytics`, and `:core:prefs`.
- Libraries: Compose, Navigation, Coil, lifecycle ViewModel/runtime Compose, Palette, and Material icons.
- Reverse-edge rule: feature modules must not depend on other feature modules or construct playback/catalog graphs.

## Threading / lifecycle

- `BriefingViewModel` is scoped to the route or navigation owner that creates it.
- Catalog, playback, and queue objects are application-scoped instances supplied by app wiring.
- Compose UI runs on the main thread; repository calls use suspend APIs from injected dependencies.

## Persistence & identity

- This module owns no storage files or stable keys.
- Briefing episode and podcast identity comes from catalog/network models.
- Playback session identity is owned by `:core:playback`.

## Testing notes

- Tablet editions and loading covers share a centered 720dp reading frame, including page actions and cover-height/status-bar tracking. Home owns its separately adapted horizontal Brief card; story, source, edition and playback callbacks remain unchanged.

- Tablet story pages use a bounded 720dp reading width, exposing additional pages in wider viewports. Height measurement uses that same page width; loading and reporting panels share the cap. Phone paging and story content stay unchanged.

- Unit tests live under `feature/briefing/src/test`.
- `BriefingViewModelLoadingTest` exercises the ViewModel's production `BriefingLoader` with suspend fakes: saved/explicit/follow-app initialization, delayed preferences versus user selection, retries, missing metadata, chapter failures, progress restoration, and noncooperative cancelled metadata/chapter responses. The loader remains route-scope owned and constructs no repositories or media services.
- `BriefingScreenTest` checks phone-sized cover/logo/navigation geometry, light/dark status-bar transitions and restoration, enlarged RTL controls, playback, story navigation and sources. Set `BOXLORE_UI_TEST_ARTIFACTS` to an existing local folder to export native JVM renders for visual inspection.
- `BriefingIdentityTest` covers region aliases and briefing identity helpers.
- `BriefingShowTest` (Robolectric) checks the shared Brief show title, author, id and player square against real resources.
- `BriefingScreenTest` uses native Robolectric graphics for playback, story paging, source-sheet actions, default-edition selection and error recovery at phone width, including dark mode, RTL and doubled text size. It uses JVM tests without emulator or device automation.
- Manual smoke should verify briefing launch, story paging, accurate duration metadata once available, and switching stories without interrupting playback.

```bash
./gradlew :feature:briefing:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` compiles and runs briefing JVM tests with the project suite.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:playback` README](../../core/playback/README.md)
- [`:app` README](../../app/README.md)

Tablet story height follows the supplied content pane, and edition/source sheets use the shared expanded dialog adapter. Phone configuration tiers and source actions are preserved.
