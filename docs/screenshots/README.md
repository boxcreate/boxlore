# Screenshot baselines (P26)

Lightweight visual-regression scaffolding. **Not required in CI.**

## Chosen approach: manual Compose capture (no Roborazzi/Papyrus)

To avoid heavy new Gradle plugins, Boxlore keeps screenshot baselines as checked-in PNG files under `screenshots/baselines/` and captures them locally from a focused Compose `androidTest` (or device screenshot).

Why not Roborazzi/Papyrus yet:

- Extra plugins, JVM screenshot engines, and CI golden management are out of scope for this scaffolding pass.
- `:feature:home` already hosts Compose `androidTest` with stable `testTag`s — enough to anchor a capture step later.

## Layout

```
screenshots/baselines/     # PNG goldens (gitkeep until first capture)
docs/screenshots/          # this guide
```

Suggested naming:

```
screenshots/baselines/add_rss_feed_dialog.png
screenshots/baselines/home_settings_entry.png
```

## Capture (local)

1. Install a debug build on an emulator/device with a fixed density (e.g. Pixel 6 API 34, xxhdpi).
2. Either:
   - Run the optional stub (ignored by default):
     ```bash
     ./gradlew :feature:home:connectedDebugAndroidTest \
       -Pandroid.testInstrumentationRunnerArguments.notAnnotation=org.junit.Ignore
     ```
     then enable `AddRssFeedDialogScreenshotStubTest` temporarily and write PNGs via
     `composeRule.onRoot().captureToImage()` (or Android Studio device screenshot), **or**
   - Capture manually from the running app at the same UI state.
3. Drop PNGs into `screenshots/baselines/` and commit when intentional.

## Compare

Until a tool is adopted:

- Diff PNGs in PR review, or
- Use `git diff` / an image diff viewer locally.

When adopting a library later, prefer **Roborazzi** (JVM, Compose-friendly) over Papyrus unless Papyrus is already required elsewhere. Keep goldens in `screenshots/baselines/` so paths stay stable.

## Gradle stub

No screenshot Gradle task is wired yet (keeps CI green). A future task could look like:

```kotlin
// root or :feature:home — not enabled
// tasks.register("updateScreenshotBaselines") {
//     dependsOn(":feature:home:connectedDebugAndroidTest")
//     // copy captured PNGs → screenshots/baselines/
// }
```

## Related

- Compose UI tests: `docs/TESTING.md` → androidTest
- Stub test: `feature/home/.../AddRssFeedDialogScreenshotStubTest.kt` (`@Ignore`)
