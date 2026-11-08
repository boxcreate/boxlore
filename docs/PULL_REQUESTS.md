# Pull requests

How boxlore pull requests are titled, labelled, described and merged. The [PR template](../.github/PULL_REQUEST_TEMPLATE.md) holds only the sections to fill in; the rules live here.

## Title

Use Conventional Commits:

- `feat(scope): short description`
- `fix(scope): short description`
- `chore: short description`
- `release: vX.Y.Z [skip changelog]`
- `docs: … [skip unit]` / `chore: … [skip unit]`: the unit suite reports green without running. Only for docs or chore changes with no logic risk.

Don't use sentence-case titles without a type prefix (avoid `Polish the announcement dialog`).

## Labels

Exactly one user-impact label; `backend-change` can be added to any of them.

| Label | Use when |
| :--- | :--- |
| `user-impact-critical` | Listeners already feel a correctness bug (missing episodes, late or wrong alerts, data loss) |
| `user-impact-high` | Listeners clearly notice (player, search, downloads, onboarding, major UX) |
| `user-impact-medium` | Noticeable but not headline (polish, secondary flows) |
| `user-impact-low` | Minor user-facing tweak |
| `no-user-impact` | CI, docs, tooling, internal-only: no listener-facing change |
| `backend-change` | Touches the server, proxy or infrastructure |

Set them with `gh pr edit <n> --add-label …` or by ticking the template's checkboxes; the Release copy workflow applies ticked ones.

## Description

Fill in the template's sections and leave nothing of the template behind:

- **Summary:** what changed and why, in a few bullets.
- **Motivation:** the problem or gap it addresses for listeners or maintainers.
- **What changed:** concrete product and code changes.
- **Behavior & compatibility:** behaviour before and after, and anything older clients, FCM payloads or APIs rely on.
- **Listener impact:** required for critical, high and medium changes. What is different in a listener's day-to-day use, written for a listener. Remove the section otherwise.
- **Test plan:** concrete verification steps, ticked when done; separate JVM tests from device checks.
- **Notes:** screenshots, rollout risks or follow-ups, only when there are some.

Write for public readers: describe the change on its own terms, without chat context, private operational details or private product numbers.

## Release copy

Nothing in the release flow uses AI; the author writes both pieces.

- **Developer copy:** the region between `<!-- release-copy:changelog:start -->` and `<!-- release-copy:changelog:end -->`, required for every PR. Keep a Changelog bullets for engineers under `### Added`, `### Changed` or `### Fixed`; class and module names are fine. Empty bullets or "TBD" fail validation. On merge it is pasted into `CHANGELOG.md` word for word. Release and tooling PRs may use `[skip changelog]`.
- **Listener notes:** a user-impact PR edits [`release-notes/upcoming.md`](../release-notes/upcoming.md), the one list behind README Upcoming, the app's Coming next, What's New and the release announcement. Read the whole list first; if a line already covers the feature, change it so it says the net change since the last release, otherwise add one line to New, Improved or Fixed next to the lines about the same screen. Highlights holds at most three lines and becomes the announcement. Plain listener English, lowercase **boxlore**, no class names, FCM keys or CI terms. If the list already says it (a fix to something not yet released), tick "Listener notes already cover this change" instead.

Never hand-edit `CHANGELOG.md` or the root README's Upcoming and What's New regions; the release workflows own them.

## Merge gate

The unit workflow runs architecture checks, detekt, JVM tests, Kover coverage, dependency guards, Python release-tooling tests and Android lint on PR pushes, as parallel jobs, unless the title has `[skip unit]` or the PR changes only documentation. A new commit cancels the run in progress; Actions → Run workflow (`Unit Tests`) runs the full suite. ktlint is not part of the PR workflow, so run `./gradlew ktlintCheck` locally for Kotlin changes. There is no merge queue.

`master` is protected by a branch ruleset. Required before merge:

1. `testDebugUnitTest` passes.
2. Every review conversation is resolved, CodeRabbit's included.

Also on PRs, not ruleset-required: SonarCloud (zero new-code issues), CodeRabbit and Gitleaks.

Flow:

1. Open the PR and iterate.
2. Address every CodeRabbit finding and resolve every thread; fix Sonar new-code issues and wait for the unit suite. CodeRabbit's status only says its review finished.
3. If the review decision is `CHANGES_REQUESTED`, stop: don't dismiss the review or force-merge; a maintainer merges or dismisses it.
4. Otherwise squash-merge once required checks are green. A maintainer may explicitly authorize bypassing required status checks for a specific PR; general merge approval is not bypass approval. Report the remaining checks and known risks before using that exception. Review requirements still apply.
