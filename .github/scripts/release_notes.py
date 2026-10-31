#!/usr/bin/env python3
"""The listener notes list (`release-notes/upcoming.md`): parse it, check its rules, and render it.

PRs edit the list in place, so it always says what changed since the last release: the README's
Upcoming region, the app's Coming next, What's new at release and the release announcement are all
built from it. Release preparation archives it as `release-notes/vX.Y.Z.md` and starts it afresh.
"""

from __future__ import annotations

import re
from argparse import Namespace
from dataclasses import dataclass, field
from pathlib import Path

import manual_dispatch

UPCOMING_PATH = Path("release-notes/upcoming.md")
ARCHIVE_DIR = Path("release-notes")
MAX_HIGHLIGHTS = 3
GROUPS = ("New", "Improved", "Fixed")
HEADINGS = ("Highlights", *GROUPS)
TITLE_PREFIX = "Announcement title:"

# A line may end with the PRs it covers: "(#1234)" or "(#1234, #1240)". They become README badges
# and are left out of the app's notes and the announcement.
PR_REFS_RE = re.compile(r"\s*\((#\d+(?:\s*,\s*#\d+)*)\)\s*$")
PLACEHOLDER_RE = re.compile(r"^(?:tbd|todo|\.\.\.|…|-)?$", re.IGNORECASE)
COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)

RULES = """<!--
Listener notes for the next release: what listeners will read in What's new, the app's Coming next
and the release announcement. Every PR that changes something listeners notice edits this list.

- Read the whole list first. If a line already covers the feature, edit that line so it says the net
  change since the last release (black to yellow, not a second "green to yellow" line).
- One line per change, in plain listener words: what they notice, not how it was built. No class
  names, CI or internal terms. The product name is boxlore (lowercase).
- Highlights: at most 3 lines, most important first. They open What's new and form the release
  announcement, which must fit one notification with its title (the PR check measures it). To add
  one when there are 3, move the least important down into its group.
- New, Improved, Fixed: add the line to the group it belongs to, at the end of that group.
- End a line with the PRs it covers, like (#1234) or (#1234, #1240). Optional.
- Announcement title: optional; without it the announcement reads "boxlore vX.Y.Z is here".

Release preparation keeps these notes as release-notes/vX.Y.Z.md and empties this list.
-->"""


@dataclass
class ListenerNotes:
    title: str = ""
    highlights: list[str] = field(default_factory=list)
    groups: dict[str, list[str]] = field(default_factory=lambda: {name: [] for name in GROUPS})

    def is_empty(self) -> bool:
        return not self.highlights and not any(self.groups.values())


def template() -> str:
    """The empty list a release leaves behind."""
    sections = "\n\n".join(f"## {heading}" for heading in HEADINGS)
    return f"{RULES}\n\n{TITLE_PREFIX}\n\n{sections}\n"


def parse(text: str) -> ListenerNotes:
    """The list's title, highlights and groups; raises ValueError on a line outside a known section."""
    notes = ListenerNotes()
    section: str | None = None
    for number, raw in enumerate(COMMENT_RE.sub("", text).splitlines(), start=1):
        line = raw.strip()
        if not line:
            continue
        if line.startswith(TITLE_PREFIX):
            notes.title = line[len(TITLE_PREFIX):].strip()
            continue
        heading = re.match(r"^##\s+(.+?)\s*$", line)
        if heading:
            section = heading.group(1)
            if section not in HEADINGS:
                raise ValueError(f"Line {number}: unknown section '{section}'. Use: {', '.join(HEADINGS)}.")
            continue
        item = re.match(r"^(?:[-*]|\d+\.)\s+(.*)$", line)
        if not item or section is None:
            raise ValueError(f"Line {number}: '{line[:60]}' is not a list line under one of: {', '.join(HEADINGS)}.")
        text_line = item.group(1).strip()
        if section == "Highlights":
            notes.highlights.append(text_line)
        else:
            notes.groups[section].append(text_line)
    return notes


def problems(notes: ListenerNotes) -> list[str]:
    """What breaks the list's rules, in words for the PR check."""
    found: list[str] = []
    if len(notes.highlights) > MAX_HIGHLIGHTS:
        found.append(
            f"Highlights has {len(notes.highlights)} lines; keep at most {MAX_HIGHLIGHTS} "
            "and move the least important down into its group."
        )
    for heading, lines in (("Highlights", notes.highlights), *notes.groups.items()):
        for line in lines:
            if PLACEHOLDER_RE.match(strip_refs(line)):
                found.append(f"{heading} has an empty or TBD line; write the listener wording or remove it.")
    seen: set[str] = set()
    for line in [*notes.highlights, *(item for lines in notes.groups.values() for item in lines)]:
        key = strip_refs(line).casefold()
        if key in seen:
            found.append(f"'{strip_refs(line)[:60]}' appears twice; keep one line per change.")
        seen.add(key)
    size = announcement_bytes(notes)
    if size > manual_dispatch.TOPIC_PAYLOAD_LIMIT:
        found.append(
            f"The release announcement (the title and the highlights) needs {size:,} bytes of the "
            f"{manual_dispatch.TOPIC_PAYLOAD_LIMIT:,} a notification carries; shorten the highlights or the title."
        )
    return found


# The longest tag the announcement will plausibly carry, so a list that passes now still fits at release.
_SIZING_TAG = "v99.99.999"


def _release_announcement_args(title: str, body: str) -> Namespace:
    """The announcement as release publication sends it (the announce-release job's settings)."""
    return Namespace(
        title=title, body=body, type="in-app", target="direct_users", image="",
        route=f"https://github.com/boxcreate/boxlore/releases/latest/download/boxlore-{_SIZING_TAG}.apk",
        release_url=f"https://github.com/boxcreate/boxlore/releases/tag/{_SIZING_TAG}",
        release_alert="true", release_version_code="2147483647", presentation="fullscreen",
        sound="silent", action_label="Download", show_action_in_push="false",
        show_action_in_app="true", category="NEW RELEASE",
    )


def announcement_bytes(notes: ListenerNotes) -> int:
    """The notification data the release announcement needs, in the bytes FCM counts; 0 for an empty list."""
    if notes.is_empty():
        return 0
    title, body = announcement(notes, _SIZING_TAG)
    overhead = manual_dispatch.notification_plan(_release_announcement_args("x", "x"))["payload_bytes"] - 2
    return overhead + len(title.encode("utf-8")) + len(body.encode("utf-8"))


def strip_refs(line: str) -> str:
    """A line without its trailing PR numbers."""
    return PR_REFS_RE.sub("", line).strip()


def pr_numbers(line: str) -> list[int]:
    match = PR_REFS_RE.search(line)
    return [int(ref) for ref in re.findall(r"#(\d+)", match.group(1))] if match else []


def readme_bullet(line: str, repository: str) -> str:
    """A line for the README: its text, then its PRs as Markdown links the README renders as badges."""
    links = " ".join(f"([#{number}](https://github.com/{repository}/pull/{number}))" for number in pr_numbers(line))
    return f"{strip_refs(line)} {links}".strip()


def readme_groups(notes: ListenerNotes, repository: str) -> list[dict[str, list[str]]]:
    """The README Upcoming groups, highlights first."""
    groups = [{"heading": "Highlights", "bullets": [readme_bullet(line, repository) for line in notes.highlights]}]
    groups += [{"heading": name, "bullets": [readme_bullet(line, repository) for line in notes.groups[name]]} for name in GROUPS]
    return [group for group in groups if group["bullets"]]


def announcement(notes: ListenerNotes, tag: str) -> tuple[str, str]:
    """The release announcement: the title (or "boxlore <tag> is here") and the highlights, word for word."""
    lines = notes.highlights or next((lines for lines in notes.groups.values() if lines), [])[:MAX_HIGHLIGHTS]
    title = notes.title or f"boxlore {tag} is here"
    return title, "\n".join(f"- {strip_refs(line)}" for line in lines)


def archive_path(tag: str) -> Path:
    return ARCHIVE_DIR / f"{tag}.md"


def load(path: Path | None = None) -> ListenerNotes:
    """The notes in [path] (the list by default); a missing file is an error, never an empty list."""
    path = path or UPCOMING_PATH
    if not path.is_file():
        raise ValueError(f"{path} is missing; restore it (an empty list keeps its section headings)")
    return parse(path.read_text(encoding="utf-8"))
