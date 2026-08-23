#!/usr/bin/env python3
"""
Builds index.min.json from the APKs the CI just produced.

## Why this exists rather than being read off the APKs at install time

The index is what Animato fetches before anything is installed — it is how the app knows an
extension exists, what it is called, and whether the installed copy is out of date. So it has to be
derivable from the build output alone, with no device involved.

## The one field that matters more than the rest

`code`. Animato compares the installed version code against this number and offers an update when
this one is higher; the version *name* is never compared. Forgetting to raise it ships a release
nobody is offered.
"""

import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "repo"
APK_DIR = OUT / "apk"


def aapt_badging(apk: Path) -> str:
    """Read the manifest back out of the built APK, rather than trusting the build files."""
    aapt = subprocess.run(
        ["aapt", "dump", "badging", str(apk)],
        capture_output=True,
        text=True,
        check=True,
    )
    return aapt.stdout


def field(badging: str, pattern: str, default: str = "") -> str:
    found = re.search(pattern, badging)
    return found.group(1) if found else default


def entry_for(apk: Path) -> dict:
    badging = aapt_badging(apk)
    package = field(badging, r"package: name='([^']+)'")
    code = int(field(badging, r"versionCode='(\d+)'", "0"))
    name = field(badging, r"application-label:'([^']+)'") or package

    # The metadata the loader reads, read here for the same reason: what shipped is what counts.
    nsfw = int(field(badging, r"tachiyomi\.animeextension\.nsfw'\s+value='(\d+)'", "0"))
    lang = package.split(".")[-2] if package.count(".") >= 2 else "all"

    return {
        "name": name,
        "pkg": package,
        "apk": apk.name,
        "lang": lang,
        "code": code,
        "version": field(badging, r"versionName='([^']+)'", "1.0"),
        "nsfw": nsfw,
        "sources": [],
    }


def main() -> None:
    if not APK_DIR.is_dir():
        sys.exit(f"No APKs at {APK_DIR}. Build first.")

    entries = sorted((entry_for(apk) for apk in APK_DIR.glob("*.apk")), key=lambda e: e["name"].lower())
    if not entries:
        sys.exit("No APKs found; refusing to write an empty index over a good one.")

    (OUT / "index.min.json").write_text(
        json.dumps(entries, separators=(",", ":"), ensure_ascii=False) + "\n",
        encoding="utf8",
    )
    print(f"index.min.json  {len(entries)} extensions")


if __name__ == "__main__":
    main()
