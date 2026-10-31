#!/usr/bin/env python3
"""Append merged-PR developer notes to CHANGELOG.md and sync README Upcoming Changes.

Developer copy in the PR body (`<!-- release-copy:changelog -->`) is pasted verbatim into
CHANGELOG, tagged `<!-- copy:locked -->`, and ordered without rewriting. Listener copy lives in
the notes list (`release-notes/upcoming.md`, see release_notes.py), which PRs edit in place;
README Upcoming is rendered from it, and prepare_release promotes it into What's New as-is.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

import release_notes

CHANGELOG_PATH = Path("CHANGELOG.md")
README_PATH = Path("README.md")
UPCOMING_CHANGES_START = "<!-- upcoming-changes:start -->"
UPCOMING_CHANGES_END = "<!-- upcoming-changes:end -->"
RELEASE_UPCOMING_START = "<!-- release-upcoming:start -->"
RELEASE_UPCOMING_END = "<!-- release-upcoming:end -->"
RELEASE_WHATS_NEW_START = "<!-- release-whats-new:start -->"
RELEASE_WHATS_NEW_END = "<!-- release-whats-new:end -->"
DOWNLOAD_APK_START = "<!-- download-apk:start -->"
DOWNLOAD_APK_END = "<!-- download-apk:end -->"
EMPTY_UPCOMING_TEXT = (
    "New features and improvements for the next release are currently in development."
)
RELEASE_META_RE = re.compile(
    r"<!--\s*release-meta:\s*version=(v(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*))"
    r"\s+date=(\d{4}-\d{2}-\d{2})\s*-->"
)
CATEGORY_ORDER = ("Added", "Changed", "Fixed", "Deprecated", "Removed", "Security")
README_GROUP_EMOJI = {
    "Highlights": "✨",
    "New": "🆕",
    "Improved": "⚡",
    "Fixed": "🐛",
    "Critical": "🚨",
    "New features": "🆕",
    "Improvements": "⚡",
    "Fixes": "🐛",
    "Security": "🔒",
    "Other": "📦",
}
DEFAULT_GITHUB_REPOSITORY = "boxcreate/boxlore"

# Required: exactly one user-impact level. Optional: backend-change (pairable).
USER_IMPACT_LABELS = (
    "user-impact-critical",
    "user-impact-high",
    "user-impact-medium",
    "user-impact-low",
    "no-user-impact",
)
USER_IMPACT_SCORE = {
    "user-impact-critical": 100,
    "user-impact-high": 95,
    "user-impact-medium": 70,
    "user-impact-low": 45,
    "no-user-impact": 12,
}
# Legacy labels still recognized when reading older CHANGELOG markers / open PRs.
USER_IMPACT_ALIASES = {
    "user-impact-critical": "user-impact-critical",
    "user impact critical": "user-impact-critical",
    "user-impact-critical-fix": "user-impact-critical",
    "user-impact-high": "user-impact-high",
    "user impact high": "user-impact-high",
    "user-impact": "user-impact-high",
    "user impact": "user-impact-high",
    "user-impact-medium": "user-impact-medium",
    "user impact medium": "user-impact-medium",
    "user-impact-low": "user-impact-low",
    "user impact low": "user-impact-low",
    "no-user-impact": "no-user-impact",
    "no user impact": "no-user-impact",
    "non-user-impact": "no-user-impact",
    "non user impact": "no-user-impact",
    "backend-fix": "no-user-impact",  # old exclusive label → no user + treat via backend flag
}
BACKEND_ALIASES = {
    "backend-change",
    "backend change",
    "backend-fix",
    "backend fix",
}
IMPACT_MARKER_RE = re.compile(
    r"<!--\s*impact:([a-z0-9\-]+)(?:\+(backend-change))?\s*-->",
    re.IGNORECASE,
)
COPY_LOCKED_MARKER = "<!-- copy:locked -->"
COPY_LOCKED_RE = re.compile(r"<!--\s*copy:locked\s*-->", re.IGNORECASE)
PR_CHANGELOG_COPY_START = "<!-- release-copy:changelog:start -->"
PR_CHANGELOG_COPY_END = "<!-- release-copy:changelog:end -->"
README_COPY_BLOCK_RE = re.compile(
    r"<!--\s*readme-copy:start\s+pr=(\d+)\s*(?:-->)?\s*(.*?)\s*"
    r"(?:<!--\s*)?readme-copy:end\s+pr=\1\s*-->",
    re.DOTALL | re.IGNORECASE,
)
PLACEHOLDER_BULLET_RE = re.compile(
    r"^(?:[-*]\s*)?(?:tbd|todo|n/?a|…|\.{3}|-)?\s*$",
    re.IGNORECASE,
)
KEEP_A_CHANGELOG_HEADER_RE = re.compile(
    r"^###\s+(Added|Changed|Fixed|Deprecated|Removed|Security)\s*$",
    re.IGNORECASE,
)
CATEGORY_BY_LOWER = {name.lower(): name for name in CATEGORY_ORDER}


def _github_repository() -> str:
    return os.environ.get("GITHUB_REPOSITORY", DEFAULT_GITHUB_REPOSITORY).strip() or DEFAULT_GITHUB_REPOSITORY


def _normalize_token(raw: str) -> str:
    key = re.sub(r"[\s_]+", "-", raw.strip().lower())
    return re.sub(r"-+", "-", key).strip("-")


def _normalize_user_impact(raw: str) -> str | None:
    key = _normalize_token(raw)
    if key in USER_IMPACT_LABELS:
        return key
    spaced = key.replace("-", " ")
    return USER_IMPACT_ALIASES.get(key) or USER_IMPACT_ALIASES.get(spaced)


def _is_backend_label(raw: str) -> bool:
    key = _normalize_token(raw)
    spaced = key.replace("-", " ")
    return key in BACKEND_ALIASES or spaced in BACKEND_ALIASES


def _impact_marker(impact: str, backend_change: bool = False) -> str:
    suffix = "+backend-change" if backend_change else ""
    return f"<!-- impact:{impact}{suffix} -->"


def _extract_impact_tags(text: str) -> tuple[str | None, bool]:
    match = IMPACT_MARKER_RE.search(text)
    if not match:
        return None, False
    impact = _normalize_user_impact(match.group(1))
    backend = bool(match.group(2)) or "backend-change" in match.group(0).lower()
    # Support legacy markers without levels.
    if impact is None and match.group(1):
        impact = _normalize_user_impact(match.group(1))
    return impact, backend


def _strip_impact_marker(text: str) -> str:
    return IMPACT_MARKER_RE.sub("", text).strip()


def _is_copy_locked(text: str) -> bool:
    return bool(COPY_LOCKED_RE.search(text or ""))


def _strip_copy_locked(text: str) -> str:
    return COPY_LOCKED_RE.sub("", text or "").strip()


def _strip_changelog_markers(text: str) -> str:
    return _strip_copy_locked(_strip_impact_marker(text)).strip()


def _attach_impact(
    text: str,
    impact: str | None,
    backend_change: bool = False,
    copy_locked: bool = False,
) -> str:
    was_locked = copy_locked or _is_copy_locked(text)
    cleaned = _strip_changelog_markers(text).strip()
    if not cleaned:
        return cleaned
    parts = [cleaned]
    if impact:
        parts.append(_impact_marker(impact, backend_change))
    if was_locked:
        parts.append(COPY_LOCKED_MARKER)
    return " ".join(parts)


def _labels_from_env_or_payload(payload: dict | None = None) -> list[str]:
    if payload is not None:
        labels = payload.get("labels") or []
        names: list[str] = []
        if isinstance(labels, list):
            for item in labels:
                if isinstance(item, str):
                    names.append(item)
                elif isinstance(item, dict) and item.get("name"):
                    names.append(str(item["name"]))
        return names

    raw_json = os.environ.get("PR_LABELS_JSON", "").strip()
    if raw_json:
        try:
            data = json.loads(raw_json)
        except json.JSONDecodeError:
            data = None
        if isinstance(data, list):
            names = []
            for item in data:
                if isinstance(item, str):
                    names.append(item)
                elif isinstance(item, dict) and item.get("name"):
                    names.append(str(item["name"]))
            return names

    csv = os.environ.get("PR_LABELS", "").strip()
    if csv:
        return [part.strip() for part in csv.split(",") if part.strip()]
    return []


def _pr_suffix(pr_number: int) -> str:
    repo = _github_repository()
    return f"([#{pr_number}](https://github.com/{repo}/pull/{pr_number}))"


def _pr_already_present(content: str, pr_number: int) -> bool:
    if f"(#{pr_number})" in content:
        return True
    return bool(
        re.search(
            rf"\[#{pr_number}\]\(https://github\.com/[^/]+/[^/]+/pull/{pr_number}\)",
            content,
        )
    )


def _is_placeholder_bullet(text: str) -> bool:
    return bool(PLACEHOLDER_BULLET_RE.match((text or "").strip()))


def _default_changelog_category(pr_title: str, impact: str | None) -> str:
    if impact == "user-impact-critical":
        return "Fixed"
    title = (pr_title or "").strip()
    conventional = re.match(
        r"^(?P<type>feat|fix|chore|ci|docs|refactor|test|perf|build|style)"
        r"(?:\([^)]*\))?!?:",
        title,
        flags=re.IGNORECASE,
    )
    if conventional:
        kind = conventional.group("type").lower()
        if kind == "feat":
            return "Added"
        if kind == "fix":
            return "Fixed"
    return "Changed"


def parse_pr_changelog_copy(
    body: str,
    pr_title: str = "",
    impact: str | None = None,
) -> dict[str, list[str]]:
    """Keep a Changelog bullets from the PR body's verbatim changelog region."""
    inner = (_extract_marked_region(body or "", PR_CHANGELOG_COPY_START, PR_CHANGELOG_COPY_END) or "").strip()
    if not inner:
        return {}

    sections: dict[str, list[str]] = {}
    current: str | None = None
    for line in inner.splitlines():
        header = KEEP_A_CHANGELOG_HEADER_RE.match(line.strip())
        if header:
            current = CATEGORY_BY_LOWER[header.group(1).lower()]
            sections.setdefault(current, [])
            continue
        match = re.match(r"^[-*]\s+(.+)$", line.strip())
        if not match:
            continue
        bullet = match.group(1).strip()
        if not bullet or _is_placeholder_bullet(bullet):
            continue
        category = current or _default_changelog_category(pr_title, impact)
        sections.setdefault(category, []).append(bullet)
    return {key: values for key, values in sections.items() if values}


def _require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        print(f"Missing required environment variable: {name}", file=sys.stderr)
        sys.exit(1)
    return value


def _extract_pr_number(bullet: str) -> int | None:
    # 1. Full markdown pull request link: [#1019](https://github.com/.../pull/1019)
    match = re.search(r"\[#(\d+)\]\(https://github\.com/[^/]+/[^/]+/pull/\1\)", bullet)
    if match:
        return int(match.group(1))
    # 2. Trailing PR reference at the end of the bullet: (#1019) before comments or end of string
    match = re.search(r"\(#(\d+)\)\s*(?:<!--.*-->)?\s*$", bullet)
    if match:
        return int(match.group(1))
    # 3. Any markdown pull link
    match = re.search(r"\[#(\d+)\]\([^)]*pull/\1\)", bullet)
    if match:
        return int(match.group(1))
    # 4. Fallback to any (#123)
    match = re.search(r"\(#(\d+)\)", bullet)
    if match:
        return int(match.group(1))
    return None


def _unreleased_bounds(content: str) -> tuple[int, int] | None:
    match = re.search(r"^## \[Unreleased\]\s*$", content, flags=re.MULTILINE)
    if not match:
        return None
    start = match.end()
    next_version = re.search(r"^## \[", content[start:], flags=re.MULTILINE)
    end = start + next_version.start() if next_version else len(content)
    return start, end


def _unreleased_raw(content: str) -> str:
    bounds = _unreleased_bounds(content)
    if bounds is None:
        return ""
    start, end = bounds
    return content[start:end]


def _collect_readme_copy_blocks(unreleased_block: str) -> dict[int, str]:
    return {
        int(match.group(1)): match.group(0).strip()
        for match in README_COPY_BLOCK_RE.finditer(unreleased_block)
    }


def _strip_readme_copy_blocks(text: str) -> str:
    stripped = README_COPY_BLOCK_RE.sub("", text)
    return re.sub(r"\n{3,}", "\n\n", stripped).strip()


def _write_unreleased_region(
    content: str,
    sections: dict[str, list[str]],
    copy_blocks: dict[int, str] | None = None,
) -> str:
    bounds = _unreleased_bounds(content)
    if bounds is None:
        raise ValueError("Could not find '## [Unreleased]' header in CHANGELOG.md")
    start, end = bounds
    if copy_blocks is None:
        copy_blocks = _collect_readme_copy_blocks(content[start:end])
    rendered = _render_unreleased(sections)
    extras = "\n\n".join(
        copy_blocks[number] for number in sorted(copy_blocks) if copy_blocks[number].strip()
    )
    if rendered and extras:
        replacement = f"\n{rendered}\n\n{extras}\n"
    elif rendered:
        replacement = f"\n{rendered}\n"
    elif extras:
        replacement = f"\n{extras}\n"
    else:
        replacement = "\n"
    updated = content[:start] + replacement + content[end:]
    if not updated.endswith("\n"):
        updated += "\n"
    return updated


def _replace_unreleased_sections(
    content: str,
    sections: dict[str, list[str]],
    copy_blocks: dict[int, str] | None = None,
) -> str:
    return _write_unreleased_region(content, sections, copy_blocks=copy_blocks)


def _bullet_sort_key(bullet: str) -> tuple[int, int]:
    impact, _ = _extract_impact_tags(bullet)
    score = USER_IMPACT_SCORE.get(impact or "", 0)
    pr_number = _extract_pr_number(bullet) or 0
    return (-score, pr_number)


def _sort_section_bullets(sections: dict[str, list[str]]) -> dict[str, list[str]]:
    return {
        category: sorted(bullets, key=_bullet_sort_key)
        for category, bullets in sections.items()
        if bullets
    }


def _parse_unreleased_sections(unreleased_block: str) -> dict[str, list[str]]:
    unreleased_block = README_COPY_BLOCK_RE.sub("", unreleased_block)
    sections: dict[str, list[str]] = {}
    current: str | None = None
    for line in unreleased_block.splitlines():
        header = re.match(r"^### (Added|Changed|Fixed|Deprecated|Removed|Security)\s*$", line.strip())
        if header:
            current = header.group(1)
            sections.setdefault(current, [])
            continue
        if current and line.startswith("- "):
            sections[current].append(line[2:].strip())
    return sections


def _render_unreleased(sections: dict[str, list[str]]) -> str:
    lines: list[str] = []
    for category in CATEGORY_ORDER:
        bullets = sections.get(category, [])
        if not bullets:
            continue
        lines.append(f"### {category}")
        for bullet in bullets:
            lines.append(f"- {bullet}")
    return "\n".join(lines)


def _merge_entries(
    existing: dict[str, list[str]],
    incoming: dict[str, list[str]],
    pr_number: int,
    impact: str | None = None,
    backend_change: bool = False,
    copy_locked: bool = False,
) -> dict[str, list[str]]:
    merged = {key: list(values) for key, values in existing.items()}
    suffix = _pr_suffix(pr_number)

    for category, bullets in incoming.items():
        merged.setdefault(category, [])
        seen = set(merged[category])
        for bullet in bullets:
            tagged = bullet if _pr_already_present(bullet, pr_number) else f"{bullet} {suffix}".strip()
            tagged = _attach_impact(
                tagged,
                impact,
                backend_change=backend_change,
                copy_locked=copy_locked,
            )
            if tagged not in seen:
                merged[category].append(tagged)
                seen.add(tagged)
    return merged


def _extract_unreleased_sections(content: str) -> dict[str, list[str]]:
    match = re.search(r"^## \[Unreleased\]\s*$", content, flags=re.MULTILINE)
    if not match:
        return {}

    start = match.end()
    next_version = re.search(r"^## \[", content[start:], flags=re.MULTILINE)
    end = start + next_version.start() if next_version else len(content)
    return _parse_unreleased_sections(content[start:end])


PR_LINK_SUFFIX_RE = re.compile(r"((?:\s*\(\[#\d+\]\([^)]+\)\))+)\s*$")


def _bullet_to_html_list_item(bullet: str) -> str:
    """A bullet as a README list item; its trailing PR links become badges, one per PR."""
    cleaned = _strip_changelog_markers(bullet.strip())
    match = PR_LINK_SUFFIX_RE.search(cleaned)
    if match:
        badges = " ".join(
            f'<a href="{url}"><img src="https://img.shields.io/badge/PR-{number}-6750A4?style=flat-square" '
            f'alt="PR #{number}" height="18"/></a>'
            for number, url in re.findall(r"\(\[#(\d+)\]\(([^)]+)\)\)", match.group(1))
        )
        return f"<li>{cleaned[: match.start()].strip()} {badges}</li>"
    return f"<li>{cleaned}</li>"


def _extract_marked_region(content: str, start_marker: str, end_marker: str) -> str | None:
    start = content.find(start_marker)
    end = content.find(end_marker)
    if start < 0 or end < 0 or end <= start:
        return None
    return content[start + len(start_marker) : end]


def _replace_marked_region(
    content: str,
    start_marker: str,
    end_marker: str,
    new_inner: str,
) -> str:
    start = content.find(start_marker)
    end = content.find(end_marker)
    if start < 0 or end < 0 or end <= start:
        raise ValueError(f"Missing markers {start_marker!r} … {end_marker!r}")
    inner = new_inner.strip("\n")
    replacement = f"{start_marker}\n{inner}\n{end_marker}"
    return content[:start] + replacement + content[end + len(end_marker) :]


def _parse_release_meta(whats_new_inner: str) -> tuple[str, str] | None:
    match = RELEASE_META_RE.search(whats_new_inner)
    if not match:
        return None
    return match.group(1), match.group(2)


def _render_whats_new_inner(version_tag: str, release_date: str, body_html: str) -> str:
    body = body_html.strip()
    return (
        f"<!-- release-meta: version={version_tag} date={release_date} -->\n"
        f"{body}"
    )


def _render_readme_upcoming_body(groups: list[dict[str, list[str]]] | None = None, bullets: list[str] | None = None) -> str:
    if groups:
        visible = [g for g in groups if g.get("bullets")]
        if not visible:
            return EMPTY_UPCOMING_TEXT

        sections: list[str] = []
        for group in visible:
            heading = group["heading"]
            emoji = README_GROUP_EMOJI.get(heading, "•")
            items = "\n".join(_bullet_to_html_list_item(b) for b in group["bullets"])
            sections.append(
                f"<b>{emoji} {heading}:</b>\n<ul align=\"left\">\n{items}\n</ul>"
            )
        return "\n".join(sections)
    if bullets:
        items = "\n".join(_bullet_to_html_list_item(b) for b in bullets)
        return f'<ul align="left">\n{items}\n</ul>'
    return EMPTY_UPCOMING_TEXT


def _format_upcoming_inner(body: str) -> str:
    return body.strip() or EMPTY_UPCOMING_TEXT


def _render_release_notes_shell(
    *,
    upcoming_inner: str,
    whats_new_inner: str | None,
) -> str:
    upcoming = _format_upcoming_inner(upcoming_inner)
    whats_new_block = ""
    if whats_new_inner and whats_new_inner.strip():
        meta = _parse_release_meta(whats_new_inner) or ("v0.0.0", "1970-01-01")
        version_tag, release_date = meta
        body = whats_new_inner.strip()
        whats_new_block = (
            f"\n\n### What's New · `{version_tag}` · {release_date}\n\n"
            f"{RELEASE_WHATS_NEW_START}\n"
            f"{body}\n"
            f"{RELEASE_WHATS_NEW_END}\n"
        )
    return (
        f"{UPCOMING_CHANGES_START}\n\n"
        "## Release notes\n\n"
        "### Upcoming\n\n"
        f"{RELEASE_UPCOMING_START}\n"
        f"{upcoming.strip()}\n"
        f"{RELEASE_UPCOMING_END}\n"
        f"{whats_new_block}\n"
        f"{UPCOMING_CHANGES_END}"
    )


def _render_readme_upcoming_block(
    content: str,
    groups: list[dict[str, list[str]]] | None = None,
    bullets: list[str] | None = None,
    whats_new_inner: str | None = None,
) -> str:
    """Rewrite the Upcoming body; keep the latest What's New region unless [whats_new_inner] replaces it."""
    body = _render_readme_upcoming_body(groups=groups, bullets=bullets)
    existing_whats_new = whats_new_inner or _extract_marked_region(
        content,
        RELEASE_WHATS_NEW_START,
        RELEASE_WHATS_NEW_END,
    )
    # Legacy fallback: old <details> What's New while migrating.
    if existing_whats_new is None:
        legacy = re.search(
            r"<details(?:\s+open)?>\s*"
            r"<summary><b>🎉 What's New \((v[^)]+)\)\s*-\s*(\d{4}-\d{2}-\d{2})</b></summary>"
            r"(.*?)</details>",
            content,
            flags=re.DOTALL,
        )
        if legacy:
            existing_whats_new = _render_whats_new_inner(
                legacy.group(1),
                legacy.group(2),
                legacy.group(3).strip(),
            )
    return _render_release_notes_shell(
        upcoming_inner=body,
        whats_new_inner=existing_whats_new,
    )


def _update_readme(
    content: str,
    groups: list[dict[str, list[str]]] | None = None,
    bullets: list[str] | None = None,
    whats_new_inner: str | None = None,
) -> str:
    block = _render_readme_upcoming_block(content, groups=groups, bullets=bullets, whats_new_inner=whats_new_inner)
    pattern = re.compile(
        re.escape(UPCOMING_CHANGES_START) + r".*?" + re.escape(UPCOMING_CHANGES_END),
        flags=re.DOTALL,
    )
    if pattern.search(content):
        # Authored text may contain backslashes; never treat it as a regex replacement.
        updated = pattern.sub(lambda _: block, content, count=1)
    else:
        anchor = re.search(
            r"^(<!-- upcoming-changes:start -->|## Search\b|<h2 id=\"features\">)",
            content,
            flags=re.MULTILINE,
        )
        if not anchor:
            raise ValueError(
                "Could not find Upcoming Changes markers or insertion anchor in README.md"
            )
        updated = content[: anchor.start()] + block + "\n\n" + content[anchor.start() :]

    if not updated.endswith("\n"):
        updated += "\n"
    return updated


def _update_changelog(
    content: str,
    entries: dict[str, list[str]],
    pr_number: int,
    impact: str | None = None,
    backend_change: bool = False,
    copy_locked: bool = False,
) -> tuple[str, bool]:
    if _pr_already_present(content, pr_number):
        print(f"CHANGELOG already contains entry for PR #{pr_number}; skipping merge.")
        return content, False

    bounds = _unreleased_bounds(content)
    if bounds is None:
        raise ValueError("Could not find '## [Unreleased]' header in CHANGELOG.md")
    start, end = bounds
    unreleased_block = content[start:end]
    existing = _parse_unreleased_sections(unreleased_block)
    copy_blocks = _collect_readme_copy_blocks(unreleased_block)
    merged = _merge_entries(
        existing,
        entries,
        pr_number,
        impact=impact,
        backend_change=backend_change,
        copy_locked=copy_locked,
    )
    return _write_unreleased_region(content, merged, copy_blocks), True


NOTES_COVERED_RE = re.compile(r"-\s*\[[xX]\]\s*Listener notes already cover this", re.IGNORECASE)


def validate_release_copy(
    pr_title: str,
    pr_body: str,
    labels: list[str] | None,
) -> tuple[str, bool, dict[str, list[str]]]:
    """Validate the PR's impact label and developer copy; never synthesize missing wording."""
    impacts = [
        impact
        for label in labels or []
        if _normalize_token(label) != "backend-change"
        and (impact := _normalize_user_impact(label))
    ]
    if len(impacts) != 1:
        raise ValueError(
            "Add exactly one PR impact label: " + ", ".join(USER_IMPACT_LABELS) +
            ". Then rerun release-copy validation."
        )
    impact = impacts[0]
    backend = any(_is_backend_label(label) for label in labels or [])
    inner = _extract_marked_region(pr_body, PR_CHANGELOG_COPY_START, PR_CHANGELOG_COPY_END)
    if pr_body.count(PR_CHANGELOG_COPY_START) != 1 or pr_body.count(PR_CHANGELOG_COPY_END) != 1 or inner is None:
        raise ValueError(
            "Keep exactly one complete release-copy:changelog region in the PR body. "
            "Use the PR template markers, then rerun validation."
        )
    for line in inner.splitlines():
        bullet = re.match(r"^[-*]\s*(.*)$", line.strip())
        if bullet and _is_placeholder_bullet(bullet.group(1)):
            raise ValueError(
                "Replace or remove the empty/TBD bullet in release-copy:changelog. "
                "Every changelog entry must have authored wording."
            )
    entries = parse_pr_changelog_copy(pr_body, pr_title, impact)
    if not entries:
        raise ValueError(
            "Fill the PR's release-copy:changelog region with categorized developer bullets. "
            "Empty bullets and TBD are placeholders. Save the PR body, then rerun the failed workflow."
        )
    return impact, backend, entries


def validate_listener_notes(pr_body: str, impact: str, changed_files: set[str] | None) -> None:
    """A listener-facing PR edits the notes list (or says it already covers the change); the list keeps its rules.

    [changed_files] is the PR's file list; without it (a local run) only the list's own rules are checked.
    """
    path = release_notes.UPCOMING_PATH
    found = release_notes.problems(release_notes.load(path))
    if found:
        raise ValueError(f"{path}: " + " ".join(found))
    if impact == "no-user-impact" or changed_files is None:
        return
    if str(path) not in changed_files and not NOTES_COVERED_RE.search(pr_body or ""):
        raise ValueError(
            f"This PR is {impact} but doesn't edit {path}. Read the list and edit the line that covers this "
            "change (or add one), or tick 'Listener notes already cover this change' in the PR body when "
            "the list already says it (a fix to something not yet released, for example)."
        )


def append_changelog(
    pr_number: int,
    pr_title: str,
    pr_body: str,
    labels: list[str] | None = None,
) -> bool:
    """Store the merged PR's developer copy in [Unreleased]; an entry already there is left as it is."""
    original = CHANGELOG_PATH.read_text(encoding="utf-8")
    if _pr_already_present(original, pr_number):
        print(f"CHANGELOG already contains PR #{pr_number}; no entry rewritten.")
        return False
    impact, backend, entries = validate_release_copy(pr_title, pr_body, labels)
    updated, changed = _update_changelog(
        original, entries, pr_number,
        impact=impact, backend_change=backend, copy_locked=True,
    )
    if changed:
        CHANGELOG_PATH.write_text(updated, encoding="utf-8")
        print(f"Stored authored developer copy for PR #{pr_number}.")
    return changed


def sync_changelog_unreleased() -> bool:
    original = CHANGELOG_PATH.read_text(encoding="utf-8")
    sections = _extract_unreleased_sections(original)
    copy_blocks = _collect_readme_copy_blocks(_unreleased_raw(original))
    updated = _replace_unreleased_sections(
        original, _sort_section_bullets(sections), copy_blocks=copy_blocks,
    )
    if updated != original:
        CHANGELOG_PATH.write_text(updated, encoding="utf-8")
        print("Ordered CHANGELOG bullets by category, impact and PR; wording unchanged.")
        return True
    return False


def _checked_notes(path: Path) -> release_notes.ListenerNotes:
    notes = release_notes.load(path)
    found = release_notes.problems(notes)
    if found:
        raise ValueError(f"{path}: " + " ".join(found))
    return notes


def released_whats_new(readme: str) -> str | None:
    """README What's New rendered from the released version's notes (release-notes/vX.Y.Z.md), if kept.

    A PR that corrects a released line edits that file; this carries it to What's New and the update feed.
    """
    region = _extract_marked_region(readme, RELEASE_WHATS_NEW_START, RELEASE_WHATS_NEW_END)
    meta = _parse_release_meta(region) if region else None
    if meta is None or not release_notes.archive_path(meta[0]).is_file():
        return None
    notes = _checked_notes(release_notes.archive_path(meta[0]))
    body = _render_readme_upcoming_body(groups=release_notes.readme_groups(notes, _github_repository()))
    return _render_whats_new_inner(meta[0], meta[1], body)


def sync_readme_upcoming() -> bool:
    """Render README Upcoming from the notes list (highlights first, then New, Improved and Fixed) and
    What's New from the released version's notes."""
    original = README_PATH.read_text(encoding="utf-8")
    groups = release_notes.readme_groups(_checked_notes(release_notes.UPCOMING_PATH), _github_repository())
    updated = _update_readme(original, groups=groups, whats_new_inner=released_whats_new(original))
    if updated != original:
        README_PATH.write_text(updated, encoding="utf-8")
        print("Synced README Upcoming and What's New from the notes.")
        return True
    return False


def _load_pr_metadata(path: str) -> tuple[int, str, str, list[str]]:
    metadata_path = Path(path)
    if not metadata_path.is_file():
        raise ValueError(f"PR metadata file does not exist: {metadata_path}")

    payload = json.loads(metadata_path.read_text(encoding="utf-8"))
    if payload.get("mergedAt") is None:
        raise ValueError("Changelog append requires a merged pull request")
    if payload.get("baseRefName") != "master":
        raise ValueError("Changelog append only accepts pull requests merged into master")

    number = int(payload["number"])
    title = str(payload.get("title", "")).strip()
    body = str(payload.get("body") or "")
    if not title:
        raise ValueError("Pull request metadata is missing a title")
    return number, title, body, _labels_from_env_or_payload(payload)


def main() -> None:
    parser = argparse.ArgumentParser(description="Update CHANGELOG and README on PR merge.")
    parser.add_argument(
        "command",
        nargs="?",
        default="all",
        choices=("all", "append", "sync-changelog", "sync-readme", "validate-copy"),
        help="append: write PR to CHANGELOG; sync-changelog: re-group [Unreleased]; sync-readme: order authored README copy; validate-copy: check a PR event; all: append + sync-changelog + sync-readme",
    )
    parser.add_argument(
        "--pr-json",
        help="Path to `gh pr view --json number,title,body,mergedAt,baseRefName,labels` output for append",
    )
    args = parser.parse_args()

    if args.command == "validate-copy":
        event = json.loads(Path(_require_env("GITHUB_EVENT_PATH")).read_text(encoding="utf-8"))
        pull = event["pull_request"]
        title = str(pull.get("title") or "")
        if "[skip changelog]" in title or str(pull.get("head", {}).get("ref", "")).startswith("release/v"):
            print("Release/tooling PR opts out of changelog copy.")
            return
        # The PR's labels as the workflow read them just now (`PR_LABELS_JSON`), else as the event saw them:
        # labels applied earlier in the same job (from the PR body's checkboxes) aren't in the event.
        labels = _labels_from_env_or_payload(None) if os.environ.get("PR_LABELS_JSON", "").strip() else _labels_from_env_or_payload(pull)
        body = str(pull.get("body") or "")
        impact, _, _ = validate_release_copy(title, body, labels)
        # The PR's changed files, newline-separated, from the workflow; without them the list's rules still apply.
        raw_files = os.environ.get("PR_CHANGED_FILES")
        changed_files = {line.strip() for line in raw_files.splitlines() if line.strip()} if raw_files is not None else None
        validate_listener_notes(body, impact, changed_files)
        print("Release copy is ready: developer copy and listener notes follow the rules.")
        return
    changelog_changed = False
    changelog_ordered = False
    readme_changed = False

    if args.command in ("all", "append"):
        if args.pr_json:
            pr_number, pr_title, pr_body, labels = _load_pr_metadata(args.pr_json)
        else:
            pr_number = int(_require_env("PR_NUMBER"))
            pr_title = _require_env("PR_TITLE")
            pr_body = os.environ.get("PR_BODY", "")
            labels = _labels_from_env_or_payload()
        changelog_changed = append_changelog(
            pr_number, pr_title, pr_body, labels=labels
        )

    if args.command in ("all", "sync-changelog"):
        changelog_ordered = sync_changelog_unreleased()

    if args.command in ("all", "sync-readme"):
        readme_changed = sync_readme_upcoming()

    if not changelog_changed and not changelog_ordered and not readme_changed:
        print("No CHANGELOG or README changes written.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        print(f"Release copy check failed: {error}", file=sys.stderr)
        sys.exit(1)
