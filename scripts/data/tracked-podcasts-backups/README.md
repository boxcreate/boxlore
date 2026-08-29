# Tracked podcast RTDB backups

The weekly **Repair Tracked Podcast Feed URLs** workflow writes one complete
pre-repair snapshot of `tracked_podcasts` per UTC ISO week:

```text
YYYY-Www.json
```

The repair script retains the newest 10 weekly JSON files. Re-running the
workflow in the same week replaces that week’s snapshot instead of consuming
another retention slot.

These files contain podcast metadata only (`title`, `imageUrl`, and optional
`feedUrl`), not listener identities or credentials. Prefer restoring an
individual affected row or field from a snapshot. Replacing the entire RTDB
node can discard valid rows written by newer app versions after the backup.
