"""Refresh the listener text in a verified update manifest: What's new and Coming next from the README.

Never changes binary metadata. What's new changes only when the README's What's New is for the
manifest's own version, so a quiet release never shows the previous release's notes.
"""
import argparse
import json
from pathlib import Path
import update_changelog
from generate_update_manifest import encode_manifest, listener_notes, upcoming_notes


def refresh(manifest, readme):
    if manifest.get("schemaVersion") != 1 or manifest.get("packageName") != "cx.aswin.boxlore" or manifest.get("testOnly"):
        raise ValueError("Expected a shipping update manifest")
    result = dict(manifest, upcoming=upcoming_notes(readme))
    region = update_changelog._extract_marked_region(
        readme, update_changelog.RELEASE_WHATS_NEW_START, update_changelog.RELEASE_WHATS_NEW_END,
    )
    meta = update_changelog._parse_release_meta(region) if region else None
    tag = f"v{manifest.get('versionName')}"
    if meta is not None and meta[0] == tag:
        # Even when empty: a correction may remove the only line the release had.
        result["notes"] = listener_notes(readme, tag)
    if len(encode_manifest(result)) > 64 * 1024:
        raise ValueError("Update feed exceeds 64 KiB; shorten the listener notes")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--readme", type=Path, default=Path("README.md"))
    args = parser.parse_args()
    args.manifest.write_bytes(encode_manifest(refresh(json.loads(args.manifest.read_text()), args.readme.read_text())))
