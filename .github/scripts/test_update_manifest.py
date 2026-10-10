import hashlib
from pathlib import Path
import tempfile
import unittest

from refresh_upcoming import refresh
from generate_update_manifest import build_manifest, listener_notes, upcoming_notes


class UpdateManifestTests(unittest.TestCase):
    def test_manifest_uses_exact_candidate_version_immutable_url_and_checksum(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "candidate.apk"
            apk.write_bytes(b"verified signed fixture")
            manifest = build_manifest('versionCode = 29\nversionName = "0.0.29"\nminSdk = 31', apk, "boxlore-v0.0.29.apk", "Improved playback")
            self.assertEqual(29, manifest["versionCode"])
            self.assertEqual("0.0.29", manifest["versionName"])
            self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), manifest["apkSha256"])
            self.assertEqual(apk.stat().st_size, manifest["apkBytes"])
            self.assertEqual("https://github.com/boxcreate/boxlore/releases/download/v0.0.29/boxlore-v0.0.29.apk", manifest["apkUrl"])
            self.assertEqual("Improved playback", manifest["notes"])

    def test_listener_bullets_preserved_without_notification_truncation(self):
        first = "Long authored copy " + "a" * 200
        readme = '<!-- release-whats-new:start --><ul><li>' + first + '</li><li>Fixed: Playback &amp; queues.</li></ul><!-- release-whats-new:end -->'
        self.assertEqual("- " + first + "\n- Fixed: Playback & queues.", listener_notes(readme))

    def test_listener_markdown_preserves_authored_emphasis_and_links(self):
        readme = '<!-- release-whats-new:start --><ul><li><b>Better playback</b> with <a href="https://example.com">details</a>.</li></ul><!-- release-whats-new:end -->'
        self.assertEqual('- **Better playback** with [details](https://example.com).', listener_notes(readme))

    def test_upcoming_preserves_authored_copy(self):
        readme = '<!-- release-upcoming:start --><b>Improvements:</b><ul><li><b>Library:</b> clearer controls.</li></ul><!-- release-upcoming:end -->'
        self.assertEqual('## Improvements\n- **Library:** clearer controls.', upcoming_notes(readme))
        self.assertEqual('', upcoming_notes('No upcoming region'))

    def test_preview_refresh_never_changes_binary_metadata(self):
        original = dict(schemaVersion=1, packageName='cx.aswin.boxlore', versionCode=29, apkSha256='verified')
        changed = refresh(original, '<!-- release-upcoming:start --><ul><li>Clearer controls</li></ul><!-- release-upcoming:end -->')
        self.assertEqual(original, {k: v for k, v in changed.items() if k != 'upcoming'})
        self.assertEqual('- Clearer controls', changed['upcoming'])
        with self.assertRaises(ValueError): refresh(dict(original, testOnly=True), '')

    def test_invalid_asset_and_empty_apk_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "candidate.apk"
            apk.write_bytes(b"a")
            gradle = 'versionCode = 29\nversionName = "0.0.29"\nminSdk = 31'
            for asset in ("../other.apk", "a.aab", "a.apk?x=1"):
                with self.assertRaises(ValueError): build_manifest(gradle, apk, asset)
            apk.write_bytes(b"")
            with self.assertRaises(ValueError): build_manifest(gradle, apk, "a.apk")

    def test_multibyte_notes_cannot_exceed_client_response_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "candidate.apk"
            apk.write_bytes(b"fixture")
            with self.assertRaisesRegex(ValueError, "64 KiB"):
                build_manifest('versionCode = 29\nversionName = "0.0.29"\nminSdk = 31', apk, "a.apk", "ࠀ" * 23_000)

    def test_notes_limit_matches_android_utf16_length(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "candidate.apk"
            apk.write_bytes(b"fixture")
            gradle = 'versionCode = 29\nversionName = "0.0.29"\nminSdk = 31'
            self.assertEqual("🎧" * 12_000, build_manifest(gradle, apk, "a.apk", "🎧" * 12_000)["notes"])
            with self.assertRaisesRegex(ValueError, "24,000"):
                build_manifest(gradle, apk, "a.apk", "🎧" * 12_001)

    def test_workflow_queues_metadata_writers_and_uses_available_refresh_token(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/changelog-on-merge.yml").read_text()
        self.assertEqual(4, workflow.count("queue: max"))
        self.assertEqual(3, workflow.count("group: boxlore-release-metadata"))
        self.assertIn("group: boxlore-release-preparation", workflow)
        refresh_job = workflow.split("  refresh-latest-artifacts:", 1)[1]
        self.assertNotIn("steps.app-token.outputs.token", refresh_job)
        manifest_step = refresh_job.split("- name: Publish verified update manifest", 1)[1]
        self.assertIn("GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}", manifest_step)

    def test_workflow_publishes_manifest_only_after_download_verification(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/changelog-on-merge.yml").read_text()
        jobs = workflow.split("      - name: Verify README latest-download APK")[1:]
        self.assertEqual(2, len(jobs))
        for job in jobs:
            self.assertLess(job.index("exit 1"), job.index("- name: Publish verified update manifest"))
            self.assertIn("generate_update_manifest.py", job)
            self.assertIn('gh release upload "$TAG" "$RUNNER_TEMP/update.json"', job)
        self.assertIn(":app:bundlePlayRelease", workflow)
        self.assertNotIn("bundleRelease", workflow)


if __name__ == "__main__": unittest.main()
