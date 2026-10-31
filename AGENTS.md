# Working on boxlore

Before planning or editing, check for `AGENTS.internal.md` in the repository root and load it when present. Report missing internal guidance. Do not perform production deployments or repository-administration operations without the relevant internal instructions. In a configured maintainer environment, missing internal guidance restricts work to read-only investigation until restored.

When installed and trusted, local Codex hooks load the internal document at session/subagent start and after compaction. Verify loading rather than assuming that a file's presence enables it. The tracked `.worktreeinclude` copies ignored local instructions and hooks into new local Codex-managed worktrees; ordinary Git worktrees and cloud environments require separate provisioning.

## Project context

boxlore is a Kotlin Android app with `:app`, `:core:*`, and `:feature:*` modules. `AppContainer` owns dependency wiring. The external backend is not part of this repository; build and launch setup is described in [CONTRIBUTING.md](CONTRIBUTING.md).

## Engineering constraints

- Read [ARCHITECTURE.md](ARCHITECTURE.md) and the affected module README before changing code. Architecture owns module boundaries and identity/storage contracts; module READMEs describe local behavior.
- Features must not depend on or import other features. Use `:core:analytics` instead of PostHog directly in features. Do not add Hilt, Koin, Dagger, or MockK.
- Keep cohesive capabilities in focused modules. Obtain explicit maintainer approval before module extraction or architectural decoupling.
- Preserve application/storage names, legacy worker and service identities, deep links, `rss:`/negative IDs, and cache-key contracts. Keep one UI-scoped `PlaybackRepository` and service-owned Smart Queue refill.
- Every Android UI or behavior change, including shared components and edits initially targeting phones, must handle the affected tablet, foldable, and resizable-window states. Follow the [adaptive UI and state contract](ARCHITECTURE.md#adaptive-ui-and-state) for state retention, window transitions, and required JVM coverage; tablet support is not optional follow-up work.
- Update the affected module README in the same change using [the module template](docs/MODULE_README_TEMPLATE.md).
- Add or extend hermetic JVM `src/test` coverage for changed logic. Bug fixes require a regression test for the failure mode, including shared behavior where applicable. Do not add Compose instrumentation or emulator CI.
- Before changing catalog scripts, read [scripts/README.md](scripts/README.md). Catalog sync runs outside GitHub Actions; pushing code does not deploy it. Production operations require the internal runbook.
- Keep work scoped to this repository. Preserve unrelated edits and avoid destructive cleanup of developer checkouts.

## Product and data safeguards

- Use **boxlore** in user-facing copy. Keep wording concise, consistent with surrounding UI, and accurate about the resulting behavior. Apply available UX writing guidance; maintainer skill requirements are in the internal document.
- Use solid Material 3 surfaces for cards and panels.
- Keep secrets and local configuration out of Git, including `.env`, `local.properties`, keystores, and `google-services.json`.
- Release workflows own `CHANGELOG.md` and the root README's generated Upcoming/What's New regions. Put developer wording in the PR template's changelog region. For a listener-facing change, read `release-notes/upcoming.md` and edit it in place so it says the net change since the last release: change the line that already covers the feature instead of adding another, keep lines about the same screen or feature together, keep Highlights to the 3 most important lines, and follow the rules at the top of the file. Intentional release-note rewrites require matching script contracts.
- Keep private operational details out of public documentation and Android PR descriptions.

## Validation and review

Use [docs/TESTING.md](docs/TESTING.md) to select relevant checks and distinguish local verification from remote CI or device verification. Use the Gradle wrapper.

For Android UI/behavior changes, report the affected phone/tablet/foldable states and transition coverage. Separate source inspection, JVM test results, and device evidence; tests at separate static sizes do not prove state survives resizing. Documentation-only changes do not require unrelated adaptive tests.

Follow [the PR template](.github/PULL_REQUEST_TEMPLATE.md) for Conventional Commit titles, exactly one user-impact label, release copy, and review requirements. Before an authorized squash merge, required checks must be green by default, every CodeRabbit finding addressed and thread resolved, and SonarCloud must have zero new-code issues. A maintainer may explicitly authorize bypassing required status checks for a specific PR; general merge authorization does not authorize a bypass. Report the remaining checks and known risks before using that exception. Review requirements still apply, and agents must not dismiss requested-change reviews.

## Documentation map

| Topic | Source |
| :--- | :--- |
| Local setup and build versus runtime configuration | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Architecture, dependency direction, stable identities | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Episode identity and recipe IDs | [docs/EPISODE_IDS.md](docs/EPISODE_IDS.md) |
| Commands, test selection, coverage, CI | [docs/TESTING.md](docs/TESTING.md) |
| Script responsibilities and development checks | [scripts/README.md](scripts/README.md) |
| Module documentation format | [docs/MODULE_README_TEMPLATE.md](docs/MODULE_README_TEMPLATE.md) |
| PR labels, release copy, review and merge process | [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md) |

`AGENTS.internal.md` supplements these public contracts with local authorization, skills, environment setup, and operations. It is intentionally absent from public clones and must remain untracked.
