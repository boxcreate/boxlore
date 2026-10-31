<!-- Maintainer checkouts: read the PR rules in AGENTS.internal.md before filling or editing this template. -->

## Title (required)

Use Conventional Commits. Examples from this repo:

- `feat(scope): short description`
- `fix(scope): short description`
- `chore: short description`
- `release: vX.Y.Z [skip changelog]`
- `docs: … [skip unit]` / `chore: … [skip unit]` — no-ops unit suite on PR (still reports green). **Only** for docs/chore with no logic risk.

Do **not** use sentence-case titles without a type prefix (e.g. avoid `Polish the announcement dialog`).

## Merge gate (required before merge)

The unit workflow runs architecture checks, detekt, JVM tests, Kover coverage, dependency guards, Python release-tooling tests, and Android lint on PR pushes, as parallel jobs, unless a safe docs/chore PR uses `[skip unit]` or the PR changes only documentation. A new commit cancels the previous in-progress run; Actions → Run workflow runs the full suite. Run `./gradlew ktlintCheck` locally for Kotlin changes; ktlint is not part of the current PR workflow. There is **no merge queue**.

Master is protected by a branch ruleset. Required before merge:

1. **`testDebugUnitTest`** — PR pushes (new commits cancel the prior run; `[skip unit]` in the title no-ops for safe docs/chore only)
2. **Every review conversation resolved** — GitHub's conversation resolution, CodeRabbit's threads included

Also on PRs (not ruleset-required): SonarCloud App, CodeRabbit App, Gitleaks. The PR must have **zero Sonar new-code issues**.

Flow:

1. Open the PR and iterate (unit suite cancels prior runs).
2. Address **every** CodeRabbit finding and mark every CodeRabbit thread **Resolved**; fix Sonar new-code issues and wait for the unit suite; the merge button stays locked until every conversation is resolved. The bare CodeRabbit status only confirms that its review completed.
3. If review decision is **`CHANGES_REQUESTED`**, stop automated merging. Do not dismiss the review or force-merge; ask a maintainer to merge (or dismiss) manually.
4. Otherwise squash-merge when required checks are green. A maintainer may explicitly authorize bypassing required status checks for this PR; general merge approval is not bypass approval. Report remaining checks and known risks before using the exception. Review requirements still apply.
5. Optional: Actions → Run workflow (`Unit Tests`) for a manual full gate.

## Summary

<!-- What changed and why. Be specific. Developer CHANGELOG wording goes in Release copy below; listener wording goes in release-notes/upcoming.md. -->

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
| `user-impact-critical` | **Critical fix** — listeners already feel a correctness bug (missing episodes, late/wrong alerts, data loss) |
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

## Release copy (verbatim)

<!--
The PR author or coding agent writes both; no workflow rewrites them, and nothing in the release flow uses AI.
- Developer copy: the CHANGELOG region below, required for every PR. Release/tooling PRs may use the
  established [skip changelog] exception.
- Listener notes: release-notes/upcoming.md, the one list behind README Upcoming, the app's Coming next,
  What's New at release and the release announcement. Its rules are at the top of the file.
Empty bullets or TBD fail validation. Do not hand-edit CHANGELOG.md or README Upcoming / What's New.
-->

### CHANGELOG.md (required developer copy)

Keep a Changelog bullets for engineers. Use `### Added` / `### Changed` / `### Fixed` when you have more than one category. Class/module names are OK here.

<!-- release-copy:changelog:start -->

### Fixed
- 

<!-- release-copy:changelog:end -->

### Listener notes (required for user-impact changes)

Edit [`release-notes/upcoming.md`](../release-notes/upcoming.md) in this PR. Read the whole list first: if a line already covers the feature, change that line so it says the net change since the last release; otherwise add one line at the end of New, Improved or Fixed. Highlights holds at most 3 lines, most important first, and becomes the release announcement. Plain listener English; the product name is **boxlore** (lowercase); no class names, FCM keys or CI.

The **Release copy** check fails a user-impact PR that doesn't change the list, unless the list already says it (a fix to something not yet released, for example):

- [ ] Listener notes already cover this change

## Test plan

<!-- Checklist of concrete verification steps for this PR. Mark items done before merge when possible. -->

- [ ] Built / installed locally (`./gradlew installDebug`) when UI or app behavior changed
- [ ] Manual checks for the user-visible paths touched by this PR
- [ ] Required checks are green before merge, or a maintainer explicitly authorized a check bypass for this PR
- [ ]

## Notes (optional)

<!-- Screenshots, rollout risks, follow-ups, related deploys (e.g. admin hosting), out of scope. -->
