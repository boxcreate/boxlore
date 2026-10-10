#!/usr/bin/env python3
"""Create the direct-update manifest from already-verified release inputs. No publication."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
from pathlib import Path

from prepare_release import read_app_version
import update_changelog


def listener_markdown(fragment: str) -> str:
    """Preserve authored emphasis and links from the generated listener HTML."""
    fragment = re.sub(r'<a\b[^>]*href=[\'"]([^\'"]+)[\'"][^>]*>(.*?)</a>',
                      lambda m: '[' + re.sub(r'<[^>]+>', '', m[2]) + '](' + html.unescape(m[1]) + ')',
                      fragment, flags=re.DOTALL | re.IGNORECASE)
    for tag, marker in (("b", "**"), ("strong", "**"), ("i", "*"), ("em", "*"), ("code", "`")):
        fragment = re.sub(r'</?' + tag + r'\b[^>]*>', lambda _: marker, fragment, flags=re.IGNORECASE)
    fragment = re.sub(r'<br\s*/?>', '\n', fragment, flags=re.IGNORECASE)
    return html.unescape(re.sub(r'<[^>]+>', '', fragment)).strip()


def listener_notes(readme: str, tag: str | None = None) -> str:
    region = update_changelog._extract_marked_region(
        readme, update_changelog.RELEASE_WHATS_NEW_START,
        update_changelog.RELEASE_WHATS_NEW_END,
    )
    if not region:
        return ""
    metadata = update_changelog._parse_release_meta(region)
    if tag and metadata and metadata[0] != tag:
        return ""  # An artifacts-only release must not claim the previous release's notes.
    # Preserve every authored listener bullet, without shortening or rewriting.
    return "\n".join("- " + listener_markdown(bullet) for bullet in re.findall(
        r"<li\b[^>]*>(.*?)</li>", region, flags=re.DOTALL,
    ))


def upcoming_notes(readme: str) -> str:
    region = update_changelog._extract_marked_region(
        readme, update_changelog.RELEASE_UPCOMING_START, update_changelog.RELEASE_UPCOMING_END,
    ) or ""
    parts = []
    for match in re.finditer(r'<b\b[^>]*>(.*?)</b>|<li\b[^>]*>(.*?)</li>', region, flags=re.DOTALL):
        if match[1] is not None:
            parts.append("\n## " + listener_markdown(match[1]).rstrip(":"))
        else:
            parts.append("- " + listener_markdown(match[2]))
    value = "\n".join(parts).strip() if "<li" in region else ""
    if len(value.encode("utf-16-le")) // 2 > 6_000:
        raise ValueError("Upcoming preview exceeds 6,000 characters")
    return value


def build_manifest(gradle: str, apk: Path, asset: str, notes: str = "") -> dict:
    version = read_app_version(gradle)
    if not re.fullmatch(r"[A-Za-z0-9_.-]+\.apk", asset):
        raise ValueError("APK asset must be a plain .apk filename")
    sdk = re.search(r"\bminSdk\s*=\s*(\d+)", gradle)
    if sdk is None or int(sdk.group(1)) < 31:
        raise ValueError("Could not resolve supported minimum Android version")
    size = apk.stat().st_size
    if not 0 < size <= 512 * 1024 * 1024:
        raise ValueError("APK is missing, empty or larger than the updater limit")
    if len(notes.encode("utf-16-le")) // 2 > 24_000:
        raise ValueError("Release notes exceed 24,000 characters; supply concise reviewed copy")
    with apk.open("rb") as binary:
        digest = hashlib.file_digest(binary, "sha256").hexdigest()
    base = "https://github.com/boxcreate/boxlore/releases"
    manifest = {
        "schemaVersion": 1, "packageName": "cx.aswin.boxlore",
        "versionCode": version.code, "versionName": version.name,
        "minSdk": int(sdk.group(1)), "apkUrl": f"{base}/download/{version.tag}/{asset}",
        "apkSha256": digest, "apkBytes": size, "notesUrl": f"{base}/tag/{version.tag}",
        "notes": notes,
    }
    if len(encode_manifest(manifest)) > 64 * 1024:
        raise ValueError("Update manifest exceeds 64 KiB; shorten the reviewed release notes")
    return manifest


def encode_manifest(manifest: dict) -> bytes:
    return (json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--asset", required=True)
    parser.add_argument("--gradle", type=Path, default=Path("app/build.gradle.kts"))
    parser.add_argument("--readme", type=Path, default=Path("README.md"))
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        gradle = args.gradle.read_text()
        manifest = build_manifest(gradle, args.apk, args.asset,
                                  listener_notes(args.readme.read_text(), read_app_version(gradle).tag))
        manifest["upcoming"] = upcoming_notes(args.readme.read_text())
        encoded = encode_manifest(manifest)
        if len(encoded) > 64 * 1024:
            raise ValueError("Update manifest exceeds 64 KiB with upcoming preview")
        args.output.write_bytes(encoded)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Update manifest not prepared: {error}. Fix the candidate and rerun publication; no update announcement was sent.\n")


if __name__ == "__main__":
    main()
