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

    def test_notification_body_only_labels_ai_generated_notes(self) -> None:
        version = pr.AppVersion("0.0.13", 13)
        direct_whats = uc._render_whats_new_inner(
            "v0.0.13",
            "2026-07-29",
            SAMPLE_WHATS_NEW_BODY,
            include_ai_notice=False,
        )
        direct_readme = _readme_with_notes(
            upcoming=uc.EMPTY_UPCOMING_TEXT,
            whats_new_inner=direct_whats,
        )
        self.assertNotIn("AI-generated summary", pr.notification_body(direct_readme, version))

        ai_whats = uc._render_whats_new_inner(
            "v0.0.13",
            "2026-07-29",
            SAMPLE_WHATS_NEW_BODY,
            include_ai_notice=True,
        )
        ai_readme = _readme_with_notes(
            upcoming=uc.EMPTY_UPCOMING_TEXT,
            whats_new_inner=ai_whats,
        )
        self.assertIn("AI-generated summary", pr.notification_body(ai_readme, version))

    def test_direct_notification_keeps_every_reviewed_readme_item(self) -> None:
        version = pr.AppVersion("0.0.13", 13)
        six_items = "<ul>" + "".join(
            f"<li>Reviewed change {index}</li>" for index in range(1, 7)
        ) + "</ul>"
        direct_whats = uc._render_whats_new_inner(
            "v0.0.13",
            "2026-07-29",
            six_items,
            include_ai_notice=False,
        )
        direct_readme = _readme_with_notes(
            upcoming=uc.EMPTY_UPCOMING_TEXT,
            whats_new_inner=direct_whats,
        )
        self.assertEqual(6, len(pr.notification_bullets(direct_readme, version)))

        ai_whats = uc._render_whats_new_inner(
            "v0.0.13",
            "2026-07-29",
            six_items,
            include_ai_notice=True,
        )
        ai_readme = _readme_with_notes(
            upcoming=uc.EMPTY_UPCOMING_TEXT,
            whats_new_inner=ai_whats,
        )
        self.assertEqual(5, len(pr.notification_bullets(ai_readme, version)))

    def test_release_note_source_supports_direct_readme_path(self) -> None:
        self.assertEqual(
            "readme-upcoming",
            pr.resolve_notes_source(skip_notify=False, use_readme_upcoming=True),
        )
        self.assertEqual(
            "reconciled",
            pr.resolve_notes_source(skip_notify=False, use_readme_upcoming=False),
        )
        self.assertEqual(
            "artifacts-only",
            pr.resolve_notes_source(skip_notify=True, use_readme_upcoming=False),
        )
        with self.assertRaisesRegex(ValueError, "cannot be combined"):
            pr.resolve_notes_source(skip_notify=True, use_readme_upcoming=True)

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
        self.assertIn("docs/images/button_github_v8.svg", inner)
        self.assertIn('width="224" height="60"', inner)

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

    def test_promote_preserves_verbatim_upcoming_without_ai_notice(self) -> None:
        upcoming_body = (
            '<b>🚨 Critical:</b>\n<ul align="left">\n'
            "<li>Missing episodes now show up in the same show.</li>\n</ul>"
        )
        readme = (
            "# boxlore\n\n"
            f"{uc.DOWNLOAD_APK_START}\napk\n{uc.DOWNLOAD_APK_END}\n\n"
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
