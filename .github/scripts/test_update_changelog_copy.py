#!/usr/bin/env python3
"""Hermetic checks for verbatim PR developer copy in CHANGELOG."""

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
Context that must not replace the exact release wording.

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
    def test_parses_keep_a_changelog_region(self) -> None:
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

    def test_empty_placeholders_are_ignored(self) -> None:
        self.assertEqual(uc.parse_pr_changelog_copy(EMPTY_COPY_BODY), {})

    def test_critical_label_scores_above_high(self) -> None:
        impact, backend, _ = uc.validate_release_copy(
            "fix: example", SAMPLE_PR_BODY, ["user-impact-critical", "backend-change"]
        )
        self.assertEqual(impact, "user-impact-critical")
        self.assertTrue(backend)
        self.assertGreater(
            uc.USER_IMPACT_SCORE["user-impact-critical"],
            uc.USER_IMPACT_SCORE["user-impact-high"],
        )


class LockedChangelogFlowTest(unittest.TestCase):
    def test_append_uses_verbatim_copy(self) -> None:
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
        parsed = uc._extract_unreleased_sections(updated)
        self.assertNotIn("Missing episodes from a show", updated)
        self.assertTrue(any("PodcastRepository" in b for b in parsed["Added"]))

    def test_sync_preserves_every_locked_and_legacy_bullet(self) -> None:
        content, _ = uc._update_changelog(
            MINIMAL_CHANGELOG, {"Changed": ["Exact first line", "Exact second line"]},
            971, impact="user-impact-critical", copy_locked=True,
        )
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "CHANGELOG.md"
            path.write_text(content, encoding="utf-8")
            with patch.object(uc, "CHANGELOG_PATH", path):
                uc.sync_changelog_unreleased()
                updated = path.read_text(encoding="utf-8")
                before = uc._extract_unreleased_sections(content)
                after = uc._extract_unreleased_sections(updated)
                self.assertCountEqual(before["Changed"], after["Changed"])
                self.assertLess(updated.index("Exact first line"), updated.index("Unrelated polish"))
                self.assertFalse(uc.sync_changelog_unreleased())

    def test_promote_changelog_strips_legacy_readme_copy_blocks(self) -> None:
        changelog, _ = uc._update_changelog(
            MINIMAL_CHANGELOG,
            {"Fixed": ["Technical bullet"]},
            971,
            impact="user-impact-critical",
            copy_locked=True,
        )
        # Listener copy older builds stored per PR inside [Unreleased].
        changelog = changelog.replace(
            "## [0.0.12]",
            "<!-- readme-copy:start pr=971 -->\n### Critical\n- Listener line\n<!-- readme-copy:end pr=971 -->\n\n## [0.0.12]",
        )
        promoted = pr.promote_changelog(changelog, pr.AppVersion("0.0.14", 14), "2026-08-12")
        versioned = promoted.split("## [v0.0.14]")[1].split("## [0.0.12]")[0]
        self.assertIn("Technical bullet", versioned)
        self.assertNotIn("readme-copy:start", versioned)
        self.assertNotIn("Listener line", versioned)
        self.assertIn("<!-- copy:locked -->", versioned)

    def test_promote_readme_preserves_verbatim_upcoming(self) -> None:
        upcoming_body = (
            '<b>🚨 Critical:</b>\n<ul align="left">\n'
            "<li>Missing episodes now show up in the same show.</li>\n</ul>"
        )
        readme = (
            "# boxlore\n\n"
            + uc._render_release_notes_shell(
                upcoming_inner=upcoming_body,
                whats_new_inner=None,
            )
            + "\n\n## Search\n"
        )
        promoted = pr.promote_readme(readme, pr.AppVersion("0.0.14", 14), "2026-08-12")
        whats_new = uc._extract_marked_region(
            promoted, uc.RELEASE_WHATS_NEW_START, uc.RELEASE_WHATS_NEW_END
        )
        assert whats_new is not None
        self.assertIn("Missing episodes now show up in the same show.", whats_new)

    def test_append_stores_authored_copy_without_credentials(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "CHANGELOG.md"
            path.write_text(MINIMAL_CHANGELOG, encoding="utf-8")
            with patch.object(uc, "CHANGELOG_PATH", path):
                changed = uc.append_changelog(
                    971, "feat(catalog): fill missing PI episodes", SAMPLE_PR_BODY,
                    labels=["user-impact-critical", "backend-change"],
                )
                self.assertTrue(changed)
                text = path.read_text(encoding="utf-8")
                self.assertIn("copy:locked", text)
                self.assertIn("PodcastRepository unions publisher-feed extras", text)
                # Listener copy lives in the notes list now, not in CHANGELOG.
                self.assertNotIn("readme-copy", text)
                self.assertNotIn("Missing episodes from a show", text)
                self.assertFalse(uc.append_changelog(971, "same", SAMPLE_PR_BODY, labels=[]))
                self.assertEqual(text, path.read_text(encoding="utf-8"))

    def test_missing_copy_fails_before_changelog_write(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "CHANGELOG.md"
            path.write_text(MINIMAL_CHANGELOG, encoding="utf-8")
            with patch.object(uc, "CHANGELOG_PATH", path):
                with self.assertRaisesRegex(ValueError, "release-copy:changelog"):
                    uc.append_changelog(971, "fix: example", EMPTY_COPY_BODY, ["user-impact-critical"])
            self.assertEqual(MINIMAL_CHANGELOG, path.read_text(encoding="utf-8"))


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


if __name__ == "__main__":
    unittest.main()
