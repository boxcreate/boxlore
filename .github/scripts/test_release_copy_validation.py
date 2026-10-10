"""Reject missing copy without generating or silently dropping release notes."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import prepare_release as pr
import update_changelog as uc
from test_update_changelog_copy import SAMPLE_PR_BODY, EMPTY_COPY_BODY, MINIMAL_CHANGELOG


class ReleaseCopyValidationTest(unittest.TestCase):
    def test_every_user_impact_requires_both_regions(self):
        missing_readme = SAMPLE_PR_BODY.split(uc.PR_README_COPY_START)[0]
        for label in uc.USER_IMPACT_LABELS[:-1]:
            with self.subTest(label=label), self.assertRaisesRegex(ValueError, "release-copy:readme"):
                uc.validate_release_copy("fix: restore downloads", missing_readme, [label])

    def test_no_user_impact_excludes_listener_copy(self):
        impact, _, entries, groups = uc.validate_release_copy("ci: validation", SAMPLE_PR_BODY, ["no-user-impact"])
        self.assertEqual(impact, "no-user-impact")
        self.assertTrue(entries)
        self.assertEqual(groups, [])

    def test_missing_multiple_or_body_only_labels_are_rejected(self):
        for labels in ([], ["backend-change"], ["user-impact-high", "user-impact-low"]):
            with self.subTest(labels=labels), self.assertRaisesRegex(ValueError, "exactly one"):
                uc.validate_release_copy("fix: user-impact-critical", SAMPLE_PR_BODY, labels)

    def test_legacy_labels_follow_the_same_normalization_as_tag_resolution(self):
        for labels in (["user-impact"], ["user-impact", "user-impact-high", "backend-change"], ["backend-fix"]):
            with self.subTest(labels=labels):
                impact, backend, _, _ = uc.validate_release_copy("fix: example", SAMPLE_PR_BODY, labels)
                self.assertEqual((impact, backend), uc._resolve_pr_tags(labels))

    def test_conflicting_legacy_impact_is_not_silently_ignored(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            uc.validate_release_copy("fix: example", SAMPLE_PR_BODY, ["backend-fix", "user-impact-high"])

    def test_placeholders_are_not_authored_copy(self):
        with self.assertRaisesRegex(ValueError, "empty/TBD"):
            uc.validate_release_copy("fix: example", EMPTY_COPY_BODY, ["user-impact-critical"])

    def test_duplicate_copy_regions_fail_instead_of_discarding_second_region(self):
        with self.assertRaisesRegex(ValueError, "exactly one complete"):
            uc.validate_release_copy("fix: example", SAMPLE_PR_BODY + SAMPLE_PR_BODY, ["user-impact-high"])

    def test_leftover_placeholder_does_not_pass_with_one_valid_note(self):
        body = SAMPLE_PR_BODY.replace("### Fixed\n", "### Fixed\n- TBD\n")
        with self.assertRaisesRegex(ValueError, "empty/TBD"):
            uc.validate_release_copy("fix: example", body, ["user-impact-high"])

    def test_authored_backslashes_are_literal_not_regex_replacement_codes(self):
        readme = "# boxlore\n" + uc._render_release_notes_shell(upcoming_inner="old", whats_new_inner=None)
        text = r"The path C:\podcasts\1 now works."
        updated = uc._update_readme(readme, groups=[{"heading": "Fixes", "bullets": [text]}])
        self.assertIn(text, updated)

    def test_empty_stored_readme_block_is_still_missing_copy(self):
        content = MINIMAL_CHANGELOG.replace("## [0.0.12]", "<!-- readme-copy:start pr=900 -->\n### Fixes\n- TBD\n<!-- readme-copy:end pr=900 -->\n\n## [0.0.12]")
        with self.assertRaisesRegex(ValueError, "#900"):
            uc.reviewed_readme_groups(content)

    def test_readme_failure_preserves_previous_file(self):
        with tempfile.TemporaryDirectory() as temp:
            readme = Path(temp) / "README.md"
            changelog = Path(temp) / "CHANGELOG.md"
            readme.write_text("Existing published README", encoding="utf-8")
            changelog.write_text(MINIMAL_CHANGELOG, encoding="utf-8")
            with patch.object(uc, "README_PATH", readme), patch.object(uc, "CHANGELOG_PATH", changelog):
                with self.assertRaisesRegex(ValueError, "#900.*backfill-changelog"):
                    uc.sync_readme_upcoming()
            self.assertEqual(readme.read_text(), "Existing published README")

    def test_authored_prefixes_and_all_bullets_survive_readme_round_trip(self):
        content = uc._upsert_readme_copy_block(
            MINIMAL_CHANGELOG, 900,
            [{"heading": "Improvements", "bullets": ["Fixed: retain these exact words", "Second authored line"]}],
        )
        groups = uc.reviewed_readme_groups(content)
        readme = "# boxlore\n" + uc._render_release_notes_shell(upcoming_inner="old", whats_new_inner=None)
        updated = uc._update_readme(readme, groups=groups)
        self.assertIn("Fixed: retain these exact words", updated)
        self.assertIn("Second authored line", updated)
        self.assertNotIn("AI-generated summary", updated)

    def test_no_user_impact_block_cannot_leak_into_readme(self):
        content = MINIMAL_CHANGELOG.replace("user-impact-low", "no-user-impact")
        content = uc._upsert_readme_copy_block(content, 900, [{"heading": "Fixes", "bullets": ["Internal CI only"]}])
        self.assertEqual(uc.reviewed_readme_groups(content), [])

    def test_historical_backfill_supplies_missing_copy_without_rewriting_bullets(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "CHANGELOG.md"
            path.write_text(MINIMAL_CHANGELOG, encoding="utf-8")
            with patch.object(uc, "CHANGELOG_PATH", path):
                self.assertTrue(uc.append_changelog(900, "fix: polish", SAMPLE_PR_BODY, ["user-impact-low"]))
            updated = path.read_text()
            self.assertEqual(uc._extract_unreleased_sections(updated), uc._extract_unreleased_sections(MINIMAL_CHANGELOG))
            self.assertTrue(uc.reviewed_readme_groups(updated))

    def test_released_pr_backfill_does_not_reinsert_into_upcoming(self):
        content = MINIMAL_CHANGELOG.replace("- Older release", "- Older release " + uc._pr_suffix(700))
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "CHANGELOG.md"
            path.write_text(content, encoding="utf-8")
            with patch.object(uc, "CHANGELOG_PATH", path):
                self.assertFalse(uc.append_changelog(700, "fix: old", SAMPLE_PR_BODY, ["user-impact-low"]))
            self.assertEqual(content, path.read_text())

    def test_batch_reconciliation_checks_all_copy_before_writing(self):
        pulls = [
            {"number": 971, "title": "fix: first", "body": SAMPLE_PR_BODY, "labels": [{"name": "user-impact-critical"}]},
            {"number": 972, "title": "fix: second", "body": EMPTY_COPY_BODY, "labels": [{"name": "user-impact-critical"}]},
        ]
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "CHANGELOG.md"
            path.write_text(MINIMAL_CHANGELOG, encoding="utf-8")
            with patch.object(pr, "CHANGELOG_PATH", path), patch.object(uc, "CHANGELOG_PATH", path), patch.object(pr, "pull_requests_between", return_value=pulls):
                with self.assertRaisesRegex(ValueError, "release-copy:changelog"):
                    pr.reconcile_changelog("boxcreate/boxlore", "unused", "v0.0.12", "candidate")
            self.assertEqual(MINIMAL_CHANGELOG, path.read_text())

    def test_prepare_copy_failure_keeps_version_and_download_links_unchanged(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            gradle = root / "build.gradle.kts"
            changelog = root / "CHANGELOG.md"
            readme = root / "README.md"
            gradle.write_text('versionCode = 12\nversionName = "0.0.12"\n')
            changelog.write_text(MINIMAL_CHANGELOG.replace("[0.0.12]", "[v0.0.12]"))
            readme.write_text(uc._render_release_notes_shell(
                upcoming_inner="Reviewed notes",
                whats_new_inner=uc._render_whats_new_inner("v0.0.12", "2026-07-25", "Published notes"),
            ))
            paths = [gradle, changelog, readme]
            original = [path.read_bytes() for path in paths]
            pulls = [{"number": 971, "title": "fix: example", "body": EMPTY_COPY_BODY, "labels": [{"name": "user-impact-high"}]}]
            with patch.object(pr, "APP_GRADLE_PATH", gradle), patch.object(pr, "CHANGELOG_PATH", changelog), patch.object(pr, "README_PATH", readme), patch.object(uc, "CHANGELOG_PATH", changelog), patch.object(uc, "README_PATH", readme), patch.object(pr, "assert_remote_target_is_free"), patch.object(pr, "pull_requests_between", return_value=pulls), patch.dict(os.environ, {"GITHUB_REPOSITORY": "boxcreate/boxlore", "GITHUB_TOKEN": "offline-test"}, clear=True):
                with self.assertRaisesRegex(ValueError, "release-copy:changelog"):
                    pr.prepare_release(Namespace(bump="patch", latest_tag="v0.0.12", head_sha="candidate", skip_notify=False, use_readme_upcoming=False))
            self.assertEqual(original, [path.read_bytes() for path in paths])

    def test_cli_validates_event_without_api_key_or_file_mutation(self):
        with tempfile.TemporaryDirectory() as temp:
            event = Path(temp) / "event.json"
            event.write_text(json.dumps({"pull_request": {"title": "fix: downloads", "body": SAMPLE_PR_BODY, "labels": [{"name": "user-impact-high"}]}}))
            env = {"PATH": os.environ["PATH"], "GITHUB_EVENT_PATH": str(event)}
            result = subprocess.run([sys.executable, str(Path(uc.__file__).resolve()), "validate-copy"], cwd=temp, env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(list(Path(temp).iterdir()), [event])


if __name__ == "__main__":
    unittest.main()
