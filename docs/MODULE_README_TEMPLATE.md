# Module README template

Copy into `<module>/README.md` when creating or updating a Gradle module.

## Rules

- Folder path equals Gradle project id (`:core:playback` → `core/playback/`).
- Document stable public APIs and patterns — not every file.
- A change that creates or moves module ownership must update this README in the same change.
- Root `README.md` / CHANGELOG are not substitutes for module READMEs.
- Cross-cutting maps: [`ARCHITECTURE.md`](../ARCHITECTURE.md), [`docs/TESTING.md`](TESTING.md).

---

# `:group:name`

## Purpose

What this module owns and what it deliberately does not own.

## Public API

Stable types/entry points other modules may depend on:

- Repositories / managers / ports / key Compose screens
- Contracts (interfaces, factories, assemblers)
- Application-scoped instances callers must not recreate

## Internal structure

```text
src/main/java/.../
  foo/
  bar/
```

## Dependencies

Gradle edges (project + notable libraries):

- → `:core:model`

Forbidden reverse edges (examples: catalog ↛ designsystem; no feature → feature).

## Threading / lifecycle

- Main vs IO / default dispatchers
- Application-scoped vs Activity / ViewModel-scoped
- Service / WorkManager notes if applicable

## Persistence & identity

Anything that must stay stable across releases:

- SharedPreferences / DataStore names and keys
- Room DB filenames
- Worker / Service FQCNs
- ID schemes (`rss:`, mediaId prefixes)

## Testing notes

- Test locations (`src/test`)
- Fakes / fixtures (link `:core:testing`)
- Compose `testTag`s when UI (Maestro / manual)
- Local command:

```bash
./gradlew :<module>:testDebugUnitTest
```

## CI relevance

Which workflows exercise this module, or local-only.

## See also

- [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`docs/TESTING.md`](TESTING.md)
- Related module READMEs
