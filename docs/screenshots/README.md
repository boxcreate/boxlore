# Screenshot baselines

Tablet/foldable review captures use the same fixed data/artwork as the phone matrix with `-PtabletCaptures=before|after`. They live under `build/reports/tablet-captures/`; the preserved first before pass is also under local `output/tablet-layouts/before/`. They never replace phone goldens. Synthetic tabletop bounds are 410–431dp in the 673×841dp window.

Robolectric and Roborazzi provide local visual checks. No screenshot job is added to CI.

## Phone matrix

The local `*PhoneGoldenTest` suites cover eleven screen/state entries: Home loaded/loading, Explore, Lore, Library, podcast info, episode info, Standard full player, mini player over Home, Echo's opening question and Settings. Every entry runs at 360×800, 412×915 and 800×360dp, in light and dark: 66 PNGs under `screenshots/baselines/phone/`.

The shared `PhoneGoldenRule` pins SDK 33/native graphics, mdpi, US English, window dimensions, orientation, night mode and the capture clock. Fixtures disable dynamic color and use fixed in-memory artwork through Coil; no image request leaves the process. This avoids the intentionally random decorative shapes in missing-art fallbacks. No backend or media service is started. ViewModel-bound screens receive mocked flows without constructing an Application-backed ViewModel. These are composable regression captures, not verification of live services, navigation gestures or a physical device.

Ordinary test/CI tasks exclude these classes. Use JDK 17 and the normal Gradle home, from the repository root:

```bash
GRADLE_USER_HOME="$HOME/.gradle" ./gradlew :app:testDebugUnitTest :feature:explore:testDebugUnitTest :feature:info:testDebugUnitTest :feature:player:testDebugUnitTest :feature:onboarding:testDebugUnitTest :feature:settings:testDebugUnitTest -PphoneGoldens=verify --tests '*PhoneGoldenTest'
```

For the initial capture only, replace `verify` with `record`. Recording refuses to overwrite an existing phone baseline. Never update a phone baseline to accommodate a layout change.

Roborazzi renders actuals into `build/reports/phone-goldens/actual/`. Verification then compares dimensions and every ARGB pixel, with zero tolerance; it never writes the baseline. A mismatch fails with the actual render's path. Keep the same JDK, operating system, pinned SDK and dependency graph when comparing; a renderer change is a separate investigation, not permission to refresh the baseline.

## Existing Settings captures

`SettingsGoldenRoborazziTest` lives in `:feature:settings`. Its three older, fixed-light captures are `add_rss_feed_dialog.png`, `reset_analytics_dialog.png` and `downloads_settings_page.png` directly under `screenshots/baselines/`.

```bash
./gradlew :feature:settings:recordRoborazziDebug
./gradlew :feature:settings:verifyRoborazziDebug
```

These component captures use the existing Roborazzi comparison policy. They are separate from the immutable, exact-pixel phone matrix. Prefer tagged nodes for dialogs because an AlertDialog can produce multiple roots.

## See also

- [Testing](../TESTING.md)
- [Shared test support](../../core/testing/README.md)
- [Settings](../../feature/settings/README.md)
