#!/usr/bin/env python3
"""Append merged-PR release notes to CHANGELOG.md and sync README Upcoming Changes.

Author-written copy in the PR body (`<!-- release-copy:changelog -->` and
`<!-- release-copy:readme -->`) is highest priority: it is pasted verbatim,
tagged `<!-- copy:locked -->`, and never sent to Groq for rewrite. prepare_release
promotes locked README Upcoming into What's New as-is.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
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
GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
GROQ_MODEL = "openai/gpt-oss-120b"
GROQ_USER_AGENT = "boxlore-changelog/1.4"
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
    r"<!--\s*readme-copy:start\s+pr=(\d+)\s*-->\s*(.*?)\s*"
    r"<!--\s*readme-copy:end\s+pr=\1\s*-->",
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
    "new features": "New features",
    "added": "New features",
    "improvements": "Improvements",
    "changed": "Improvements",
    "fixes": "Fixes",
    "fixed": "Fixes",
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


def _groq_entries(api_key: str, pr_number: int, pr_title: str, pr_body: str) -> dict[str, list[str]]:
    system_prompt = """You write Keep a Changelog entries for boxlore (Android/Kotlin podcast app).
Return ONLY valid JSON with optional keys: Added, Changed, Fixed, Deprecated, Removed, Security.
Each value is an array of bullet strings WITHOUT leading dashes.

Audience: developers and technical contributors reading CHANGELOG.md (NOT end-user README copy).

Rules:
- Use precise technical wording: class/module names, tiers, repositories, and behavior when relevant.
- Merge related changes into one bullet per feature area (e.g. all SmartQueueEngine work → one Added bullet).
- For CI-only, dependency-bump, or docs-only PRs, still return exactly one short Changed (or docs) bullet — never an empty object.
- Prefer summarizing analytics as "PostHog announcement viewed/dismissed/action events" — do not list every event property.
- When the PR body includes "What changes in the user's life" / Listener impact, still write technical CHANGELOG bullets, but do not invent UI toolkit branding (e.g. "Material 3") as the main story.
- Use "Fixed ..." for bugs, "Added ..." for features, "Changed ..." for behavior/refactors, "Removed ..." for deletions.
- Do not invent changes unsupported by the PR title/body.
- Omit empty categories.
- No version headers, dates, PR numbers, or markdown in bullets.
- Aim for 1–6 bullets total per PR (at least one)."""

    user_prompt = f"""PR #{pr_number}
Title: {pr_title}

Description:
{pr_body or "(no description)"}

Generate changelog bullets for the [Unreleased] section."""

    parsed = _groq_chat_json(
        api_key,
        system_prompt,
        user_prompt,
        "Groq API error",
    )
    if not parsed:
        return {}

    normalized: dict[str, list[str]] = {}
    for category in CATEGORY_ORDER:
        raw = parsed.get(category, [])
        if not isinstance(raw, list):
            continue
        bullets = [str(item).strip() for item in raw if str(item).strip()]
        if bullets:
            normalized[category] = bullets
    return normalized


def _fallback_entries_from_title(pr_title: str) -> dict[str, list[str]]:
    """Deterministic changelog bullets when Groq returns nothing (e.g. CI/deps PRs)."""
    title = (pr_title or "").strip() or "Maintenance update"
    conventional = re.match(
        r"^(?P<type>feat|fix|chore|ci|docs|refactor|test|perf|build|style)"
        r"(?:\([^)]*\))?!?:\s*(?P<summary>.+)$",
        title,
        flags=re.IGNORECASE,
    )
    if conventional:
        kind = conventional.group("type").lower()
        summary = conventional.group("summary").strip()
    else:
        kind = "chore"
        summary = title

    if kind == "feat":
        category = "Added"
    elif kind == "fix":
        category = "Fixed"
    elif kind == "docs":
        category = "Changed"
        if not summary.lower().startswith("docs"):
            summary = f"Documentation: {summary}"
    else:
        category = "Changed"
    return {category: [summary]}


def _extract_pr_number(bullet: str) -> int | None:
    match = re.search(r"\(#(\d+)\)", bullet)
    if match:
        return int(match.group(1))
    match = re.search(r"\[#(\d+)\]\(https://github\.com/[^/]+/[^/]+/pull/\1\)", bullet)
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


def _render_clustered_changelog(clusters: list[ChangelogCluster]) -> str:
    lines: list[str] = []
    for cluster in clusters:
        label = f"PR #{cluster.pr_number}" if cluster.pr_number is not None else "Unlinked"
        impact = cluster.impact or "unlabeled"
        backend = " backend-change" if cluster.backend_change else ""
        lines.append(
            f"## {label} | impact {impact}{backend} | importance {cluster.importance} | "
            f"theme: {cluster.theme}{' | copy-locked' if cluster.copy_locked else ''}"
        )
        by_category: dict[str, list[str]] = defaultdict(list)
        for category, text in cluster.items:
            by_category[category].append(text)
        for category in CATEGORY_ORDER:
            for text in by_category.get(category, []):
                lines.append(f"- [{category}] {text}")
        lines.append("")
    return "\n".join(lines).strip()


def _strip_pr_links(text: str) -> str:
    return re.sub(r"\s*\(\[#\d+\]\([^)]+\)\)\s*$", "", text).strip()


def _groq_retry_wait_seconds(detail: str, attempt: int) -> float:
    match = re.search(r"try again in ([0-9]+(?:\.[0-9]+)?)s", detail, flags=re.I)
    if match:
        return max(1.0, float(match.group(1)) + 0.5)
    return min(60.0, float(2 ** attempt))


def _groq_chat_json(api_key: str, system_prompt: str, user_prompt: str, error_label: str) -> dict:
    payload = {
        "model": GROQ_MODEL,
        "temperature": 0.2,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }

    max_attempts = 8
    last_detail = ""
    for attempt in range(1, max_attempts + 1):
        request = urllib.request.Request(
            GROQ_API_URL,
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
                "User-Agent": GROQ_USER_AGENT,
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                body = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            last_detail = exc.read().decode("utf-8", errors="replace")
            if exc.code == 429 and attempt < max_attempts:
                wait = _groq_retry_wait_seconds(last_detail, attempt)
                print(
                    f"{error_label}: rate limited (attempt {attempt}/{max_attempts}); "
                    f"retrying in {wait:.1f}s"
                )
                time.sleep(wait)
                continue
            print(f"{error_label} ({exc.code}): {last_detail}", file=sys.stderr)
            return {}
        except urllib.error.URLError as exc:
            last_detail = str(exc)
            if attempt < max_attempts:
                wait = min(30.0, float(2 ** attempt))
                print(
                    f"{error_label}: network error (attempt {attempt}/{max_attempts}); "
                    f"retrying in {wait:.1f}s ({last_detail})"
                )
                time.sleep(wait)
                continue
            print(f"{error_label}: {last_detail}", file=sys.stderr)
            return {}

        content = body["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        if not isinstance(parsed, dict):
            print(f"{error_label}: Groq response was not a JSON object", file=sys.stderr)
            return {}
        return parsed

    print(f"{error_label}: giving up after retries. {last_detail}", file=sys.stderr)
    return {}


def _format_changelog_bullet(
    text: str,
    pr_number: int | None,
    impact: str | None = None,
    backend_change: bool = False,
    copy_locked: bool = False,
) -> str:
    return _attach_impact(
        _format_readme_bullet(text, pr_number),
        impact,
        backend_change=backend_change,
        copy_locked=copy_locked,
    )


def _parse_changelog_bullet(entry: object) -> tuple[str, int | None]:
    return _parse_readme_bullet(entry)


def _sections_from_cluster_bullets(
    curated: dict[str, list[tuple[str, int | None]]],
    clusters: list[ChangelogCluster],
) -> dict[str, list[str]]:
    """Sort bullets within each Keep a Changelog category by cluster importance."""
    pr_importance = {c.pr_number: c.importance for c in clusters if c.pr_number is not None}
    pr_impact = {c.pr_number: c.impact for c in clusters if c.pr_number is not None}
    pr_backend = {
        c.pr_number: c.backend_change for c in clusters if c.pr_number is not None
    }
    pr_locked = {c.pr_number: c.copy_locked for c in clusters if c.pr_number is not None}

    def sort_key(item: tuple[str, int | None]) -> tuple[int, str]:
        _, pr_number = item
        return (-pr_importance.get(pr_number, 0), str(pr_number or ""))

    sections: dict[str, list[str]] = {}
    for category in CATEGORY_ORDER:
        items = curated.get(category, [])
        if not items:
            continue
        sorted_items = sorted(items, key=sort_key)
        bullets = [
            _format_changelog_bullet(
                text,
                pr,
                pr_impact.get(pr) if pr is not None else None,
                pr_backend.get(pr, False) if pr is not None else False,
                copy_locked=pr_locked.get(pr, False) if pr is not None else False,
            )
            for text, pr in sorted_items
            if text.strip()
        ]
        if bullets:
            sections[category] = bullets
    return sections


def _fallback_changelog_from_clusters(clusters: list[ChangelogCluster]) -> dict[str, list[str]]:
    curated: dict[str, list[tuple[str, int | None]]] = defaultdict(list)
    for cluster in clusters:
        if not cluster.items:
            continue
        if cluster.copy_locked:
            for category, text in cluster.items:
                curated[category].append((text, cluster.pr_number))
            continue
        by_category: dict[str, list[str]] = defaultdict(list)
        for category, text in cluster.items:
            by_category[category].append(text)
        for category in CATEGORY_ORDER:
            texts = by_category.get(category, [])
            if not texts:
                continue
            merged = texts[0] if len(texts) == 1 else f"{texts[0]} (+ {len(texts) - 1} related changes)"
            curated[category].append((merged, cluster.pr_number))
    return _sections_from_cluster_bullets(curated, clusters)


def _groq_curate_changelog_unreleased(
    api_key: str, sections: dict[str, list[str]]
) -> dict[str, list[str]]:
    """Re-group and prioritize the full [Unreleased] section for CHANGELOG.md."""
    if not any(sections.values()):
        return {}

    clusters = _cluster_sections_by_pr(sections)
    clustered_input = _render_clustered_changelog(clusters)
    if not clustered_input:
        return {}

    system_prompt = """You curate clustered changelog drafts for boxlore into a clean Keep a Changelog [Unreleased] section.

Audience: developers — use technical, precise language (class names, tiers, modules, repositories, guards).
This is NOT the user-facing README; do not simplify away useful implementation detail.

Input is grouped by PR/theme with impact tags and importance scores. Each ## block is one merged feature area.
Impact levels: user-impact-critical (~100), user-impact-high (~95), user-impact-medium (~70), user-impact-low (~45), no-user-impact (~12).
Optional flag backend-change may appear with any impact level (pairable).
Blocks marked copy-locked are author-written and must be preserved verbatim — do not rewrite, merge, or drop them.

Return ONLY valid JSON:
{"Added": [{"text": "...", "pr": 853}, ...], "Changed": [...], "Fixed": [...], "Removed": [...], ...}

Rules:
1. ONE bullet object per ## block per category. Merge all [Added] lines in a block into one Added bullet; same for Changed/Fixed/Removed. Exception: copy-locked blocks keep every bullet unchanged.
2. Include "pr" from the ## PR #NNN header on every bullet.
3. Preserve every ## block so release reconciliation can prove each merged PR is represented. Put no-user-impact / tooling last and collapse it to one concise Changed bullet.
4. Within each category array, order bullets by importance (user-impact-critical first, then high, no-user-impact last). backend-change does not by itself lower priority when paired with a user-impact level.
5. Keep a Changelog category names exactly: Added, Changed, Fixed, Deprecated, Removed, Security.
6. No PR numbers inside "text" (appended separately). No markdown headers in output.
7. Prefer 2–4 bullets per PR cluster total across categories, not one line per commit.

Tone examples (CHANGELOG — technical):
- "Smart Queue v2: tiered SmartQueueEngine (T0–T4, T3.5) with skip memory, region-aware refill, and unified BoxLorePlaybackService path"
- "EngagementPromptCoordinator: native PostHog NPS surveys, unified NPS/Play review modal, 14-day promoter cooldown"
- "Fixed Tier 0 newest-sort adding archive episodes on latest-item play; discovery landing guard skips deep continuation"

Do NOT use README listener tone like "shows why each item is there" — use provenance labels, AUTO_FILL, contextSourceId, etc."""

    user_prompt = f"""Curate these PR/theme clusters into grouped Keep a Changelog bullets:

{clustered_input}"""

    parsed = _groq_chat_json(
        api_key,
        system_prompt,
        user_prompt,
        "Groq CHANGELOG curation error",
    )

    curated: dict[str, list[tuple[str, int | None]]] = defaultdict(list)
    for category in CATEGORY_ORDER:
        raw = parsed.get(category, [])
        if not isinstance(raw, list):
            continue
        for entry in raw:
            text, pr_number = _parse_changelog_bullet(entry)
            if text:
                curated[category].append((text, pr_number))

    if curated:
        curated_sections = _sections_from_cluster_bullets(curated, clusters)
        expected_prs = {
            cluster.pr_number
            for cluster in clusters
            if cluster.pr_number is not None
        }
        returned_prs = {
            pr_number
            for bullets in curated_sections.values()
            for bullet in bullets
            if (pr_number := _extract_pr_number(bullet)) is not None
        }
        if returned_prs == expected_prs:
            return curated_sections
        print(
            "Groq curation omitted or added PR clusters; "
            "using deterministic fallback."
        )
    return _fallback_changelog_from_clusters(clusters)


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
    lines = [f"<!-- readme-copy:start pr={pr_number} -->"]
    for group in groups:
        heading = str(group.get("heading") or "").strip()
        bullets = [str(item).strip() for item in group.get("bullets") or [] if str(item).strip()]
        if not bullets:
            continue
        if heading:
            lines.append(f"### {heading}")
        for bullet in bullets:
            lines.append(f"- {bullet}")
    lines.append(f"<!-- readme-copy:end pr={pr_number} -->")
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


def _merge_locked_and_curated(
    locked: dict[str, list[str]],
    curated: dict[str, list[str]],
) -> dict[str, list[str]]:
    locked_prs = {
        _extract_pr_number(bullet)
        for bullets in locked.values()
        for bullet in bullets
        if _extract_pr_number(bullet) is not None
    }
    merged: dict[str, list[str]] = {}
    for category in CATEGORY_ORDER:
        locked_bullets = sorted(locked.get(category, []), key=_bullet_sort_key)
        curated_bullets = [
            bullet
            for bullet in curated.get(category, [])
            if _extract_pr_number(bullet) not in locked_prs
        ]
        combined = locked_bullets + curated_bullets
        if combined:
            merged[category] = combined
    return merged


def _sections_excluding_prs(
    sections: dict[str, list[str]],
    pr_numbers: set[int],
) -> dict[str, list[str]]:
    filtered: dict[str, list[str]] = {}
    for category, bullets in sections.items():
        kept = [
            bullet
            for bullet in bullets
            if (pr := _extract_pr_number(bullet)) is None or pr not in pr_numbers
        ]
        if kept:
            filtered[category] = kept
    return filtered


def _locked_readme_groups_from_changelog(
    content: str,
) -> tuple[list[dict[str, list[str]]], set[int]]:
    grouped: dict[str, list[str]] = defaultdict(list)
    locked_prs: set[int] = set()
    for match in README_COPY_BLOCK_RE.finditer(_unreleased_raw(content)):
        pr_number = int(match.group(1))
        locked_prs.add(pr_number)
        for group in _parse_readme_copy_inner(match.group(2)):
            heading = group["heading"]
            for bullet in group["bullets"]:
                grouped[heading].append(_format_readme_bullet(bullet, pr_number))
    groups = _sort_readme_groups(
        [{"heading": heading, "bullets": bullets} for heading, bullets in grouped.items() if bullets]
    )
    return groups, locked_prs


def _drop_prs_from_readme_groups(
    groups: list[dict[str, list[str]]],
    pr_numbers: set[int],
) -> list[dict[str, list[str]]]:
    cleaned: list[dict[str, list[str]]] = []
    for group in groups:
        bullets = [
            bullet
            for bullet in group.get("bullets") or []
            if (pr := _extract_pr_number(bullet)) is None or pr not in pr_numbers
        ]
        if bullets:
            cleaned.append({"heading": group["heading"], "bullets": bullets})
    return cleaned


def _merge_readme_groups(
    locked: list[dict[str, list[str]]],
    ai_groups: list[dict[str, list[str]]],
) -> list[dict[str, list[str]]]:
    by_heading: dict[str, list[str]] = defaultdict(list)
    seen: dict[str, set[str]] = defaultdict(set)
    for source in (locked, ai_groups):
        for group in source:
            heading = group["heading"]
            for bullet in group.get("bullets") or []:
                if bullet in seen[heading]:
                    continue
                seen[heading].add(bullet)
                by_heading[heading].append(bullet)
    return _sort_readme_groups(
        [{"heading": heading, "bullets": bullets} for heading, bullets in by_heading.items() if bullets]
    )


def _format_readme_bullet(text: str, pr_number: int | None) -> str:
    cleaned = text.strip()
    if not cleaned:
        return cleaned
    if pr_number is not None and not _pr_already_present(cleaned, pr_number):
        cleaned = f"{cleaned} {_pr_suffix(pr_number)}"
    return cleaned


def _parse_readme_bullet(entry: object) -> tuple[str, int | None]:
    if isinstance(entry, dict):
        text = str(entry.get("text", "")).strip()
        pr_raw = entry.get("pr")
        pr_number = int(pr_raw) if pr_raw is not None else None
        return text, pr_number
    return str(entry).strip(), None


def _dominant_readme_heading(cluster: ChangelogCluster) -> str:
    if cluster.impact == "user-impact-critical":
        return "Critical"
    categories = {cat for cat, _ in cluster.items}
    if "Added" in categories:
        return "New features"
    if "Changed" in categories:
        return "Improvements"
    if "Fixed" in categories:
        return "Fixes"
    if "Security" in categories:
        return "Security"
    return "Other"


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


def _fallback_readme_from_clusters(clusters: list[ChangelogCluster]) -> list[dict[str, list[str]]]:
    """Deterministic grouped README when Groq grouping fails."""
    grouped: dict[str, list[str]] = defaultdict(list)
    for cluster in clusters:
        if not _readme_eligible(cluster):
            continue
        heading = _dominant_readme_heading(cluster)
        preview = cluster.items[0][1]
        if len(preview) > 117:
            preview = preview[:114].rstrip() + "..."
        grouped[heading].append(_format_readme_bullet(preview, cluster.pr_number))
    return _sort_readme_groups(
        [{"heading": heading, "bullets": bullets} for heading, bullets in grouped.items() if bullets]
    )


def _groq_curate_readme_upcoming(
    api_key: str, sections: dict[str, list[str]]
) -> list[dict[str, list[str]]]:
    """Curate all [Unreleased] entries into grouped, importance-sorted README bullets."""
    if not any(sections.values()):
        return []

    clusters = _cluster_sections_by_pr(sections)
    clustered_input = _render_clustered_changelog(clusters)
    if not clustered_input:
        return []

    system_prompt = """You curate clustered changelog entries into a README "Upcoming Changes" section for boxlore listeners.

Audience: podcast listeners using the app — plain English only.
This section must answer: what is different in the listener's day-to-day use of boxlore?
Do NOT use CHANGELOG-style technical terms (SmartQueueEngine, Tier 0, PlaybackRepository, AUTO_FILL, contextSourceId, composable/class names, FCM payload keys, PackageManager, DataStore).
Never mention: Material 3 / Material You, PostHog, analytics/telemetry/event names, CI, Groq, CodeRabbit, Sonar, refactoring, modules, workflows, or "logs views/actions".
The paired CHANGELOG entry for the same PR uses developer language; this README section must feel different.

If the PR/cluster text includes a "Listener impact" or "What changes in the user's life" idea, prioritize that outcome over implementation details.

Input is pre-grouped by PR/theme with an importance score (higher = more user-visible). Each ## block is ONE feature area — merge its bullets into as few README lines as possible.

Return ONLY valid JSON:
{"groups": [{"heading": "...", "bullets": [{"text": "...", "pr": 853}, ...]}, ...]}

Each bullet MUST include "pr" copied from the matching ## PR #NNN block in the input. One bullet object per ## block you keep.

Allowed headings (exactly one per group, omit empty):
- "Critical" — correctness fixes listeners already feel (missing episodes, late alerts). Lead the list.
- "New features" — new capabilities listeners can use (from Added clusters that are truly new surfaces)
- "Improvements" — clearer UI, polish, safer dismissal, better update notes (prefer this for announcement/dialog polish)
- "Fixes" — bugs resolved (from Fixed clusters)
- "Security" — privacy/security only

MANDATORY clustering rules:
1. ONE README bullet per input ## block in most cases. Never split a single PR/theme across multiple bullets.
2. For importance 90+ themes (queue, playback, discovery): allow at most TWO bullets if they describe clearly distinct user wins; prefer ONE strong sentence.
3. For importance 40–55 themes (NPS, surveys, review prompts): exactly ONE bullet, never lead the list.
4. Drop clusters with importance below 40 / no-user-impact entirely from README. Include user-impact-critical, user-impact-high and user-impact-medium always; user-impact-low when space allows. backend-change may be paired with any user-impact level and does not exclude README by itself.
5. Merge all bullets inside a ## block before writing — e.g. four queue bullets → one: "Smart queue auto-refills, shows why items appear, supports drag reorder, and undo remove." Never rewrite copy-locked blocks.
6. Sort bullets within each group by the cluster importance score (highest first). user-impact-critical MUST appear before high/medium/low.
7. Cap at 8 bullets total across all groups. Copy-locked bullets are exempt from the cap and must be kept.
8. Prefer concrete listener outcomes (easier to read update notes, harder to dismiss by accident, Play installs skip GitHub download prompts) over UI toolkit or logging details.

Importance guidance (respect the scores in input):
- user-impact-critical / 100: lead README / What's New; author copy if present
- user-impact-high / 90–99: headline features
- user-impact-medium / 70–89: solid README entries
- user-impact-low / 40–55: optional shorter lines
- no-user-impact / below 40: omit from README (CHANGELOG only)

Rewrite in plain English; no PR numbers inside "text", no Compose/modules jargon. Under 140 chars in "text" (PR link is appended separately)."""

    user_prompt = f"""Curate these PR/theme clusters into grouped README bullets.
Lead with what listeners notice. Ignore analytics and design-system branding.

{clustered_input}"""

    parsed = _groq_chat_json(
        api_key,
        system_prompt,
        user_prompt,
        "Groq README curation error",
    )
    raw_groups = parsed.get("groups", [])
    if not isinstance(raw_groups, list):
        return _fallback_readme_from_clusters(clusters)

    groups: list[dict[str, list[str]]] = []
    for item in raw_groups:
        if not isinstance(item, dict):
            continue
        heading = str(item.get("heading", "")).strip()
        bullets_raw = item.get("bullets", [])
        if not heading or not isinstance(bullets_raw, list):
            continue
        bullets: list[str] = []
        for entry in bullets_raw:
            text, pr_number = _parse_readme_bullet(entry)
            if text:
                bullets.append(_format_readme_bullet(text, pr_number))
        if bullets:
            groups.append({"heading": heading, "bullets": bullets})

    if groups:
        return _sort_readme_groups(groups)
    return _fallback_readme_from_clusters(clusters)


def _sort_readme_groups(groups: list[dict[str, list[str]]]) -> list[dict[str, list[str]]]:
    order = {name: index for index, name in enumerate(README_GROUP_ORDER)}

    def rank(group: dict[str, list[str]]) -> tuple[int, str]:
        heading = group["heading"]
        return (order.get(heading, len(README_GROUP_ORDER)), heading.lower())

    return sorted(groups, key=rank)


def _groq_readme_summary(api_key: str, sections: dict[str, list[str]]) -> list[str]:
    """Flat bullet fallback when grouped curation returns nothing."""
    if not any(sections.values()):
        return []

    changelog_text = _render_unreleased(sections)
    system_prompt = """You rewrite changelog entries as short README bullets for end users of boxlore, a podcast Android app.
Return ONLY valid JSON: {"bullets": ["...", ...]}.

Rules:
- Write for listeners using the app, not developers. Answer what changed in their day-to-day use.
- One bullet per user-visible change; merge related technical items into one line.
- Never mention: Compose, recomposition, lazy grid, PerfLog, CI, Groq, CodeRabbit, Sonar, refactoring, parameters, modules, workflows, Material 3, PostHog, analytics event names, or internal code names.
- Prefer outcomes like clearer update notes, accidental dismissals avoided, Play installs skipping GitHub download prompts.
- Keep each bullet under 140 characters.
- No leading dashes, PR numbers, or markdown links."""

    user_prompt = f"""Convert these [Unreleased] changelog entries into user-facing README bullets.
Ignore implementation detail; keep listener-facing outcomes only.

{changelog_text}"""

    parsed = _groq_chat_json(
        api_key,
        system_prompt,
        user_prompt,
        "Groq README summary error",
    )
    raw = parsed.get("bullets", [])
    if not isinstance(raw, list):
        return []
    return [str(item).strip() for item in raw if str(item).strip()]


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
    include_ai_notice: bool = True,
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


def _format_upcoming_inner(body: str, include_ai_notice: bool = True) -> str:
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
    include_ai_notice: bool = True,
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
    include_ai_notice: bool = True,
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
    include_ai_notice: bool = True,
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
        updated = pattern.sub(block, content, count=1)
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


def append_changelog(
    api_key: str,
    pr_number: int,
    pr_title: str,
    pr_body: str,
    labels: list[str] | None = None,
) -> bool:
    if not CHANGELOG_PATH.exists():
        print("CHANGELOG.md not found", file=sys.stderr)
        sys.exit(1)

    impact, backend_change = _resolve_pr_tags(
        labels=labels, title=pr_title, body=pr_body
    )
    if impact:
        backend_note = " + backend-change" if backend_change else ""
        print(f"PR #{pr_number} tags: {impact}{backend_note}")
    else:
        print(
            f"::warning::PR #{pr_number} is missing a required user-impact label "
            f"({', '.join(USER_IMPACT_LABELS)}). "
            f"Optional pairable label: {BACKEND_CHANGE_LABEL}. "
            "Prioritization will use theme heuristics only."
        )
        if backend_change:
            print(f"PR #{pr_number} also has backend-change")

    changelog_original = CHANGELOG_PATH.read_text(encoding="utf-8")
    locked_entries = parse_pr_changelog_copy(pr_body, pr_title, impact)
    copy_locked = bool(locked_entries)
    if copy_locked:
        print(f"PR #{pr_number}: using verbatim CHANGELOG copy from the PR body (not Groq).")
        entries = locked_entries
    else:
        if impact == "user-impact-critical":
            print(
                f"::warning::PR #{pr_number} is user-impact-critical but "
                "release-copy:changelog is empty; falling back to Groq."
            )
        entries = _groq_entries(api_key, pr_number, pr_title, pr_body)
        if not entries:
            print(
                f"Groq returned no changelog entries for PR #{pr_number}; "
                "synthesizing a Changed/Added/Fixed bullet from the PR title."
            )
            entries = _fallback_entries_from_title(pr_title)
            if not entries:
                print("Could not synthesize a changelog entry from the PR title.")
                return False

    changelog_updated, changelog_changed = _update_changelog(
        changelog_original,
        entries,
        pr_number,
        impact=impact,
        backend_change=backend_change,
        copy_locked=copy_locked,
    )
    if changelog_changed:
        changelog_original = changelog_updated

    readme_groups = parse_pr_readme_copy(pr_body, impact)
    if readme_groups:
        print(f"PR #{pr_number}: storing verbatim README copy for Upcoming / What's New.")
        with_copy = _upsert_readme_copy_block(changelog_original, pr_number, readme_groups)
        if with_copy != changelog_original:
            changelog_original = with_copy
            changelog_changed = True
    elif impact == "user-impact-critical":
        print(
            f"::warning::PR #{pr_number} is user-impact-critical but "
            "release-copy:readme is empty; README will be Groq-curated."
        )

    if changelog_changed:
        CHANGELOG_PATH.write_text(changelog_original, encoding="utf-8")
        print(f"Updated CHANGELOG.md for PR #{pr_number}.")
    else:
        print(f"No CHANGELOG changes for PR #{pr_number}.")
    return changelog_changed


def sync_changelog_unreleased(api_key: str) -> bool:
    if not CHANGELOG_PATH.exists():
        print("CHANGELOG.md not found", file=sys.stderr)
        sys.exit(1)

    original = CHANGELOG_PATH.read_text(encoding="utf-8")
    unreleased = _extract_unreleased_sections(original)
    copy_blocks = _collect_readme_copy_blocks(_unreleased_raw(original))
    if not unreleased and not copy_blocks:
        print("No [Unreleased] entries to curate.")
        return False

    locked, unlocked = _partition_locked_sections(unreleased)
    if not any(unlocked.values()):
        print("All [Unreleased] bullets are copy-locked; skipping Groq CHANGELOG curation.")
        curated = _sort_section_bullets(locked)
    else:
        print("Curating unlocked CHANGELOG bullets with Groq; preserving copy-locked bullets.")
        groq_curated = _groq_curate_changelog_unreleased(api_key, unlocked)
        if not groq_curated and not any(locked.values()):
            print("CHANGELOG curation produced no entries.")
            return False
        curated = _merge_locked_and_curated(locked, groq_curated or {})

    updated = _replace_unreleased_sections(original, curated, copy_blocks=copy_blocks)
    if updated != original:
        CHANGELOG_PATH.write_text(updated, encoding="utf-8")
        print("Re-grouped CHANGELOG.md [Unreleased] section.")
        return True

    print("No CHANGELOG changes written.")
    return False


def sync_readme_upcoming(api_key: str) -> bool:
    if not CHANGELOG_PATH.exists():
        print("CHANGELOG.md not found", file=sys.stderr)
        sys.exit(1)
    if not README_PATH.exists():
        print("README.md not found", file=sys.stderr)
        sys.exit(1)

    readme_original = README_PATH.read_text(encoding="utf-8")
    changelog_content = CHANGELOG_PATH.read_text(encoding="utf-8")
    unreleased = _extract_unreleased_sections(changelog_content)

    if not unreleased:
        print("No [Unreleased] entries; clearing README upcoming section.")
        readme_updated = _update_readme(readme_original)
    else:
        locked_groups, locked_prs = _locked_readme_groups_from_changelog(changelog_content)
        unlocked = _sections_excluding_prs(unreleased, locked_prs)
        ai_groups: list[dict[str, list[str]]] = []
        if any(unlocked.values()):
            print("Curating README from unlocked [Unreleased] changelog entries...")
            ai_groups = _groq_curate_readme_upcoming(api_key, unlocked)
            if not ai_groups:
                print("Grouped curation empty; falling back to cluster bullets.")
                clusters = _cluster_sections_by_pr(unlocked)
                ai_groups = _fallback_readme_from_clusters(clusters)
            ai_groups = _drop_prs_from_readme_groups(ai_groups, locked_prs)
        elif locked_groups:
            print("Using verbatim README copy; skipping Groq README curation.")
        else:
            print("Curating README from all [Unreleased] changelog entries...")
            ai_groups = _groq_curate_readme_upcoming(api_key, unreleased)
            if not ai_groups:
                print("Grouped curation empty; falling back to cluster bullets.")
                clusters = _cluster_sections_by_pr(unreleased)
                ai_groups = _fallback_readme_from_clusters(clusters)

        groups = _merge_readme_groups(locked_groups, ai_groups)
        include_ai_notice = bool(ai_groups)
        if groups:
            readme_updated = _update_readme(
                readme_original,
                groups=groups,
                include_ai_notice=include_ai_notice,
            )
        else:
            readme_updated = _update_readme(readme_original)

    if readme_updated != readme_original:
        README_PATH.write_text(readme_updated, encoding="utf-8")
        print("Synced README.md Upcoming Changes section.")
        return True

    print("No README changes written.")
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
        choices=("all", "append", "sync-changelog", "sync-readme"),
        help="append: write PR to CHANGELOG; sync-changelog: re-group [Unreleased]; sync-readme: curate README; all: append + sync-changelog + sync-readme",
    )
    parser.add_argument(
        "--pr-json",
        help="Path to `gh pr view --json number,title,body,mergedAt,baseRefName,labels` output for append/backfill",
    )
    args = parser.parse_args()

    api_key = _require_env("GROQ_API_KEY")
    changelog_changed = False
    changelog_curated = False
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
            api_key, pr_number, pr_title, pr_body, labels=labels
        )

    if args.command in ("all", "sync-changelog"):
        changelog_curated = sync_changelog_unreleased(api_key)

    if args.command in ("all", "sync-readme"):
        readme_changed = sync_readme_upcoming(api_key)

    if not changelog_changed and not changelog_curated and not readme_changed:
        print("No CHANGELOG or README changes written.")


if __name__ == "__main__":
    main()
