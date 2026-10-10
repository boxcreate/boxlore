#!/usr/bin/env python3
"""Append merged-PR release notes to CHANGELOG.md and sync README Upcoming Changes.

Author-written copy in the PR body (`<!-- release-copy:changelog -->` and
`<!-- release-copy:readme -->`) is highest priority: it is pasted verbatim,
tagged `<!-- copy:locked -->`, and ordered without rewriting. prepare_release
promotes locked README Upcoming into What's New as-is.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

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
README_AI_NOTICE = (
    '<p align="center">'
    "<sub><sub>"
    "AI-generated summary; may contain mistakes.<br/>"
    'Verify details in the <a href="CHANGELOG.md">changelog</a> '
    "and linked pull requests."
    "</sub></sub>"
    "</p>"
)
CATEGORY_ORDER = ("Added", "Changed", "Fixed", "Deprecated", "Removed", "Security")
README_GROUP_ORDER = (
    "Critical",
    "New features",
    "Improvements",
    "Fixes",
    "Security",
    "Other",
)
README_GROUP_EMOJI = {
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
BACKEND_CHANGE_LABEL = "backend-change"
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
PR_README_COPY_START = "<!-- release-copy:readme:start -->"
PR_README_COPY_END = "<!-- release-copy:readme:end -->"
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
README_HEADING_ALIASES = {
    "critical": "Critical",
    "key updates": "Critical",
    "highlights": "Critical",
    "new features": "New features",
    "added": "New features",
    "improvements": "Improvements",
    "changed": "Improvements",
    "fixes": "Fixes",
    "fixed": "Fixes",
    "high": "Fixes",
    "medium": "Fixes",
    "low": "Fixes",
    "security": "Security",
    "other": "Other",
}
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


def _resolve_pr_tags(
    labels: list[str] | None = None,
    title: str = "",
    body: str = "",
) -> tuple[str | None, bool]:
    """Return (user_impact_label, backend_change)."""
    found_impact: list[str] = []
    backend = False
    for label in labels or []:
        if _is_backend_label(label):
            backend = True
        impact = _normalize_user_impact(label)
        # backend-change is not a user-impact level; backend-fix legacy maps to no-user-impact.
        if impact and impact not in found_impact:
            if _normalize_token(label) == "backend-change":
                continue
            found_impact.append(impact)

    user_impact: str | None = None
    if len(found_impact) == 1:
        user_impact = found_impact[0]
    elif len(found_impact) > 1:
        user_impact = max(found_impact, key=lambda name: USER_IMPACT_SCORE[name])

    if user_impact is None or not backend:
        blob = f"{title}\n{body}"
        if user_impact is None:
            # Prefer explicit level phrases before legacy aliases.
            for alias in (
                "user-impact-critical",
                "user impact critical",
                "user-impact-high",
                "user impact high",
                "user-impact-medium",
                "user impact medium",
                "user-impact-low",
                "user impact low",
                "no-user-impact",
                "no user impact",
            ):
                pattern = rf"(?i)(?:^|[\s\[\(,;]){re.escape(alias)}(?:$|[\s\]\),;:])"
                if re.search(pattern, blob):
                    user_impact = USER_IMPACT_ALIASES.get(alias) or _normalize_user_impact(alias)
                    break
        if not backend:
            for alias in ("backend-change", "backend change"):
                pattern = rf"(?i)(?:^|[\s\[\(,;]){re.escape(alias)}(?:$|[\s\]\),;:])"
                if re.search(pattern, blob):
                    backend = True
                    break

    return user_impact, backend


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


def _attach_copy_locked(text: str) -> str:
    cleaned = _strip_copy_locked(text)
    if not cleaned:
        return cleaned
    return f"{cleaned} {COPY_LOCKED_MARKER}"


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


def _normalize_readme_heading(raw: str, impact: str | None = None) -> str:
    cleaned = re.sub(r"^[^\w]+", "", (raw or "").strip()).strip()
    if not cleaned:
        return "Critical" if impact == "user-impact-critical" else "Improvements"
    return README_HEADING_ALIASES.get(cleaned.lower(), cleaned)


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


def parse_pr_readme_copy(
    body: str,
    impact: str | None = None,
) -> list[dict[str, list[str]]]:
    """Listener README bullets from the PR body's verbatim readme region."""
    inner = (_extract_marked_region(body or "", PR_README_COPY_START, PR_README_COPY_END) or "").strip()
    if not inner:
        return []
    return _parse_readme_copy_inner(inner, impact=impact)


def _parse_readme_copy_inner(
    inner: str,
    impact: str | None = None,
) -> list[dict[str, list[str]]]:
    grouped: dict[str, list[str]] = defaultdict(list)
    current_heading: str | None = None
    found_bullet = False
    leftover: list[str] = []
    for line in inner.splitlines():
        stripped = line.strip()
        header = re.match(r"^###\s+(.+)$", stripped)
        if header:
            current_heading = _normalize_readme_heading(header.group(1), impact)
            continue
        match = re.match(r"^[-*]\s+(.+)$", stripped)
        if match:
            bullet = match.group(1).strip()
            if not bullet or _is_placeholder_bullet(bullet):
                continue
            found_bullet = True
            heading = current_heading or _normalize_readme_heading("", impact)
            grouped[heading].append(bullet)
            continue
        if stripped and not stripped.startswith("<!--"):
            leftover.append(stripped)

    if not found_bullet:
        paragraph = " ".join(leftover).strip()
        if paragraph and not _is_placeholder_bullet(paragraph):
            heading = current_heading or _normalize_readme_heading("", impact)
            grouped[heading].append(paragraph)

    return _sort_readme_groups(
        [{"heading": heading, "bullets": bullets} for heading, bullets in grouped.items() if bullets]
    )


def _require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        print(f"Missing required environment variable: {name}", file=sys.stderr)
        sys.exit(1)
    return value


def _clean_pr_body(body: str) -> str:
    """Strip template noise, test plans, checkboxes, and agent prompts from PR description."""
    if not body:
        return ""
    # Strip <details> blocks (e.g. AI agent prompts from reviews)
    cleaned = re.sub(r"<details>.*?</details>", "", body, flags=re.DOTALL | re.IGNORECASE)
    # Strip HTML comments
    cleaned = re.sub(r"<!--.*?-->", "", cleaned, flags=re.DOTALL)
    # Strip Test plan section onwards
    cleaned = re.split(r"(?i)##\s*Test\s+plan", cleaned)[0]
    # Strip checkbox lines like - [x] or - [ ]
    cleaned = re.sub(r"^\s*-\s*\[[ xX]\].*$", "", cleaned, flags=re.MULTILINE)
    # Strip excessive blank lines
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()


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


@dataclass
class ChangelogCluster:
    pr_number: int | None
    items: list[tuple[str, str]] = field(default_factory=list)
    importance: int = 50
    theme: str = "general"
    impact: str | None = None
    backend_change: bool = False
    copy_locked: bool = False

    def text_blob(self) -> str:
        return " ".join(text for _, text in self.items).lower()


def _cluster_theme(text: str) -> str:
    if any(k in text for k in ("queue", "refill", "auto-fill", "auto‑fill", "reorder", "skip memory", "lore queue")):
        return "queue & playback"
    if any(k in text for k in ("nps", "survey", "play review", "play store", "engagement", "prompt", "posthog")):
        return "feedback & surveys"
    if any(k in text for k in ("home tab", "scroll", "lag", "shimmer", "staggered", "recomposition")):
        return "home performance"
    if any(k in text for k in ("gitignore", "pycache", "__pycache__", "bytecode")):
        return "developer tooling"
    if any(k in text for k in ("analytics", "telemetry", "event")):
        return "analytics"
    return "general"


def _cluster_importance(
    theme: str,
    text: str,
    categories: set[str],
    impact: str | None = None,
) -> int:
    if impact in USER_IMPACT_SCORE:
        return USER_IMPACT_SCORE[impact]
    if theme == "developer tooling" or theme == "analytics":
        return 10
    if theme == "queue & playback":
        return 95
    if theme == "home performance":
        return 80
    if theme == "feedback & surveys":
        return 42
    if "fixed" in {c.lower() for c in categories} and theme == "general":
        return 55
    return 50


def _cluster_sections_by_pr(sections: dict[str, list[str]]) -> list[ChangelogCluster]:
    grouped: dict[int | None, ChangelogCluster] = {}

    for category, bullets in sections.items():
        for bullet in bullets:
            if not bullet.strip():
                continue
            pr_number = _extract_pr_number(bullet)
            impact, backend = _extract_impact_tags(bullet)
            locked = _is_copy_locked(bullet)
            cleaned = _strip_changelog_markers(_strip_pr_links(bullet))
            cluster = grouped.setdefault(pr_number, ChangelogCluster(pr_number=pr_number))
            cluster.items.append((category, cleaned))
            if backend:
                cluster.backend_change = True
            if locked:
                cluster.copy_locked = True
            if impact:
                if cluster.impact is None or USER_IMPACT_SCORE[impact] > USER_IMPACT_SCORE.get(
                    cluster.impact, 0
                ):
                    cluster.impact = impact

    clusters: list[ChangelogCluster] = []
    for cluster in grouped.values():
        categories = {cat for cat, _ in cluster.items}
        blob = cluster.text_blob()
        cluster.theme = _cluster_theme(blob)
        cluster.importance = _cluster_importance(
            cluster.theme, blob, categories, cluster.impact
        )
        clusters.append(cluster)

    clusters.sort(key=lambda c: (-c.importance, c.pr_number or 0))
    return clusters


def _strip_pr_links(text: str) -> str:
    return re.sub(r"\s*\(\[#\d+\]\([^)]+\)\)\s*$", "", text).strip()


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


def _render_readme_copy_block(pr_number: int, groups: list[dict[str, list[str]]]) -> str:
    lines = [f"<!-- readme-copy:start pr={pr_number}"]
    for group in groups:
        heading = str(group.get("heading") or "").strip()
        bullets = [str(item).strip() for item in group.get("bullets") or [] if str(item).strip()]
        if not bullets:
            continue
        if heading:
            lines.append(f"### {heading}")
        for bullet in bullets:
            lines.append(f"- {bullet}")
    lines.append(f"readme-copy:end pr={pr_number} -->")
    return "\n".join(lines)


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


def _upsert_readme_copy_block(
    content: str,
    pr_number: int,
    groups: list[dict[str, list[str]]],
) -> str:
    bounds = _unreleased_bounds(content)
    if bounds is None:
        raise ValueError("Could not find '## [Unreleased]' header in CHANGELOG.md")
    start, end = bounds
    block = content[start:end]
    copy_blocks = _collect_readme_copy_blocks(block)
    copy_blocks[pr_number] = _render_readme_copy_block(pr_number, groups)
    sections = _parse_unreleased_sections(block)
    return _write_unreleased_region(content, sections, copy_blocks)


def _partition_locked_sections(
    sections: dict[str, list[str]],
) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    locked: dict[str, list[str]] = {}
    unlocked: dict[str, list[str]] = {}
    for category, bullets in sections.items():
        for bullet in bullets:
            dest = locked if _is_copy_locked(bullet) else unlocked
            dest.setdefault(category, []).append(bullet)
    return locked, unlocked


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


def _locked_readme_groups_from_changelog(
    content: str,
) -> tuple[list[dict[str, list[str]]], set[int]]:
    impact_scores: dict[int, int] = {}
    for bullets in _parse_unreleased_sections(_unreleased_raw(content)).values():
        for bullet in bullets:
            pr_number = _extract_pr_number(bullet)
            impact, _ = _extract_impact_tags(bullet)
            if pr_number is not None:
                impact_scores[pr_number] = max(
                    impact_scores.get(pr_number, 0),
                    USER_IMPACT_SCORE.get(impact or "", 0),
                )

    grouped: dict[str, list[tuple[int, int, str]]] = defaultdict(list)
    locked_prs: set[int] = set()
    for match in README_COPY_BLOCK_RE.finditer(_unreleased_raw(content)):
        pr_number = int(match.group(1))
        if impact_scores.get(pr_number) == USER_IMPACT_SCORE["no-user-impact"]:
            continue
        parsed_groups = _parse_readme_copy_inner(match.group(2))
        if not parsed_groups:
            continue
        locked_prs.add(pr_number)
        for group in parsed_groups:
            heading = group["heading"]
            for bullet in group["bullets"]:
                grouped[heading].append(
                    (
                        impact_scores.get(pr_number, 0),
                        pr_number,
                        _format_readme_bullet(bullet, pr_number, preserve_copy=True),
                    )
                )
    groups = _sort_readme_groups(
        [
            {
                "heading": heading,
                "bullets": [
                    bullet
                    for _, _, bullet in sorted(
                        bullets,
                        key=lambda item: (-item[0], -item[1]),
                    )
                ],
            }
            for heading, bullets in grouped.items()
            if bullets
        ]
    )
    return groups, locked_prs


def _format_readme_bullet(text: str, pr_number: int | None, *, preserve_copy: bool = False) -> str:
    cleaned = text.strip()
    if not cleaned:
        return cleaned
    if not preserve_copy:
        cleaned = re.sub(
            r"^(?:\[(?:Fixed|Added|Changed|Improved|Fix|Add|Change|Security)\]|(?:Fixed|Added|Changed|Improved|Fix|Add|Change|Security):)\s*",
            "",
            cleaned,
            flags=re.IGNORECASE,
        ).strip()
    if pr_number is not None and not _pr_already_present(cleaned, pr_number):
        cleaned = f"{cleaned} {_pr_suffix(pr_number)}"
    return cleaned


def _readme_eligible(cluster: ChangelogCluster) -> bool:
    """Whether a cluster should appear in README Upcoming / release highlights."""
    if not cluster.items:
        return False
    if cluster.impact == "no-user-impact":
        return False
    if cluster.impact == "user-impact-critical":
        return True
    if cluster.impact == "user-impact-high":
        return True
    if cluster.impact == "user-impact-medium":
        return True
    if cluster.impact == "user-impact-low":
        # Include low user impact unless it's also backend-only noise; still allow.
        return True
    # Unlabeled: keep legacy importance threshold.
    return cluster.importance >= 40


def _sort_readme_groups(groups: list[dict[str, list[str]]]) -> list[dict[str, list[str]]]:
    order = {name: index for index, name in enumerate(README_GROUP_ORDER)}

    def rank(group: dict[str, list[str]]) -> tuple[int, str]:
        heading = group["heading"]
        return (order.get(heading, len(README_GROUP_ORDER)), heading.lower())

    return sorted(groups, key=rank)


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


def _bullet_to_html_list_item(bullet: str) -> str:
    cleaned = _strip_changelog_markers(bullet.strip())
    match = re.search(r"^(.*?)\s*\(\[#(\d+)\]\(([^)]+)\)\)\s*$", cleaned)
    if match:
        text, pr_number, url = match.groups()
        return (
            f'<li>{text.strip()} '
            f'<a href="{url}"><img src="https://img.shields.io/badge/PR-{pr_number}-6750A4?style=flat-square" '
            f'alt="PR #{pr_number}" height="18"/></a></li>'
        )
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


def _render_whats_new_inner(
    version_tag: str,
    release_date: str,
    body_html: str,
    include_ai_notice: bool = False,
) -> str:
    body = body_html.strip()
    if include_ai_notice:
        return (
            f"<!-- release-meta: version={version_tag} date={release_date} -->\n"
            f"{body}\n"
            f"{README_AI_NOTICE}"
        )
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


def _format_upcoming_inner(body: str, include_ai_notice: bool = False) -> str:
    stripped = body.strip()
    if not stripped:
        stripped = EMPTY_UPCOMING_TEXT
    if not include_ai_notice:
        return stripped
    if stripped == EMPTY_UPCOMING_TEXT or (
        not stripped.startswith("<ul") and not stripped.startswith("<b>")
    ):
        return f"{stripped}\n{README_AI_NOTICE}"
    return f"{stripped}\n{README_AI_NOTICE}"


def _render_release_notes_shell(
    *,
    upcoming_inner: str,
    whats_new_inner: str | None,
    include_ai_notice: bool = False,
) -> str:
    upcoming = _format_upcoming_inner(upcoming_inner, include_ai_notice=include_ai_notice)
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
    include_ai_notice: bool = False,
) -> str:
    """Rewrite the Upcoming body; preserve the latest What's New region if present."""
    body = _render_readme_upcoming_body(groups=groups, bullets=bullets)
    existing_whats_new = _extract_marked_region(
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
        include_ai_notice=include_ai_notice,
    )


def _update_readme(
    content: str,
    groups: list[dict[str, list[str]]] | None = None,
    bullets: list[str] | None = None,
    include_ai_notice: bool = False,
) -> str:
    block = _render_readme_upcoming_block(
        content,
        groups=groups,
        bullets=bullets,
        include_ai_notice=include_ai_notice,
    )
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


def _ensure_readme_ai_notice(block: str) -> str:
    if "AI-generated summary; may contain mistakes." in block:
        return block
    return f"{block.rstrip()}\n{README_AI_NOTICE}"


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


def validate_release_copy(
    pr_title: str,
    pr_body: str,
    labels: list[str] | None,
) -> tuple[str, bool, dict[str, list[str]], list[dict[str, list[str]]]]:
    """Validate author copy before writing files; never synthesize missing wording."""
    impacts = {
        impact
        for label in labels or []
        if _normalize_token(label) != "backend-change"
        and (impact := _normalize_user_impact(label))
    }
    if len(impacts) != 1:
        raise ValueError(
            "Add exactly one PR impact label: " + ", ".join(USER_IMPACT_LABELS) +
            ". Then rerun release-copy validation."
        )
    impact = next(iter(impacts))
    backend = any(_is_backend_label(label) for label in labels or [])
    for name, start, end in (
        ("changelog", PR_CHANGELOG_COPY_START, PR_CHANGELOG_COPY_END),
        ("readme", PR_README_COPY_START, PR_README_COPY_END),
    ):
        if name == "readme" and impact == "no-user-impact":
            continue
        inner = _extract_marked_region(pr_body, start, end)
        if pr_body.count(start) != 1 or pr_body.count(end) != 1 or inner is None:
            raise ValueError(
                f"Keep exactly one complete release-copy:{name} region in the PR body. "
                "Use the PR template markers, then rerun validation."
            )
        for line in inner.splitlines():
            bullet = re.match(r"^[-*]\s*(.*)$", line.strip())
            if bullet and _is_placeholder_bullet(bullet.group(1)):
                raise ValueError(
                    f"Replace or remove the empty/TBD bullet in release-copy:{name}. "
                    "Every release note must have authored wording."
                )
    entries = parse_pr_changelog_copy(pr_body, pr_title, impact)
    if not entries:
        raise ValueError(
            "Fill the PR's release-copy:changelog region with categorized developer bullets. "
            "Empty bullets and TBD are placeholders. Save the PR body, then rerun the failed workflow."
        )
    groups = parse_pr_readme_copy(pr_body, impact)
    if impact != "no-user-impact" and not groups:
        raise ValueError(
            "Fill the PR's release-copy:readme region with listener-facing changes. "
            "Save the PR body, then rerun the failed workflow. No automatic copy is generated."
        )
    if impact == "no-user-impact":
        groups = []
    return impact, backend, entries, groups


def append_changelog(
    pr_number: int,
    pr_title: str,
    pr_body: str,
    labels: list[str] | None = None,
) -> bool:
    original = CHANGELOG_PATH.read_text(encoding="utf-8")
    # Historical entries without authored copy remain readable and idempotent.
    if _pr_already_present(original, pr_number):
        if not _pr_already_present(_unreleased_raw(original), pr_number):
            return False
        groups = parse_pr_readme_copy(pr_body)
        impact, _ = _resolve_pr_tags(labels)
        if groups and impact != "no-user-impact" and pr_number not in _locked_readme_groups_from_changelog(original)[1]:
            updated = _upsert_readme_copy_block(original, pr_number, groups)
            if updated != original:
                CHANGELOG_PATH.write_text(updated, encoding="utf-8")
                return True
        print(f"CHANGELOG already contains PR #{pr_number}; no entry rewritten.")
        return False
    impact, backend, entries, groups = validate_release_copy(pr_title, pr_body, labels)
    updated, changed = _update_changelog(
        original, entries, pr_number,
        impact=impact, backend_change=backend, copy_locked=True,
    )
    if groups:
        updated = _upsert_readme_copy_block(updated, pr_number, groups)
    if changed:
        CHANGELOG_PATH.write_text(updated, encoding="utf-8")
        print(f"Stored authored release copy for PR #{pr_number}.")
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


def reviewed_readme_groups(content: str) -> list[dict[str, list[str]]]:
    groups, copied_prs = _locked_readme_groups_from_changelog(content)
    clusters = _cluster_sections_by_pr(_extract_unreleased_sections(content))
    missing = [c.pr_number for c in clusters if _readme_eligible(c) and c.pr_number not in copied_prs]
    if missing:
        names = ", ".join(f"#{number}" if number is not None else "an unlinked entry" for number in missing)
        raise ValueError(
            "Missing listener release copy for " + names + ". Fill each PR's "
            "release-copy:readme region, run backfill-changelog for that PR, then retry. "
            "No README entries were rewritten or omitted."
        )
    return groups


def sync_readme_upcoming() -> bool:
    original = README_PATH.read_text(encoding="utf-8")
    groups = reviewed_readme_groups(CHANGELOG_PATH.read_text(encoding="utf-8"))
    updated = _update_readme(original, groups=groups, include_ai_notice=False)
    if updated != original:
        README_PATH.write_text(updated, encoding="utf-8")
        print("Synced authored README copy; category order and impact determine placement.")
        return True
    return False


def _load_pr_metadata(path: str) -> tuple[int, str, str, list[str]]:
    metadata_path = Path(path)
    if not metadata_path.is_file():
        raise ValueError(f"PR metadata file does not exist: {metadata_path}")

    payload = json.loads(metadata_path.read_text(encoding="utf-8"))
    if payload.get("mergedAt") is None:
        raise ValueError("Changelog backfill requires a merged pull request")
    if payload.get("baseRefName") != "master":
        raise ValueError("Changelog backfill only accepts pull requests merged into master")

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
        help="Path to `gh pr view --json number,title,body,mergedAt,baseRefName,labels` output for append/backfill",
    )
    args = parser.parse_args()

    if args.command == "validate-copy":
        event = json.loads(Path(_require_env("GITHUB_EVENT_PATH")).read_text(encoding="utf-8"))
        pull = event["pull_request"]
        title = str(pull.get("title") or "")
        if "[skip changelog]" in title or str(pull.get("head", {}).get("ref", "")).startswith("release/v"):
            print("Release/tooling PR opts out of changelog copy.")
            return
        validate_release_copy(title, str(pull.get("body") or ""), _labels_from_env_or_payload(pull))
        print("Release copy is ready: authored wording will be preserved.")
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
