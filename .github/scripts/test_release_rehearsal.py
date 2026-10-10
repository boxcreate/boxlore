"""Offline rehearsal must leave source files, releases and audiences untouched."""

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rehearse_release as rehearsal
import update_changelog as copy
from test_update_changelog_copy import MINIMAL_CHANGELOG


GRADLE = 'android { defaultConfig {\nversionCode = 12\nversionName = "0.0.12"\n} }\n'
README = "# boxlore\n" + copy._render_release_notes_shell(upcoming_inner="old", whats_new_inner=None)
CHANGELOG = copy._upsert_readme_copy_block(
    MINIMAL_CHANGELOG, 900,
    [{"heading": "Improvements", "bullets": ["Your library opens faster.", "Exact second note."]}],
)


class ReleaseRehearsalTest(unittest.TestCase):
    def test_prepare_uses_real_copy_and_version_functions(self):
        preview = rehearsal.candidate(GRADLE, CHANGELOG, README, operation="prepare-release", bump="minor", release_date="2026-10-10")
        self.assertIn('versionName = "0.1.0"', preview["gradle"])
        self.assertIn("versionCode = 13", preview["gradle"])
        self.assertIn("## [v0.1.0] - 2026-10-10", preview["changelog"])
        self.assertIn("Your library opens faster.", preview["readme"])
        plan = preview["report"]["announcement"]
        self.assertEqual(plan["topic"], "debug_users")
        self.assertEqual(plan["mode"], "offline-preview")
        self.assertFalse(plan["sent"])

    def test_quiet_refresh_keeps_version_and_does_not_prepare_notification(self):
        preview = rehearsal.candidate(GRADLE, CHANGELOG, README, operation="refresh-latest-artifacts", bump="major", release_date="2026-10-10")
        self.assertEqual(preview["gradle"], GRADLE)
        self.assertEqual(preview["changelog"], CHANGELOG)
        self.assertEqual(preview["readme"], README)
        self.assertFalse(preview["report"]["version_bumped"])
        self.assertIsNone(preview["report"]["announcement"])

    def sources(self, root, changelog=CHANGELOG):
        (root / "app").mkdir()
        values = {"app/build.gradle.kts": GRADLE, "CHANGELOG.md": changelog, "README.md": README}
        for name, value in values.items():
            (root / name).write_text(value, encoding="utf-8")
        return {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in values}

    def test_report_writing_never_changes_source_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            original = self.sources(root)
            output = root / "preview"
            rehearsal.write_preview(root, output, release_date="2026-10-10")
            self.assertEqual(original, {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in original})
            report = json.loads((output / "report.json").read_text())
            self.assertEqual(report["candidate_code"], 13)
            self.assertEqual(len(list(output.iterdir())), 4)

    def test_missing_copy_fails_before_any_artifact_is_written(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            original = self.sources(root, MINIMAL_CHANGELOG)
            with self.assertRaisesRegex(ValueError, "#900"):
                rehearsal.write_preview(root, root / "preview")
            self.assertFalse((root / "preview").exists())
            self.assertEqual(original, {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in original})

    def test_output_symlink_cannot_overwrite_source(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.sources(root)
            output = root / "preview"
            output.mkdir()
            (output / "candidate.gradle.kts").symlink_to(root / "app/build.gradle.kts")
            with self.assertRaisesRegex(ValueError, "source files"):
                rehearsal.write_preview(root, output)
            self.assertEqual((root / "app/build.gradle.kts").read_text(), GRADLE)
            self.assertFalse((output / "report.json").exists())

    def test_rehearsal_workflow_has_no_publish_or_send_capability(self):
        workflows = Path(__file__).resolve().parent.parent / "workflows"
        source = (workflows / "release-rehearsal.yml").read_text()
        self.assertIn("workflow_dispatch:", source)
        self.assertIn("contents: read", source)
        for forbidden in ("secrets.", "contents: write", "gh release", "git push", "send-notification.yml", "FIREBASE_CREDENTIALS"):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
