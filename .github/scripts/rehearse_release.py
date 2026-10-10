"""Exercise release-note preparation offline, writing only preview artifacts.

This is deliberately independent of publishing, FCM and repository credentials.
It exercises local reviewed copy; it cannot discover unrecorded merged PRs.
"""

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import manual_dispatch
import prepare_release as release
import update_changelog as copy


def candidate(gradle, changelog, readme, *, operation, bump, release_date):
    current = release.read_app_version(gradle)
    if operation == "refresh-latest-artifacts":
        return {
            "report": {
                "operation": operation, "current_version": current.name,
                "candidate_version": current.name, "candidate_code": current.code,
                "version_bumped": False, "announcement": None,
                "guidance": "A same-version refresh stays quiet. No release alert is prepared.",
            },
            "gradle": gradle, "changelog": changelog, "readme": readme,
        }
    if operation != "prepare-release":
        raise ValueError("Rehearsal supports prepare-release or refresh-latest-artifacts")
    # Real copy ordering and promotion functions; only in-memory copies are changed.
    groups = copy.reviewed_readme_groups(changelog)
    reviewed_readme = copy._update_readme(readme, groups=groups)
    target = release.bump_version(current, bump)
    promoted_changelog = release.promote_changelog(changelog, target, release_date)
    promoted_readme = release.promote_readme(reviewed_readme, target, release_date)
    message = manual_dispatch.argument_parser().parse_args([
        "--title", f"boxlore {target.tag} is here",
        "--body", release.notification_body(promoted_readme, target),
        "--type", "both", "--target", "debug_users", "--test-mode", "true",
        "--preview-only", "true", "--action-label", "Download",
        "--route", "https://example.invalid/rehearsal.apk",
    ])
    plan = manual_dispatch.notification_plan(message)
    return {
        "report": {
            "operation": operation, "current_version": current.name,
            "candidate_version": target.name, "candidate_code": target.code,
            "version_bumped": True, "announcement": {**plan, "mode": "offline-preview", "sent": False},
            "guidance": "Review the candidate notes and message. No version files, release or notifications were changed.",
        },
        "gradle": release.replace_gradle_version(gradle, current, target),
        "changelog": promoted_changelog, "readme": promoted_readme,
    }


def write_preview(root, output, *, operation="prepare-release", bump="patch", release_date=None):
    root, output = Path(root).resolve(), Path(output).resolve()
    # Keep artifacts away from the three source-file locations, including symlinks.
    destinations = [output / name for name in ("report.json", "candidate.gradle.kts", "candidate-changelog.md", "candidate-readme.md")]
    sources = [root / name for name in ("app/build.gradle.kts", "CHANGELOG.md", "README.md")]
    if any(dest.resolve() in {source.resolve() for source in sources} for dest in destinations):
        raise ValueError("Choose a separate preview directory; source files must stay unchanged")
    preview = candidate(
        *(source.read_text(encoding="utf-8") for source in sources),
        operation=operation, bump=bump,
        release_date=release_date or datetime.now(timezone.utc).date().isoformat(),
    )
    output.mkdir(parents=True, exist_ok=True)
    for path, content in zip(destinations, (
        json.dumps(preview["report"], ensure_ascii=False, indent=2) + "\n",
        preview["gradle"], preview["changelog"], preview["readme"],
    )):
        path.write_text(content, encoding="utf-8")
    return preview["report"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", required=True)
    parser.add_argument("--operation", choices=("prepare-release", "refresh-latest-artifacts"), default="prepare-release")
    parser.add_argument("--bump", choices=("patch", "minor", "major"), default="patch")
    args = parser.parse_args()
    try:
        report = write_preview(args.root, args.output, operation=args.operation, bump=args.bump)
        print(report["guidance"])
    except (ValueError, OSError) as error:
        parser.exit(1, f"Rehearsal stopped: {error}\nFix the indicated copy or file, then rerun. Nothing was published or sent.\n")
