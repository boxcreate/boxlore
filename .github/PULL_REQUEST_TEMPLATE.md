## Title (required)

Use Conventional Commits. Examples from this repo:

- `feat(scope): short description`
- `fix(scope): short description`
- `chore: short description`
- `release: vX.Y.Z [skip changelog]`
- `docs: … [skip unit]` / `chore: … [skip unit]` — no-ops unit suite on PR (still reports green). **Only** for docs/chore with no logic risk.

Do **not** use sentence-case titles without a type prefix (e.g. avoid `Polish the announcement dialog`).

## Merge gate (required before merge)

The unit workflow runs architecture checks, detekt, JVM tests, Kover coverage, dependency guards, Python release-tooling tests, and Android lint on PR pushes unless a safe docs/chore PR uses `[skip unit]`. A new commit cancels the previous in-progress run; Actions → Run workflow runs the full suite. Run `./gradlew ktlintCheck` locally for Kotlin changes; ktlint is not part of the current PR workflow. There is **no merge queue**.

Master is protected by a branch ruleset. Required checks before merge:

1. **`testDebugUnitTest`** — PR pushes (new commits cancel the prior run; `[skip unit]` in the title no-ops for safe docs/chore only)
2. **`coderabbit-threads-resolved`** — every non-outdated CodeRabbit review thread is marked Resolved

Also on PRs (not ruleset-required): SonarCloud App, CodeRabbit App, Gitleaks. The PR must have **zero Sonar new-code issues**.

Flow:

1. Open the PR and iterate (unit suite cancels prior runs).
2. Address **every** CodeRabbit finding and mark every CodeRabbit thread **Resolved**; fix Sonar new-code issues and wait for unit + **`coderabbit-threads-resolved`**. The bare CodeRabbit status only confirms that its review completed.
3. If review decision is **`CHANGES_REQUESTED`**, stop automated merging. Do not dismiss the review or force-merge; ask a maintainer to merge (or dismiss) manually.
4. Otherwise squash-merge when required checks are green. A maintainer may explicitly authorize bypassing required status checks for this PR; general merge approval is not bypass approval. Report remaining checks and known risks before using the exception. Review requirements still apply.
5. Optional: Actions → Run workflow (`Unit Tests`) for a manual full gate.

## Summary

<!-- What changed and why. Be specific. Exact CHANGELOG / README wording goes in Release copy below. -->

-

## Motivation

<!-- Why this change exists. What problem or gap does it address for listeners or maintainers? -->

-

## What changed

<!-- Concrete product / code changes. Prefer bullets over vague summaries. -->

-

## Behavior & compatibility

<!-- User-visible behavior before/after. Call out FCM/API/payload compatibility, defaults, and anything older clients still rely on. -->

-

## Impact (required)

### User impact — pick **exactly one**

| Label | Use when |
|:--|:--|
| `user-impact-critical` | **Critical fix** — listeners already feel a correctness bug (missing episodes, late/wrong alerts, data loss). Highest README / changelog priority; **release-copy below is required** and is pasted verbatim (not AI-rewritten). |
| `user-impact-high` | Listeners clearly notice (player, search, downloads, onboarding, major UX) |
| `user-impact-medium` | Noticeable but not headline (polish, secondary flows) |
| `user-impact-low` | Minor user-facing tweak |
| `no-user-impact` | CI, docs, tooling, internal-only — no listener-facing change |

- [ ] `user-impact-critical`
- [ ] `user-impact-high`
- [ ] `user-impact-medium`
- [ ] `user-impact-low`
- [ ] `no-user-impact`

### Listener impact — **required when** `user-impact-critical`, `user-impact-high`, or `user-impact-medium`

<!-- Write this for a listener, not an engineer. What is different in their day-to-day use of boxlore after this ships? -->
<!-- Skip only for `user-impact-low` or `no-user-impact`. -->

**What changes in the user’s life:**

-

### Backend — optional, **pairable** with any user-impact level

| Label | Use when |
|:--|:--|
| `backend-change` | Touches server / proxy / infra (can combine with critical/high/medium/low/none) |

- [ ] `backend-change`

Examples: `user-impact-critical` + `backend-change`, `user-impact-high` + `backend-change`, or `no-user-impact` + `backend-change`.

Add impact labels on the PR (`gh pr edit <n> --add-label user-impact-critical --add-label backend-change`).

## Release copy (verbatim — highest priority)

<!--
changelog-on-merge (update_changelog.py) and prepare-release (prepare_release.py)
paste these regions as-is. The PR author or coding agent writes and reviews the
copy; workflows only order it and add PR links. No AI rewriting or fallback.

Developer copy is required for every PR. Listener copy is required for every
user-impact label; no-user-impact changes appear only in CHANGELOG.md.
Empty bullets or TBD fail validation. Release/tooling PRs may use the established
[skip changelog] exception.
Do not hand-edit CHANGELOG.md or README Upcoming / What's New; this section is
the source those workflows copy from.
-->

### CHANGELOG.md (required developer copy)

Keep a Changelog bullets for engineers. Use `### Added` / `### Changed` / `### Fixed` when you have more than one category. Class/module names are OK here.

<!-- release-copy:changelog:start -->

### Fixed
- 

<!-- release-copy:changelog:end -->

### README What's New / Upcoming (required for user-impact changes)

Plain listener English for README Upcoming, then What's New on release, and in-app release notes. Product name is **boxlore** (lowercase). No class names, FCM keys, or CI.
Use functional categories: `### Critical` (for `user-impact-critical`), `### Improvements`, or `### Fixes`. Do not use impact levels like `### High` or `### Medium` as headings.

<!-- release-copy:readme:start -->

### Critical
- 

<!-- release-copy:readme:end -->

## Test plan

<!-- Checklist of concrete verification steps for this PR. Mark items done before merge when possible. -->

- [ ] Built / installed locally (`./gradlew installDebug`) when UI or app behavior changed
- [ ] Manual checks for the user-visible paths touched by this PR
- [ ] Required checks are green before merge, or a maintainer explicitly authorized a check bypass for this PR
- [ ]

## Notes (optional)

<!-- Screenshots, rollout risks, follow-ups, related deploys (e.g. admin hosting), out of scope. -->
