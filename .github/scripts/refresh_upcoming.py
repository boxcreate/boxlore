"""Refresh preview copy in a verified update manifest; never changes binary metadata."""
import argparse
import json
from pathlib import Path
from generate_update_manifest import encode_manifest, upcoming_notes


def refresh(manifest, readme):
    if manifest.get("schemaVersion") != 1 or manifest.get("packageName") != "cx.aswin.boxlore" or manifest.get("testOnly"):
        raise ValueError("Expected a shipping update manifest")
    result = dict(manifest, upcoming=upcoming_notes(readme))
    if len(encode_manifest(result)) > 64 * 1024:
        raise ValueError("Update feed exceeds 64 KiB; shorten Upcoming Changes")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--readme", type=Path, default=Path("README.md"))
    args = parser.parse_args()
    args.manifest.write_bytes(encode_manifest(refresh(json.loads(args.manifest.read_text()), args.readme.read_text())))
