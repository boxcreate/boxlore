#!/usr/bin/env python3
"""Extract a normalized SHA-256 certificate digest from tool output."""

from __future__ import annotations

import re
import sys


def extract_sha256_digest(blob: str) -> str:
    flat = " ".join(blob.splitlines())
    patterns = (
        r"SHA-?256 digest:\s*((?:[0-9A-Fa-f]{2}:\s*){31}[0-9A-Fa-f]{2})",
        r"SHA-?256 digest:\s*([0-9A-Fa-f]{64})",
        r"SHA256:\s*((?:[0-9A-Fa-f]{2}:\s*){31}[0-9A-Fa-f]{2})",
        r"SHA256:\s*([0-9A-Fa-f]{64})",
    )
    for pattern in patterns:
        match = re.search(pattern, flat, flags=re.I)
        if match:
            return re.sub(r"[^0-9A-Fa-f]", "", match.group(1)).upper()
    return ""


def main() -> int:
    print(extract_sha256_digest(sys.stdin.read()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
