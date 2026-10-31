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


# The app's own bounds (UpdateManifest): longer notes make it reject the whole feed.
NOTES_LIMIT = 24_000
UPCOMING_LIMIT = 6_000
NOTES_MORE = "- And more: see the full release notes."
UPCOMING_MORE = "- And more in the next release."


def _utf16_length(value: str) -> int:
    return len(value.encode("utf-16-le")) // 2


def fit_lines(lines: list[str], limit: int, more: str) -> str:
    """The lines, or as many whole lines as fit in [limit] with [more] after them; never a cut line."""
    value = "\n".join(lines).strip()
    if _utf16_length(value) <= limit:
        return value
    kept: list[str] = []
    for line in lines:
        if _utf16_length("\n".join([*kept, line, more]).strip()) > limit:
            break
        kept.append(line)
    while kept and kept[-1].lstrip().startswith("#"):
        kept.pop()  # A heading with nothing under it.
    return "\n".join([*kept, more]).strip()


def listener_markdown(fragment: str) -> str:
    """Preserve authored emphasis and links from the generated listener HTML; PR badges (links without text) go."""
    def link(match: re.Match) -> str:
        text = re.sub(r'<[^>]+>', '', match[2]).strip()
        return f'[{text}]({html.unescape(match[1])})' if text else ''
    fragment = re.sub(r'<a\b[^>]*href=[\'"]([^\'"]+)[\'"][^>]*>(.*?)</a>', link,
                      fragment, flags=re.DOTALL | re.IGNORECASE)
    for tag, marker in (("b", "**"), ("strong", "**"), ("i", "*"), ("em", "*"), ("code", "`")):
        fragment = re.sub(r'</?' + tag + r'\b[^>]*>', lambda _: marker, fragment, flags=re.IGNORECASE)
    fragment = re.sub(r'<br\s*/?>', '\n', fragment, flags=re.IGNORECASE)
    return re.sub(r'[ \t]{2,}', ' ', html.unescape(re.sub(r'<[^>]+>', '', fragment))).strip()


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
    # Every authored listener bullet, without shortening or rewriting; whole bullets only if they can't all fit.
    return fit_lines(["- " + listener_markdown(bullet) for bullet in re.findall(
        r"<li\b[^>]*>(.*?)</li>", region, flags=re.DOTALL,
    )], NOTES_LIMIT, NOTES_MORE)


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
    if "<li" not in region:
        return ""
    # Whole lines that fit the app's Coming next bound, so a long list never fails the feed.
    return fit_lines([line for part in parts for line in part.strip("\n").splitlines() or [""]], UPCOMING_LIMIT, UPCOMING_MORE)


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
    if _utf16_length(notes) > NOTES_LIMIT:
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
