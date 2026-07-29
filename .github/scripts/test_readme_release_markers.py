#!/usr/bin/env python3
"""Hermetic checks for README release-note markers used by changelog / prepare_release."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS))

import prepare_release as pr  # noqa: E402
import update_changelog as uc  # noqa: E402


SAMPLE_WHATS_NEW_BODY = """\
<b>🆕 New features:</b>
<ul align="left">
<li>Typo-tolerant show search. <a href="https://github.com/boxcreate/boxlore/pull/955"><img src="https://img.shields.io/badge/PR-955-6750A4?style=flat-square" alt="PR #955" height="18"/></a></li>
<li>Concept search for shows and episodes.</li>
</ul>
"""


def _readme_with_notes(*, upcoming: str, whats_new_inner: str | None) -> str:
    shell = uc._render_release_notes_shell(
        upcoming_inner=upcoming,
        whats_new_inner=whats_new_inner,
    )
    return (
        "# boxlore\n\n"
        f"{uc.DOWNLOAD_APK_START}\n"
        '<a href="https://github.com/boxcreate/boxlore/releases/latest/download/boxlore-v0.0.12.apk">'
        "apk</a>\n"
        f"{uc.DOWNLOAD_APK_END}\n\n"
        f"{shell}\n\n"
        "## Search\n"
    )


class ReadmeReleaseMarkersTest(unittest.TestCase):
    def test_sync_preserves_whats_new_and_rewrites_upcoming(self) -> None:
        whats = uc._render_whats_new_inner("v0.0.13", "2026-07-29", SAMPLE_WHATS_NEW_BODY)
        readme = _readme_with_notes(upcoming=uc.EMPTY_UPCOMING_TEXT, whats_new_inner=whats)
        updated = uc._update_readme(
            readme,
            groups=[
                {
                    "heading": "New features",
                    "bullets": [
                        "Faster charts ([#953](https://github.com/boxcreate/boxlore/pull/953))"
                    ],
                }
            ],
        )
        upcoming = uc._extract_marked_region(
            updated, uc.RELEASE_UPCOMING_START, uc.RELEASE_UPCOMING_END
        )
        assert upcoming is not None
        self.assertIn("Faster charts", upcoming)
        self.assertIn("PR-953", upcoming)
        whats_new = uc._extract_marked_region(
            updated, uc.RELEASE_WHATS_NEW_START, uc.RELEASE_WHATS_NEW_END
        )
        assert whats_new is not None
        self.assertIn("release-meta: version=v0.0.13", whats_new)
        self.assertIn("Typo-tolerant show search", whats_new)

    def test_promote_moves_upcoming_into_whats_new(self) -> None:
        upcoming_body = (
            '<b>🆕 New features:</b>\n<ul align="left">\n'
            "<li>Brand new search.</li>\n</ul>"
        )
        readme = _readme_with_notes(upcoming=upcoming_body, whats_new_inner=None)
        promoted = pr.promote_readme(readme, pr.AppVersion("0.0.14", 14), "2026-07-30")
        self.assertEqual(pr.latest_readme_version(promoted), "0.0.14")
        upcoming = uc._extract_marked_region(
            promoted, uc.RELEASE_UPCOMING_START, uc.RELEASE_UPCOMING_END
        )
        assert upcoming is not None
        self.assertIn(uc.EMPTY_UPCOMING_TEXT, upcoming)
        whats_new = uc._extract_marked_region(
            promoted, uc.RELEASE_WHATS_NEW_START, uc.RELEASE_WHATS_NEW_END
        )
        assert whats_new is not None
        self.assertIn("Brand new search", whats_new)
        self.assertIn("release-meta: version=v0.0.14 date=2026-07-30", whats_new)

    def test_notification_bullets_from_markers(self) -> None:
        whats = uc._render_whats_new_inner("v0.0.13", "2026-07-29", SAMPLE_WHATS_NEW_BODY)
        readme = _readme_with_notes(upcoming=uc.EMPTY_UPCOMING_TEXT, whats_new_inner=whats)
        bullets = pr.notification_bullets(readme, pr.AppVersion("0.0.13", 13))
        self.assertGreaterEqual(len(bullets), 1)
        self.assertTrue(any("Typo-tolerant" in b for b in bullets))

    def test_download_apk_marker_updates(self) -> None:
        whats = uc._render_whats_new_inner("v0.0.12", "2026-07-25", SAMPLE_WHATS_NEW_BODY)
        readme = _readme_with_notes(upcoming=uc.EMPTY_UPCOMING_TEXT, whats_new_inner=whats)
        updated = pr.update_readme_download_url(
            readme, "boxcreate/boxlore", pr.AppVersion("0.0.13", 13)
        )
        self.assertIn("boxlore-v0.0.13.apk", updated)
        self.assertNotIn("boxlore-v0.0.12.apk", updated)
        inner = uc._extract_marked_region(
            updated, uc.DOWNLOAD_APK_START, uc.DOWNLOAD_APK_END
        )
        assert inner is not None
        self.assertIn("boxlore-v0.0.13.apk", inner)

    def test_skip_notify_expected_files_include_readme_url(self) -> None:
        self.assertEqual(
            pr.EXPECTED_FILES_SKIP_NOTIFY,
            {"app/build.gradle.kts", "README.md"},
        )
        self.assertTrue(
            pr._skip_notify_changed_files_ok({"app/build.gradle.kts", "README.md"})
        )
        self.assertTrue(pr._skip_notify_changed_files_ok({"app/build.gradle.kts"}))
        self.assertFalse(
            pr._skip_notify_changed_files_ok(
                {"app/build.gradle.kts", "README.md", "CHANGELOG.md"}
            )
        )

    def test_skip_notify_url_rewrite_preserves_whats_new(self) -> None:
        whats = uc._render_whats_new_inner("v0.0.12", "2026-07-25", SAMPLE_WHATS_NEW_BODY)
        upcoming = "Something cooking for next."
        readme = _readme_with_notes(upcoming=upcoming, whats_new_inner=whats)
        updated = pr.update_readme_download_url(
            readme, "boxcreate/boxlore", pr.AppVersion("0.0.13", 13)
        )
        self.assertIn("boxlore-v0.0.13.apk", updated)
        self.assertIn(upcoming, updated)
        self.assertIn("release-meta: version=v0.0.12", updated)
        self.assertNotIn("release-meta: version=v0.0.13", updated)


if __name__ == "__main__":
    unittest.main()
