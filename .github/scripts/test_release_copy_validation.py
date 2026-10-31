"""The PR check and the notes list: reject missing copy, never generate or drop release notes."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from argparse import Namespace
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import prepare_release as pr
import release_notes as rn
import update_changelog as uc
from test_update_changelog_copy import SAMPLE_PR_BODY, EMPTY_COPY_BODY, MINIMAL_CHANGELOG

NOTES = """<!-- rules -->

Announcement title: Insights are here

## Highlights

1. Listening history has a new Insights tab. (#1207)

## New

- Timeline lets you jump to any day. (#1207)

## Improved

- Podcast pages now use yellow accents. (#2001, #2003)

## Fixed

- Shows no longer list the same episode twice. (#1176)
"""

COVERED_BODY = SAMPLE_PR_BODY + "\n- [x] Listener notes already cover this change\n"


def notes_paths(stack: ExitStack, root: Path, text: str | None = NOTES) -> Path:
    """Point the notes list and its archives at [root]; returns the list's path."""
    upcoming = root / "release-notes" / "upcoming.md"
    upcoming.parent.mkdir(parents=True, exist_ok=True)
    if text is not None:
        upcoming.write_text(text, encoding="utf-8")
    stack.enter_context(patch.object(rn, "UPCOMING_PATH", upcoming))
    stack.enter_context(patch.object(rn, "ARCHIVE_DIR", upcoming.parent))
    return upcoming


class ReleaseCopyValidationTest(unittest.TestCase):
    def test_every_impact_needs_developer_copy(self):
        missing = SAMPLE_PR_BODY.split(uc.PR_CHANGELOG_COPY_START)[0]
        for label in uc.USER_IMPACT_LABELS:
            with self.subTest(label=label), self.assertRaisesRegex(ValueError, "release-copy:changelog"):
                uc.validate_release_copy("fix: restore downloads", missing, [label])

    def test_listener_copy_is_no_longer_part_of_the_pr_body(self):
        body = SAMPLE_PR_BODY.split("<!-- release-copy:readme:start -->")[0]
        impact, _, entries = uc.validate_release_copy("fix: restore downloads", body, ["user-impact-high"])
        self.assertEqual(impact, "user-impact-high")
        self.assertTrue(entries)

    def test_missing_multiple_or_body_only_labels_are_rejected(self):
        for labels in ([], ["backend-change"], ["user-impact-high", "user-impact-low"]):
            with self.subTest(labels=labels), self.assertRaisesRegex(ValueError, "exactly one"):
                uc.validate_release_copy("fix: user-impact-critical", SAMPLE_PR_BODY, labels)

    def test_legacy_labels_still_resolve(self):
        for labels, expected in ((["user-impact"], ("user-impact-high", False)), (["user-impact-high", "backend-change"], ("user-impact-high", True)), (["backend-fix"], ("no-user-impact", True))):
            with self.subTest(labels=labels):
                impact, backend, _ = uc.validate_release_copy("fix: example", SAMPLE_PR_BODY, labels)
                self.assertEqual((impact, backend), expected)

    def test_multiple_impact_labels_rejected_even_when_aliases_match(self):
        for labels in (["user-impact", "user-impact-high"], ["user-impact-high", "user-impact-high"]):
            with self.subTest(labels=labels), self.assertRaisesRegex(ValueError, "exactly one"):
                uc.validate_release_copy("fix: example", SAMPLE_PR_BODY, labels)

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
        updated = uc._update_readme(readme, groups=[{"heading": "Fixed", "bullets": [text]}])
        self.assertIn(text, updated)


class ListenerNotesRuleTest(unittest.TestCase):
    def test_a_listener_facing_pr_edits_the_list_or_says_it_covers_the_change(self):
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            path = notes_paths(stack, Path(temp))
            with self.assertRaisesRegex(ValueError, "doesn't edit"):
                uc.validate_listener_notes(SAMPLE_PR_BODY, "user-impact-medium", {"app/Feature.kt"})
            uc.validate_listener_notes(SAMPLE_PR_BODY, "user-impact-medium", {"app/Feature.kt", str(path)})
            uc.validate_listener_notes(COVERED_BODY, "user-impact-low", {"app/Feature.kt"})
            uc.validate_listener_notes(SAMPLE_PR_BODY, "no-user-impact", {"app/Feature.kt"})
            # A local run without the PR's files checks only the list's own rules.
            uc.validate_listener_notes(SAMPLE_PR_BODY, "user-impact-high", None)

    def test_the_list_keeps_its_rules_on_every_pr(self):
        too_many = NOTES.replace("## New", "2. Two\n3. Three\n4. Four\n\n## New")
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            notes_paths(stack, Path(temp), too_many)
            with self.assertRaisesRegex(ValueError, "at most 3"):
                uc.validate_listener_notes(SAMPLE_PR_BODY, "no-user-impact", set())

    def test_parse_reads_title_highlights_and_groups(self):
        notes = rn.parse(NOTES)
        self.assertEqual(notes.title, "Insights are here")
        self.assertEqual(notes.highlights, ["Listening history has a new Insights tab. (#1207)"])
        self.assertEqual(notes.groups["Improved"], ["Podcast pages now use yellow accents. (#2001, #2003)"])
        self.assertEqual(rn.problems(notes), [])
        self.assertTrue(rn.parse(rn.template()).is_empty())

    def test_problems_catch_placeholders_duplicates_and_stray_lines(self):
        self.assertTrue(rn.problems(rn.parse(NOTES.replace("- Timeline lets you jump to any day. (#1207)", "- TBD"))))
        self.assertTrue(rn.problems(rn.parse(NOTES + "- Timeline lets you jump to any day.\n")))
        with self.assertRaisesRegex(ValueError, "unknown section"):
            rn.parse("## Coming soon\n- Something\n")
        with self.assertRaisesRegex(ValueError, "not a list line"):
            rn.parse("A line outside any section\n")

    def test_readme_shows_highlights_first_with_a_badge_per_pr(self):
        groups = rn.readme_groups(rn.parse(NOTES), "boxcreate/boxlore")
        self.assertEqual([group["heading"] for group in groups], ["Highlights", "New", "Improved", "Fixed"])
        item = uc._bullet_to_html_list_item(groups[2]["bullets"][0])
        self.assertIn("Podcast pages now use yellow accents.", item)
        self.assertIn("alt=\"PR #2001\"", item)
        self.assertIn("alt=\"PR #2003\"", item)

    def test_announcement_is_the_title_and_the_highlights_word_for_word(self):
        self.assertEqual(
            rn.announcement(rn.parse(NOTES), "v0.0.30"),
            ("Insights are here", "- Listening history has a new Insights tab."),
        )
        untitled = rn.parse(NOTES.replace("Announcement title: Insights are here", "Announcement title:"))
        self.assertEqual(rn.announcement(untitled, "v0.0.30")[0], "boxlore v0.0.30 is here")

    def test_readme_sync_renders_the_list_and_keeps_the_readme_when_the_list_breaks_its_rules(self):
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            root = Path(temp)
            readme = root / "README.md"
            readme.write_text("# boxlore\n" + uc._render_release_notes_shell(upcoming_inner="old", whats_new_inner=None), encoding="utf-8")
            upcoming = notes_paths(stack, root)
            stack.enter_context(patch.object(uc, "README_PATH", readme))
            self.assertTrue(uc.sync_readme_upcoming())
            region = uc._extract_marked_region(readme.read_text(), uc.RELEASE_UPCOMING_START, uc.RELEASE_UPCOMING_END)
            self.assertLess(region.index("Highlights"), region.index("Fixed"))
            self.assertIn("Podcast pages now use yellow accents.", region)
            before = readme.read_text()
            upcoming.write_text(NOTES.replace("## New", "2. Two\n3. Three\n4. Four\n\n## New"), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "at most 3"):
                uc.sync_readme_upcoming()
            self.assertEqual(before, readme.read_text())


class ChangelogAndPrepareTest(unittest.TestCase):
    def test_released_pr_is_not_reinserted_into_unreleased(self):
        content = MINIMAL_CHANGELOG.replace("- Older release", "- Older release " + uc._pr_suffix(700))
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "CHANGELOG.md"
            path.write_text(content, encoding="utf-8")
            with patch.object(uc, "CHANGELOG_PATH", path):
                self.assertFalse(uc.append_changelog(700, "fix: old", SAMPLE_PR_BODY, ["user-impact-low"]))
            self.assertEqual(content, path.read_text())

    def _prepare(self, root: Path, stack: ExitStack, notes: str | None, pulls: list[dict]) -> tuple[list[Path], Path]:
        gradle = root / "build.gradle.kts"
        changelog = root / "CHANGELOG.md"
        readme = root / "README.md"
        gradle.write_text('versionCode = 12\nversionName = "0.0.12"\n')
        changelog.write_text(MINIMAL_CHANGELOG.replace("[0.0.12]", "[v0.0.12]"))
        readme.write_text(uc._render_release_notes_shell(
            upcoming_inner="Old notes",
            whats_new_inner=uc._render_whats_new_inner("v0.0.12", "2026-07-25", "Published notes"),
        ) + "\n" + pr.DOWNLOAD_APK_START + "x" + pr.DOWNLOAD_APK_END + "\n")
        upcoming = notes_paths(stack, root, notes)
        for target, path in ((pr, "APP_GRADLE_PATH"), (pr, "CHANGELOG_PATH"), (pr, "README_PATH"), (uc, "CHANGELOG_PATH"), (uc, "README_PATH")):
            stack.enter_context(patch.object(target, path, {"APP_GRADLE_PATH": gradle, "CHANGELOG_PATH": changelog, "README_PATH": readme}[path]))
        stack.enter_context(patch.object(pr, "assert_remote_target_is_free"))
        stack.enter_context(patch.object(pr, "pull_requests_between", return_value=pulls))
        stack.enter_context(patch.dict(os.environ, {"GITHUB_REPOSITORY": "boxcreate/boxlore", "GITHUB_TOKEN": "offline-test"}, clear=True))
        return [gradle, changelog, readme, upcoming], upcoming

    def test_prepare_archives_the_list_starts_a_fresh_one_and_lists_prs_missing_from_changelog(self):
        pulls = [{"number": 900, "title": "fix: polish"}, {"number": 971, "title": "fix: no entry"}]
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            root = Path(temp)
            files, upcoming = self._prepare(root, stack, NOTES, pulls)
            output = root / "outputs"
            stack.enter_context(patch.dict(os.environ, {"GITHUB_OUTPUT": str(output)}))
            pr.prepare_release(Namespace(bump="patch", latest_tag="v0.0.12", head_sha="candidate", skip_notify=False))
            archive = upcoming.parent / "v0.0.13.md"
            self.assertIn("Podcast pages now use yellow accents.", archive.read_text())
            self.assertTrue(rn.load(upcoming).is_empty())
            whats_new = uc._extract_marked_region(files[2].read_text(), uc.RELEASE_WHATS_NEW_START, uc.RELEASE_WHATS_NEW_END)
            self.assertLess(whats_new.index("Listening history has a new Insights tab."), whats_new.index("Shows no longer list"))
            self.assertIn("missing_changelog_prs=#971", output.read_text())

    def test_prepare_with_an_empty_list_fails_without_changing_any_file(self):
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            files, upcoming = self._prepare(Path(temp), stack, rn.template(), [])
            original = [path.read_bytes() for path in files]
            with self.assertRaisesRegex(ValueError, "is empty"):
                pr.prepare_release(Namespace(bump="patch", latest_tag="v0.0.12", head_sha="candidate", skip_notify=False))
            self.assertEqual(original, [path.read_bytes() for path in files])
            self.assertFalse((upcoming.parent / "v0.0.13.md").exists())

    def test_announcement_comes_from_the_released_notes(self):
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            root = Path(temp)
            notes_paths(stack, root)
            gradle = root / "build.gradle.kts"
            gradle.write_text('versionCode = 13\nversionName = "0.0.13"\n')
            (root / "release-notes" / "v0.0.13.md").write_text(NOTES, encoding="utf-8")
            output = root / "outputs"
            stack.enter_context(patch.object(pr, "APP_GRADLE_PATH", gradle))
            stack.enter_context(patch.dict(os.environ, {"GITHUB_REPOSITORY": "boxcreate/boxlore", "GITHUB_OUTPUT": str(output)}, clear=True))
            pr.write_notification_outputs()
            written = output.read_text()
            self.assertIn("notification_title=Insights are here", written)
            self.assertIn("- Listening history has a new Insights tab.", written)
            self.assertNotIn("#1207", written)

    def test_release_files_include_the_versions_notes_archive(self):
        self.assertEqual(
            pr.expected_release_files(skip_notify=False, version=pr.AppVersion("0.0.13", 13)),
            {"CHANGELOG.md", "README.md", "app/build.gradle.kts", "release-notes/upcoming.md", "release-notes/v0.0.13.md"},
        )
        self.assertEqual(pr.expected_release_files(skip_notify=True), pr.EXPECTED_FILES_SKIP_NOTIFY)


class ValidateCopyCliTest(unittest.TestCase):
    def _run(self, temp: str, labels: list | None, files: str | None, body: str = SAMPLE_PR_BODY):
        event = Path(temp) / "event.json"
        event.write_text(json.dumps({"pull_request": {"title": "fix: downloads", "body": body, "labels": [{"name": "user-impact-high"}]}}))
        env = {"PATH": os.environ["PATH"], "GITHUB_EVENT_PATH": str(event)}
        if labels is not None:
            env["PR_LABELS_JSON"] = json.dumps(labels)
        if files is not None:
            env["PR_CHANGED_FILES"] = files
        return subprocess.run([sys.executable, str(Path(uc.__file__).resolve()), "validate-copy"], cwd=temp, env=env, capture_output=True, text=True)

    def test_cli_validates_event_without_api_key_or_file_mutation(self):
        with tempfile.TemporaryDirectory() as temp:
            result = self._run(temp, None, None)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual([path.name for path in Path(temp).iterdir()], ["event.json"])

    def test_cli_reads_labels_applied_after_the_event(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertNotEqual(self._run(temp, [], None).returncode, 0)
            self.assertEqual(self._run(temp, ["user-impact-high"], None).returncode, 0)

    def test_cli_needs_the_list_edited_for_a_listener_facing_pr(self):
        with tempfile.TemporaryDirectory() as temp:
            missing = self._run(temp, ["user-impact-high"], "app/src/main/Feature.kt\n")
            self.assertNotEqual(missing.returncode, 0)
            self.assertIn("release-notes/upcoming.md", missing.stderr + missing.stdout)
            self.assertEqual(self._run(temp, ["user-impact-high"], "app/Feature.kt\nrelease-notes/upcoming.md\n").returncode, 0)
            self.assertEqual(self._run(temp, ["user-impact-high"], "app/Feature.kt\n", COVERED_BODY).returncode, 0)


if __name__ == "__main__":
    unittest.main()
