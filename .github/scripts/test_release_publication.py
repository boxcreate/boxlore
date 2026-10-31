"""Publication builds the release commit (or master after a fix), keeps the version, and re-runs safely."""

import os
import re
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
from refresh_update_feed import refresh
from test_release_copy_validation import NOTES
from test_update_changelog_copy import MINIMAL_CHANGELOG

V13 = pr.AppVersion("0.0.13", 13)
SHA_A = "a" * 40
SHA_B = "b" * 40
PUBLISHED = {V13.apk_asset, V13.aab_asset, "SHA256SUMS", pr.BUILD_COMMIT_ASSET, "update.json"}


class PublicationPlanTest(unittest.TestCase):
    def plan(self, **kwargs):
        return pr.publication_plan(mode=kwargs.pop("mode", "publish"), version=V13, build_sha=SHA_A, **kwargs)

    def test_a_first_publication_builds_and_creates_the_release(self):
        self.assertEqual({"build": "true", "release": "create"}, self.plan(tag_target=None, release_assets=None))

    def test_a_rerun_after_the_files_were_published_reuses_them(self):
        self.assertEqual(
            {"build": "false", "release": "keep"},
            self.plan(tag_target=SHA_A, release_assets=PUBLISHED, built_from=SHA_A),
        )

    def test_files_of_unknown_origin_are_built_again(self):
        # Releases published before files recorded their commit.
        self.assertEqual(
            {"build": "true", "release": "upload"},
            self.plan(tag_target=SHA_A, release_assets=PUBLISHED - {pr.BUILD_COMMIT_ASSET}),
        )

    def test_a_rerun_after_a_refresh_never_reuses_the_refreshed_files(self):
        # A refresh rebuilt the files from master (B) and left the tag at the release commit (A): reusing
        # B's APK with A's manifest could offer it to phones it doesn't support.
        with self.assertRaisesRegex(ValueError, "last built from bbbbbbbbbbbb.*refresh-latest-artifacts"):
            self.plan(tag_target=SHA_A, release_assets=PUBLISHED, built_from=SHA_B)

    def test_a_rerun_after_a_partial_upload_builds_and_uploads_again(self):
        self.assertEqual(
            {"build": "true", "release": "upload"},
            self.plan(tag_target=SHA_A, release_assets={V13.apk_asset}),
        )

    def test_a_tag_made_without_a_release_is_used_as_it_is(self):
        self.assertEqual({"build": "true", "release": "create"}, self.plan(tag_target=SHA_A, release_assets=None))

    def test_a_version_published_from_another_commit_points_to_refresh(self):
        with self.assertRaisesRegex(ValueError, "refresh-latest-artifacts"):
            self.plan(tag_target=SHA_B, release_assets=PUBLISHED)

    def test_refresh_always_builds_and_needs_the_release(self):
        self.assertEqual(
            {"build": "true", "release": "upload"},
            self.plan(mode="refresh", tag_target=SHA_B, release_assets=PUBLISHED),
        )
        with self.assertRaisesRegex(ValueError, "publish it first"):
            self.plan(mode="refresh", tag_target=None, release_assets=None)


class ReleaseStateTest(unittest.TestCase):
    def test_reads_the_tag_target_the_files_and_the_commit_they_were_built_from(self):
        responses = {
            "git/ref/tags/v0.0.13": {"object": {"type": "tag", "sha": "annotation"}},
            "git/tags/annotation": {"object": {"type": "commit", "sha": SHA_A}},
            "releases/tags/v0.0.13": {"assets": [{"name": name, "id": index} for index, name in enumerate(sorted(PUBLISHED))]},
        }
        with patch.object(pr, "github_request", side_effect=lambda _repo, _token, path, **_: responses.get(path)), \
                patch.object(pr, "github_asset_text", return_value=SHA_A) as asset_text:
            self.assertEqual((SHA_A, PUBLISHED, SHA_A), pr.github_release_state("boxcreate/boxlore", "token", "v0.0.13"))
        asset_text.assert_called_once_with("boxcreate/boxlore", "token", sorted(PUBLISHED).index(pr.BUILD_COMMIT_ASSET))
        with patch.object(pr, "github_request", return_value=None), patch.object(pr, "github_asset_text") as asset_text:
            self.assertEqual((None, None, None), pr.github_release_state("boxcreate/boxlore", "token", "v0.0.13"))
        asset_text.assert_not_called()


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


class PublishAfterAFixTest(unittest.TestCase):
    """v0.0.26: the publication failed, a fix merged, and the same version could not be published again."""

    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        temp = Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        (temp / "repo").mkdir()
        previous = Path.cwd()
        os.chdir(temp / "repo")
        self.addCleanup(os.chdir, previous)
        self.stack.enter_context(patch.dict(os.environ, {
            "GITHUB_REPOSITORY": "boxcreate/boxlore",
            "GITHUB_TOKEN": "offline-test",
            "GITHUB_OUTPUT": str(temp / "outputs"),
            "GIT_AUTHOR_NAME": "Test", "GIT_AUTHOR_EMAIL": "test@example.com",
            "GIT_COMMITTER_NAME": "Test", "GIT_COMMITTER_EMAIL": "test@example.com",
        }))
        Path("app").mkdir()
        Path("app/build.gradle.kts").write_text('versionCode = 12\nversionName = "0.0.12"\n')
        Path("CHANGELOG.md").write_text(MINIMAL_CHANGELOG.replace("[0.0.12]", "[v0.0.12]"))
        Path("README.md").write_text(uc._render_release_notes_shell(
            upcoming_inner="Old notes",
            whats_new_inner=uc._render_whats_new_inner("v0.0.12", "2026-07-25", "<ul><li>Published notes</li></ul>"),
        ) + "\n" + pr.DOWNLOAD_APK_START + "x" + pr.DOWNLOAD_APK_END + "\n")
        Path("release-notes").mkdir()
        Path("release-notes/upcoming.md").write_text(NOTES)
        git("init", "-q", "-b", "master")
        git("add", "-A")
        git("commit", "-q", "-m", "feat: before the release")
        with patch.object(pr, "assert_remote_target_is_free"), patch.object(pr, "pull_requests_between", return_value=[]):
            pr.prepare_release(Namespace(bump="patch", latest_tag="v0.0.12", head_sha=git("rev-parse", "HEAD"), skip_notify=False))
        git("add", "-A")
        git("commit", "-q", "-m", "release: v0.0.13 [skip changelog]", "-m", "* release: prepare v0.0.13 [skip changelog]")
        self.release_sha = git("rev-parse", "HEAD")
        # The fix merges after the failed publication: CHANGELOG gains an [Unreleased] entry.
        changelog = Path("CHANGELOG.md").read_text()
        self.assertIn("## [Unreleased]\n", changelog)
        Path("CHANGELOG.md").write_text(changelog.replace("## [Unreleased]\n", "## [Unreleased]\n\n### Fixed\n- Compile fix\n", 1))
        Path("Fix.kt").write_text("fixed\n")
        git("add", "-A")
        git("commit", "-q", "-m", "fix: compile again (#2000)")
        self.fix_sha = git("rev-parse", "HEAD")

    def resolve(self, **kwargs) -> dict[str, str]:
        Path(os.environ["GITHUB_OUTPUT"]).write_text("")
        with patch.object(pr, "github_release_state", return_value=kwargs.pop("state", (None, None, None))):
            pr.resolve_build(Namespace(**{"operation": "publish", "latest_master": False, "latest_tag": None, **kwargs}))
        outputs: dict[str, str] = {}
        lines = iter(Path(os.environ["GITHUB_OUTPUT"]).read_text().splitlines())
        for line in lines:
            if "<<" in line:
                key, marker = line.split("<<", 1)
                outputs[key] = "\n".join(iter(lambda: next(lines), marker))
            elif "=" in line:
                key, value = line.split("=", 1)
                outputs[key] = value
        return outputs

    def test_the_old_check_of_master_itself_fails_once_a_fix_merged(self):
        with self.assertRaisesRegex(ValueError, r"\[Unreleased\]"):
            pr.verify_release_metadata(V13)

    def test_publication_builds_the_release_commit_by_default(self):
        outputs = self.resolve()
        self.assertEqual(self.release_sha, outputs["merge_sha"])
        self.assertEqual(self.release_sha, outputs["build_sha"])
        self.assertEqual(("publish", "v0.0.13", "13", "true", "create"), (
            outputs["mode"], outputs["tag"], outputs["version_code"], outputs["build"], outputs["release"]))

    def test_latest_master_publishes_the_same_version_with_the_fix(self):
        outputs = self.resolve(latest_master=True)
        self.assertEqual(self.release_sha, outputs["merge_sha"])
        self.assertEqual(self.fix_sha, outputs["build_sha"])
        self.assertEqual(("v0.0.13", "13", "create"), (outputs["tag"], outputs["version_code"], outputs["release"]))

    def test_a_rerun_after_publishing_from_master_reuses_its_files(self):
        outputs = self.resolve(latest_master=True, state=(self.fix_sha, PUBLISHED, self.fix_sha))
        self.assertEqual(("false", "keep"), (outputs["build"], outputs["release"]))

    def test_a_release_commit_that_changed_other_files_is_refused(self):
        git("reset", "-q", "--hard", self.release_sha)
        Path("Stray.kt").write_text("stray\n")
        git("add", "-A")
        git("commit", "-q", "--amend", "--no-edit")
        with self.assertRaisesRegex(ValueError, "expected exactly"):
            self.resolve()


class FeedTextTest(unittest.TestCase):
    def readme(self, tag: str, line: str) -> str:
        return uc._render_release_notes_shell(
            upcoming_inner='<b>Fixed:</b><ul><li>Coming soon</li></ul>',
            whats_new_inner=uc._render_whats_new_inner(tag, "2026-11-01", f"<ul><li>{line}</li></ul>"),
        )

    def test_feed_text_refresh_rewrites_whats_new_for_its_own_version_only(self):
        manifest = dict(schemaVersion=1, packageName="cx.aswin.boxlore", versionCode=13, versionName="0.0.13",
                        apkSha256="verified", notes="- Old line", upcoming="- Old")
        refreshed = refresh(manifest, self.readme("v0.0.13", "Corrected line"))
        self.assertEqual("- Corrected line", refreshed["notes"])
        self.assertEqual("## Fixed\n- Coming soon", refreshed["upcoming"])
        self.assertEqual(manifest["apkSha256"], refreshed["apkSha256"])
        self.assertEqual("- Old line", refresh(manifest, self.readme("v0.0.12", "Other release"))["notes"])
        emptied = self.readme("v0.0.13", "x").replace("<ul><li>x</li></ul>", "")
        self.assertEqual("", refresh(manifest, emptied)["notes"])

    def test_whats_new_follows_the_released_notes_file(self):
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            root = Path(temp)
            readme = root / "README.md"
            readme.write_text(self.readme("v0.0.13", "Listening history has a new Insights tab."))
            (root / "release-notes").mkdir()
            (root / "release-notes" / "upcoming.md").write_text(rn.template())
            (root / "release-notes" / "v0.0.13.md").write_text(
                NOTES.replace("has a new Insights tab", "has a new Insights tab with your records"))
            stack.enter_context(patch.object(uc, "README_PATH", readme))
            stack.enter_context(patch.object(rn, "UPCOMING_PATH", root / "release-notes" / "upcoming.md"))
            stack.enter_context(patch.object(rn, "ARCHIVE_DIR", root / "release-notes"))
            self.assertTrue(uc.sync_readme_upcoming())
            whats_new = uc._extract_marked_region(readme.read_text(), uc.RELEASE_WHATS_NEW_START, uc.RELEASE_WHATS_NEW_END)
            self.assertIn("with your records", whats_new)
            self.assertIn("release-meta: version=v0.0.13 date=2026-11-01", whats_new)
            self.assertFalse(uc.sync_readme_upcoming())


class WorkflowShapeTest(unittest.TestCase):
    root = Path(__file__).resolve().parents[2]

    def test_publication_and_refresh_share_one_build(self):
        workflow = (self.root / ".github/workflows/changelog-on-merge.yml").read_text()
        self.assertEqual(1, workflow.count(":app:assembleRelease :app:bundlePlayRelease"))
        self.assertNotIn("refresh-latest-artifacts:\n", workflow)
        publish = workflow.split("  publish-release:\n", 1)[1].split("\n  refresh-update-feed:\n", 1)[0]
        self.assertLess(publish.index("Set up Android build tools"), publish.index("git checkout --quiet --detach"))
        self.assertLess(publish.index("git checkout --quiet --detach"), publish.index("Materialize protected build inputs"))
        for step in publish.split("      - name: ")[1:]:
            name = step.split("\n", 1)[0]
            if name in {"Materialize protected build inputs", "Build signed release APK and AAB", "Verify artifact versions and signatures"}:
                self.assertIn("steps.release.outputs.build == 'true'", step, name)

    def test_files_record_their_commit_and_the_feed_text_comes_from_master(self):
        workflow = (self.root / ".github/workflows/changelog-on-merge.yml").read_text()
        publish = workflow.split("  publish-release:\n", 1)[1].split("\n  refresh-update-feed:\n", 1)[0]
        self.assertLess(publish.index('cp README.md "$RUNNER_TEMP/master-README.md"'), publish.index("git checkout --quiet --detach"))
        self.assertIn('--readme "$RUNNER_TEMP/master-README.md"', publish.split("- name: Publish verified update manifest", 1)[1])
        self.assertIn('> "$RUNNER_TEMP/release-assets/build-commit.txt"', publish)
        upload = publish.split("- name: Publish the files to the GitHub release", 1)[1].split("      - name: ", 1)[0]
        self.assertIn('"$RUNNER_TEMP/release-assets/build-commit.txt"', upload)
        self.assertEqual("build-commit.txt", pr.BUILD_COMMIT_ASSET)

    def test_every_job_that_uses_the_pusher_token_makes_it(self):
        workflow = (self.root / ".github/workflows/changelog-on-merge.yml").read_text()
        jobs = re.split(r"\n(?=  [a-z][a-z-]*:\n)", workflow.split("\njobs:\n", 1)[1])
        self.assertGreaterEqual(len(jobs), 5)
        for job in jobs:
            if "steps.app-token.outputs.token" in job:
                self.assertIn("id: app-token", job, job.split("\n", 1)[0])

    def test_release_dry_run_runs_on_release_prs_only(self):
        dry_run = (self.root / ".github/workflows/release-contracts.yml").read_text()
        self.assertIn("pull_request:", dry_run)
        self.assertIn("paths: [app/build.gradle.kts]", dry_run)
        self.assertIn("startsWith(github.head_ref, 'release/v')", dry_run)
        self.assertNotIn("push:", dry_run)


if __name__ == "__main__":
    unittest.main()
