# Tracked podcast RTDB backups

The weekly **Repair Tracked Podcast Feed URLs** workflow writes one pre-repair snapshot of `tracked_podcasts` per UTC ISO week:

```text
YYYY-Www.json
```

The repair script retains the newest 10 weekly JSON files. Re-running the
workflow in the same week replaces that week’s snapshot instead of consuming
another retention slot.

These public files contain `title`, `imageUrl`, and optional `feedUrl`. Feed URLs
can carry access credentials: notification registration is therefore restricted
to user-confirmed public feeds. RSS device registration rows are grouped into
show-and-URL scopes (`rss:<show-id>~<sha256-of-trimmed-feed-url>`) before writing,
so device IDs are not published. Different accepted URLs under one saved show
ID remain separate; identical scopes collapse to one metadata row.
Grouped RSS backups are not a per-device registration restore; the app must
re-register its own accepted feed after restore. Prefer restoring an
individual affected row or field from a snapshot. Replacing the entire RTDB
node can discard valid rows written by newer app versions after the backup.
