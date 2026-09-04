#!/usr/bin/env python3
"""Hermetic checks for verbatim PR release-copy and user-impact-critical."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS))

import prepare_release as pr  # noqa: E402
import update_changelog as uc  # noqa: E402


SAMPLE_PR_BODY = """
## Summary
Something Groq would rewrite into different words.

<!-- release-copy:changelog:start -->
### Added
- PodcastRepository unions publisher-feed extras after the PI page
### Fixed
- LatestEpisodeTipLogic never writes an older tip over a newer one
<!-- release-copy:changelog:end -->

<!-- release-copy:readme:start -->
### Critical
- Missing episodes from a show’s public feed now show up in the same show, and alerts can fire when the feed updates even if the catalog is behind
<!-- release-copy:readme:end -->
"""

EMPTY_COPY_BODY = """
<!-- release-copy:changelog:start -->
### Fixed
- 
<!-- release-copy:changelog:end -->

<!-- release-copy:readme:start -->
### Critical
- TBD
<!-- release-copy:readme:end -->
"""

MINIMAL_CHANGELOG = """# Changelog

## [Unreleased]

### Changed
- Unrelated polish ([#900](https://github.com/boxcreate/boxlore/pull/900)) <!-- impact:user-impact-low -->

## [0.0.12] - 2026-07-25
### Added
- Older release
"""


class ReleaseCopyParseTest(unittest.TestCase):
    def test_parses_keep_a_changelog_and_readme_regions(self) -> None:
        changelog = uc.parse_pr_changelog_copy(
            SAMPLE_PR_BODY, "feat(catalog): fill missing PI episodes", "user-impact-critical"
        )
        self.assertEqual(
            changelog["Added"],
            ["PodcastRepository unions publisher-feed extras after the PI page"],
        )
        self.assertEqual(
            changelog["Fixed"],
            ["LatestEpisodeTipLogic never writes an older tip over a newer one"],
        )
        readme = uc.parse_pr_readme_copy(SAMPLE_PR_BODY, "user-impact-critical")
        self.assertEqual(readme[0]["heading"], "Critical")
        self.assertIn("Missing episodes", readme[0]["bullets"][0])

    def test_empty_placeholders_are_ignored(self) -> None:
        self.assertEqual(uc.parse_pr_changelog_copy(EMPTY_COPY_BODY), {})
        self.assertEqual(uc.parse_pr_readme_copy(EMPTY_COPY_BODY, "user-impact-critical"), [])

    def test_critical_label_scores_above_high(self) -> None:
        impact, backend = uc._resolve_pr_tags(
            labels=["user-impact-critical", "backend-change"]
        )
        self.assertEqual(impact, "user-impact-critical")
        self.assertTrue(backend)
        self.assertGreater(
            uc.USER_IMPACT_SCORE["user-impact-critical"],
            uc.USER_IMPACT_SCORE["user-impact-high"],
        )

    def test_critical_wins_when_multiple_impact_labels(self) -> None:
        impact, _ = uc._resolve_pr_tags(
            labels=["user-impact-high", "user-impact-critical"]
        )
        self.assertEqual(impact, "user-impact-critical")


class LockedChangelogFlowTest(unittest.TestCase):
    def test_append_uses_verbatim_copy_and_skips_groq(self) -> None:
        updated, changed = uc._update_changelog(
            MINIMAL_CHANGELOG,
            uc.parse_pr_changelog_copy(SAMPLE_PR_BODY),
            971,
            impact="user-impact-critical",
            backend_change=True,
            copy_locked=True,
        )
        self.assertTrue(changed)
        self.assertIn("<!-- copy:locked -->", updated)
        self.assertIn("PodcastRepository unions publisher-feed extras", updated)
        self.assertIn("<!-- impact:user-impact-critical+backend-change -->", updated)
        with_readme = uc._upsert_readme_copy_block(
            updated,
            971,
            uc.parse_pr_readme_copy(SAMPLE_PR_BODY, "user-impact-critical"),
        )
        self.assertIn("<!-- readme-copy:start pr=971 -->", with_readme)
        parsed = uc._extract_unreleased_sections(with_readme)
        self.assertNotIn("Missing episodes from a show", " ".join(parsed.get("Fixed", [])))
        self.assertTrue(any("PodcastRepository" in b for b in parsed["Added"]))

    def test_sync_changelog_does_not_call_groq_when_all_locked(self) -> None:
        locked, _ = uc._update_changelog(
            MINIMAL_CHANGELOG,
            {"Fixed": ["Author-written fix bullet"]},
            971,
            impact="user-impact-critical",
            copy_locked=True,
        )
        with patch.object(uc, "_groq_curate_changelog_unreleased") as groq:
            locked_sections, unlocked = uc._partition_locked_sections(
                uc._extract_unreleased_sections(locked)
            )
            self.assertTrue(any(locked_sections.values()))
            self.assertIn("Author-written fix bullet", locked_sections["Fixed"][0])
            self.assertTrue(uc._is_copy_locked(locked_sections["Fixed"][0]))
            curated = uc._merge_locked_and_curated(
                locked_sections,
                {"Fixed": ["Groq would rewrite this ([#971](https://github.com/boxcreate/boxlore/pull/971))"]},
            )
            self.assertIn("Author-written fix bullet", curated["Fixed"][0])
            self.assertTrue(uc._is_copy_locked(curated["Fixed"][0]))
            self.assertFalse(any("Groq would rewrite" in b for b in curated["Fixed"]))
            groq.assert_not_called()

    def test_readme_sync_uses_locked_copy_verbatim_without_ai_notice(self) -> None:
        changelog, _ = uc._update_changelog(
            MINIMAL_CHANGELOG,
            {"Fixed": ["Technical bullet"]},
            971,
            impact="user-impact-critical",
            copy_locked=True,
        )
        changelog = uc._upsert_readme_copy_block(
            changelog,
            971,
            uc.parse_pr_readme_copy(SAMPLE_PR_BODY, "user-impact-critical"),
        )
        groups, locked_prs = uc._locked_readme_groups_from_changelog(changelog)
        self.assertEqual(locked_prs, {971})
        self.assertEqual(groups[0]["heading"], "Critical")
        self.assertIn("Missing episodes from a show", groups[0]["bullets"][0])
        readme = (
            "# boxlore\n\n"
            + uc._render_release_notes_shell(
                upcoming_inner=uc.EMPTY_UPCOMING_TEXT,
                whats_new_inner=None,
            )
            + "\n\n## Search\n"
        )
        updated = uc._update_readme(readme, groups=groups, include_ai_notice=False)
        upcoming = uc._extract_marked_region(
            updated, uc.RELEASE_UPCOMING_START, uc.RELEASE_UPCOMING_END
        )
        assert upcoming is not None
        self.assertIn("Missing episodes from a show", upcoming)
        self.assertIn("Critical", upcoming)
        self.assertNotIn("AI-generated summary", upcoming)

    def test_locked_readme_copy_orders_higher_impact_before_older_prs(self) -> None:
        changelog = """# Changelog

## [Unreleased]

### Added
- Existing feature ([#1001](https://github.com/boxcreate/boxlore/pull/1001)) <!-- impact:user-impact-medium -->
- Requested widget theme ([#1002](https://github.com/boxcreate/boxlore/pull/1002)) <!-- impact:user-impact-critical -->

<!-- readme-copy:start pr=1001 -->
### New features
- Existing feature
<!-- readme-copy:end pr=1001 -->

<!-- readme-copy:start pr=1002 -->
### New features
- Requested widget theme
- Preferred default tabs
<!-- readme-copy:end pr=1002 -->
"""

        groups, locked_prs = uc._locked_readme_groups_from_changelog(changelog)

        self.assertEqual(locked_prs, {1001, 1002})
        new_features = next(group["bullets"] for group in groups if group["heading"] == "New features")
        self.assertIn("Requested widget theme", new_features[0])
        self.assertIn("Preferred default tabs", new_features[1])
        self.assertIn("Existing feature", new_features[2])

    def test_promote_changelog_strips_readme_copy_blocks(self) -> None:
        changelog, _ = uc._update_changelog(
            MINIMAL_CHANGELOG,
            {"Fixed": ["Technical bullet"]},
            971,
            impact="user-impact-critical",
            copy_locked=True,
        )
        changelog = uc._upsert_readme_copy_block(
            changelog,
            971,
            [{"heading": "Critical", "bullets": ["Listener line"]}],
        )
        promoted = pr.promote_changelog(changelog, pr.AppVersion("0.0.14", 14), "2026-08-12")
        versioned = promoted.split("## [v0.0.14]")[1].split("## [0.0.12]")[0]
        self.assertIn("Technical bullet", versioned)
        self.assertNotIn("readme-copy:start", versioned)
        self.assertNotIn("Listener line", versioned)
        self.assertIn("<!-- copy:locked -->", versioned)

    def test_promote_readme_preserves_verbatim_upcoming_without_ai_notice(self) -> None:
        upcoming_body = (
            '<b>🚨 Critical:</b>\n<ul align="left">\n'
            "<li>Missing episodes now show up in the same show.</li>\n</ul>"
        )
        readme = (
            "# boxlore\n\n"
            + uc._render_release_notes_shell(
                upcoming_inner=upcoming_body,
                whats_new_inner=None,
                include_ai_notice=False,
            )
            + "\n\n## Search\n"
        )
        promoted = pr.promote_readme(readme, pr.AppVersion("0.0.14", 14), "2026-08-12")
        whats_new = uc._extract_marked_region(
            promoted, uc.RELEASE_WHATS_NEW_START, uc.RELEASE_WHATS_NEW_END
        )
        assert whats_new is not None
        self.assertIn("Missing episodes now show up in the same show.", whats_new)
        self.assertNotIn("AI-generated summary", whats_new)

    def test_append_changelog_skips_groq_when_copy_present(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "CHANGELOG.md"
            path.write_text(MINIMAL_CHANGELOG, encoding="utf-8")
            with patch.object(uc, "CHANGELOG_PATH", path), patch.object(
                uc, "_groq_entries"
            ) as groq:
                changed = uc.append_changelog(
                    "unused-key",
                    971,
                    "feat(catalog): fill missing PI episodes",
                    SAMPLE_PR_BODY,
                    labels=["user-impact-critical", "backend-change"],
                )
                groq.assert_not_called()
                self.assertTrue(changed)
                text = path.read_text(encoding="utf-8")
                self.assertIn("copy:locked", text)
                self.assertIn("readme-copy:start pr=971", text)
                self.assertIn("PodcastRepository unions publisher-feed extras", text)
                self.assertIn("Missing episodes from a show", text)

    def test_groq_is_not_called_when_appending_locked_copy(self) -> None:
        with patch.object(uc, "_groq_entries") as groq:
            entries = uc.parse_pr_changelog_copy(SAMPLE_PR_BODY)
            self.assertTrue(entries)
            groq.assert_not_called()


class ChangelogSanitizationAndExtractionTest(unittest.TestCase):
    def test_extract_pr_number_prioritizes_pr_link_over_issue_number(self) -> None:
        bullet_with_issue_and_pr = (
            "- Fixed same-show continuation in `SmartQueueEngine` by restoring forward "
            "chronological queries in `LocalEpisodeCatalogDao` and `RssEpisodeDao` (#1017). "
            "([#1019](https://github.com/boxcreate/boxlore/pull/1019)) "
            "<!-- impact:user-impact-critical --> <!-- copy:locked -->"
        )
        self.assertEqual(uc._extract_pr_number(bullet_with_issue_and_pr), 1019)

    def test_extract_pr_number_handles_standard_trailing_pr(self) -> None:
        bullet_trailing = "- Enabled Gradle configuration cache ([#1021](https://github.com/boxcreate/boxlore/pull/1021))"
        self.assertEqual(uc._extract_pr_number(bullet_trailing), 1021)
        simple_pr = "- Some minor polish (#999)"
        self.assertEqual(uc._extract_pr_number(simple_pr), 999)

    def test_clean_pr_body_strips_test_plans_checkboxes_and_prompts(self) -> None:
        noisy_body = """## Summary
Real summary text.

<details>
<summary>🤖 Prompt for AI Agents</summary>
Noise that should be stripped.
</details>

<!-- release-copy:changelog:start -->
<!-- release-copy:changelog:end -->

## Impact
- [x] user-impact-critical
- [ ] user-impact-high

### Listener impact
Important listener note.

## Test plan
- [x] Built / installed locally
- [x] Tested on device
"""
        cleaned = uc._clean_pr_body(noisy_body)
        self.assertIn("Real summary text.", cleaned)
        self.assertIn("Important listener note.", cleaned)
        self.assertNotIn("Prompt for AI Agents", cleaned)
        self.assertNotIn("Noise that should be stripped", cleaned)
        self.assertNotIn("user-impact-critical", cleaned)
        self.assertNotIn("Test plan", cleaned)
        self.assertNotIn("Built / installed locally", cleaned)

    def test_format_readme_bullet_strips_category_prefixes(self) -> None:
        self.assertEqual(
            uc._format_readme_bullet("[Fixed] Fixed broken queue", 1019),
            "Fixed broken queue ([#1019](https://github.com/boxcreate/boxlore/pull/1019))",
        )
        self.assertEqual(
            uc._format_readme_bullet("Added: Brand new explore screen", 1020),
            "Brand new explore screen ([#1020](https://github.com/boxcreate/boxlore/pull/1020))",
        )


if __name__ == "__main__":
    unittest.main()
