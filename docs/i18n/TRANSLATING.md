# Translating boxlore

boxlore's own text ships in English (`res/values/`) and in Spanish (`values-es`), Brazilian Portuguese (`values-pt-rBR`), German (`values-de`) and French (`values-fr`). Translations are drafted with AI help, checked by the tests below and reviewed by native speakers. Listeners can pick the language in **Settings › Appearance › App language**.

## What gets translated

- Everything boxlore itself writes: screens, buttons, dialogs, empty and error states, screen-reader labels, notifications and their channel names, widgets, Android Auto and the watch.
- Not translated: server text (Echo's onboarding replies, briefings, curiosity cards, announcements, support replies, release notes), show and episode names and descriptions, debug-only screens, log messages, analytics values, preference keys and IDs.
- The Privacy page stays in English for now: its strings are `translatable="false"` with a comment. Translating it needs the maintainer's approval of the exact wording, one language at a time.

## Glossary

Product names stay in English in every language. Feature names that describe what they do are translated with the agreed words.

| English | Kind | es | pt-BR | de | fr |
| :--- | :--- | :--- | :--- | :--- | :--- |
| boxlore | brand: always lowercase, never translated, even at the start of a sentence | boxlore | boxlore | boxlore | boxlore |
| Echo | name of the guide | Echo | Echo | Echo | Echo |
| Lore | name of the Lore tab | Lore | Lore | Lore | Lore |
| Showcase | name of the branded show pages | Showcase | Showcase | Showcase | Showcase |
| LeaveMeAlone | name of the page that turns recommendations off | LeaveMeAlone | LeaveMeAlone | LeaveMeAlone | LeaveMeAlone |
| Smart Queue | translated | Cola inteligente | Fila inteligente | Smarte Warteschlange | File d’attente intelligente |
| Up next | translated | A continuación | A seguir | Als Nächstes | À suivre |
| Daily mix | translated | Mezcla diaria | Mix diário | Täglicher Mix | Mix du jour |
| New Episodes | translated | Episodios nuevos | Episódios novos | Neue Folgen | Nouveaux épisodes |
| show (a podcast) | translated | podcast | podcast | Podcast | podcast |
| episode | translated | episodio | episódio | Folge | épisode |
| subscribe / follow | translated | seguir | seguir | abonnieren | s’abonner |
| queue | translated | cola | fila | Warteschlange | file d’attente |
| download | translated | descargar | baixar | herunterladen | télécharger |
| library | translated | biblioteca | biblioteca | Bibliothek | bibliothèque |

Add a row here before a new product or feature name appears in more than one string.

## Voice and formality

- The voice is a curious but calm companion: short, plain, warm, never pushy.
- Spanish uses **tú**, Brazilian Portuguese **você**, German **du** (lowercase), French **vous**.
- Keep the English meaning and the action a button takes; don't add or drop information.
- German and French run about 30% longer than English. Prefer the shorter natural phrasing when a comment gives a length limit.

## Echo story lines

The monthly Echo story lines (`feature/history`, `story_echo_lines.xml`) are transcreation, not translation: keep the joke or the pun's spirit in the target language, and keep each line at **70 characters or fewer**. If a pun doesn't survive, write a new one about the same moment.

## Placeholders and plurals

- Keep every placeholder exactly: `%1$s`, `%2$d`, `%.1f` and named tokens such as `{month}`. Reorder them freely; never add, drop or retype one.
- Write whole sentences. English strings are whole sentences with placeholders, never fragments joined in code.
- Counts are `<plurals>`. Spanish, Portuguese and French need `one` and `other` and may add `many` (used for large round numbers such as a million); German needs `one` and `other`. A `one` form may leave out the number ("Eine Folge").
- Dates, times, durations and numbers are formatted by the app in the listener's language; never put a date pattern or unit abbreviation in a string.
- `%%` is a literal percent sign.

## For developers

- Compose text uses `stringResource` / `pluralStringResource`. Code outside composition returns `UiText` (`:core:model`, `cx.aswin.boxlore.core.model.text`): `UiText.res(id, args…)`, `UiText.plural(id, count, args…)`, or `UiText.raw(text)` for server text and names. Resolve it with `asString()` in Compose (`:core:designsystem`) or `resolve(context)` elsewhere.
- Dates, times, durations, relative times and numbers go through `LocaleFormats` with the screen's locale (`currentLocale()` in Compose, `context.displayLocale()` elsewhere). Upper- and lower-casing display text passes that locale too.
- Resource names are snake_case with a module or screen prefix (`library_playlists_empty_title`). Add a short `<!-- comment -->` for anything ambiguous or length-limited.
- Strings that act as keys (genre IDs, analytics values, stored values) never change; map them to display text at the UI edge.
- A line ending in `// i18n-ignore` is skipped by the hard-coded text check (say why in the comment); a debug-only file says `i18n-ignore-file: debug only` in a comment.

## Checks

All run as JVM tests in `:core:testing`:

| Test | Checks |
| :--- | :--- |
| `TranslationResourcesGuardTest` | Every translation keeps the English placeholders, uses its language's plural forms, keeps "boxlore" lowercase, translates nothing marked `translatable="false"`, has an English original, and string arrays keep their item count. |
| `BrandNameGuardTest` | "boxlore" is never capitalized anywhere in resources. |
| `HardcodedTextGuardTest` | No new listener text in Kotlin. Remaining text is listed in [`hardcoded-text-baseline.txt`](hardcoded-text-baseline.txt), which may only shrink. |

```bash
./gradlew :core:testing:testDebugUnitTest
# after moving text into resources, regenerate the baseline:
BOXLORE_I18N_BASELINE=write ./gradlew :core:testing:testDebugUnitTest --rerun
```

A missing translation only warns in lint (`MissingTranslation` is a warning in [`lint.xml`](../../lint.xml)) until every language is complete.

## Pseudo-locales

Debug builds of the phone and watch apps include two pseudo-locales:

- **English (XA)**, `en-XA`: every string longer and accented, to find clipped or hard-coded text.
- **Arabic (XB)**, `ar-XB`: the layout mirrored right to left.

Pick one in **Settings › System › Languages** (developer options must be on to list them) or, on Android 13 and later, as boxlore's own language in **Settings › Apps › boxlore › Language**. Text that stays plain English in en-XA is still hard-coded.

## Adding a language

1. Add `res/values-<code>/` files beside each module's English, following this guide.
2. Add the language to `AppLanguage` (`:core:prefs`) with its name in that language, and to `AppLanguageMatcher` if onboarding should suggest it for a region.
3. Add the code to `localeFilters` in `app/build.gradle.kts` and `wear/build.gradle.kts`.
4. Add a glossary column and its formality line here, and its plural forms to `TranslationResources` in `:core:testing`.
5. Run the checks above and look through the app in that language, including a tablet window.

On Android 13 and later the system's per-app language list comes from the translated folders automatically. On Android 12 the App language applies to the app's screens at once and to text built through the application context from the next app start; notifications, widgets and Android Auto follow the phone's language there.
