# 📊 Turso Database Cost Report (Last 30 Days)

This report tracks the Turso SQLite read and write metrics for the background synchronization pipeline, bifurcated by country and step.

## 📅 Date: 2026-07-04

| Country | Sync Step | DB Reads | DB Writes |
| :--- | :--- | :---: | :---: |
| **`IN`** | `import-pi-data` | 10,929 | 0 |
|  | `populate-charts` | 4,020 | 0 |
|  | `sync-episodes` | 15,176 | 29 |
|  | `vectorize` | 12,274 | 137 |
|  | `vectorize-podcasts` | 12,108 | 97 |
| | *Subtotal (IN)* | *54,507* | *263* |
| **`US`** | `import-pi-data` | 14,948 | 0 |
|  | `populate-charts` | 4,019 | 0 |
|  | `sync-episodes` | 15,342 | 0 |
|  | `vectorize` | 12,049 | 27 |
|  | `vectorize-podcasts` | 11,988 | 11 |
| | *Subtotal (US)* | *58,346* | *38* |

---

