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

## The one field that is not what it looks like

`version`. It carries the extension API version as well as the release: the app takes everything
before the last dot and requires 14.0 or 16.0. That is derived from the APK here rather than
written by hand, so an extension the app would reject is caught by this script instead of by a
silence on somebody's phone.
"""

import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "repo"
APK_DIR = OUT / "apk"
ICON_DIR = OUT / "icon"

SUPPORTED_LIB_VERSIONS = {"14.0", "16.0"}


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


def lib_version_of(version_name: str) -> str:
    """What Animato's loader will read out of this version name."""
    return version_name.rpartition(".")[0]


def entry_for(apk: Path) -> dict:
    badging = aapt_badging(apk)
    package = field(badging, r"package: name='([^']+)'")
    code = int(field(badging, r"versionCode='(\d+)'", "0"))
    version = field(badging, r"versionName='([^']+)'", "1.0")
    # aapt prints `application-label:` for a label it resolved and `application: label='...'` for
    # what it read; both are checked so a build that changes how the label is declared cannot
    # quietly start publishing the package name as the extension's name.
    name = (
        field(badging, r"application-label:'([^']+)'")
        or field(badging, r"application: label='([^']+)'")
        or package
    )

    # Refused here rather than shipped, because the app says nothing when it rejects one: the
    # extension installs, appears in Android's app list, and never shows up in Animato.
    lib = lib_version_of(version)
    if lib not in SUPPORTED_LIB_VERSIONS:
        sys.exit(
            f"{apk.name}: version name '{version}' reads as extension API '{lib}', which Animato "
            f"does not support ({', '.join(sorted(SUPPORTED_LIB_VERSIONS))}). Fix versionName in "
            f"the module's build.gradle.kts — it must be \"<lib>.<extVersionCode>\"."
        )

    # The metadata the loader reads, read here for the same reason: what shipped is what counts.
    nsfw = int(field(badging, r"tachiyomi\.animeextension\.nsfw'\s+value='(\d+)'", "0"))
    lang = package.split(".")[-2] if package.count(".") >= 2 else "all"

    return {
        "name": name,
        "pkg": package,
        "apk": apk.name,
        "lang": lang,
        "code": code,
        "version": version,
        "nsfw": nsfw,
        "sources": [],
    }


def copy_icon(apk: Path, package: str) -> None:
    """
    Put the source's icon where the store expects it.

    Animato builds the icon address from the index's location and the package name —
    `<store>/icon/<pkg>.png` — so an extension whose module has an icon.png needs it copied under
    that name. Without it the store lists the extension with a broken image, which reads as a
    broken extension.
    """
    module = apk.name.removesuffix("-release.apk")
    icon = REPO / "src" / module / "icon.png"
    if icon.is_file():
        ICON_DIR.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(icon, ICON_DIR / f"{package}.png")


def main() -> None:
    if not APK_DIR.is_dir():
        sys.exit(f"No APKs at {APK_DIR}. Build first.")

    entries = []
    for apk in APK_DIR.glob("*.apk"):
        entry = entry_for(apk)
        copy_icon(apk, entry["pkg"])
        entries.append(entry)

    entries.sort(key=lambda e: e["name"].lower())
    if not entries:
        sys.exit("No APKs found; refusing to write an empty index over a good one.")

    (OUT / "index.min.json").write_text(
        json.dumps(entries, separators=(",", ":"), ensure_ascii=False) + "\n",
        encoding="utf8",
    )
    print(f"index.min.json  {len(entries)} extensions")


if __name__ == "__main__":
    main()
