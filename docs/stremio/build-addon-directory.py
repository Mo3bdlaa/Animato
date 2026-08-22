#!/usr/bin/env python3
"""
Builds the bundled Stremio addon directory from stremio-addons.net.

## Why a snapshot and not a fetch

Stremio's own collection — `api.strem.io/addonscollection.json`, which the app also reads at
runtime — is about ninety addons. stremio-addons.net is where the community actually publishes,
and it holds roughly five hundred. It has no API: the list is rendered into its pages by Next.js
and nothing serves it as data.

Parsing somebody else's HTML in the request path of a screen would break on their next deploy,
silently, on everybody's phone at once. So it is parsed here instead, once, and the result ships
as an asset. The app merges the two lists and prefers the fetched entry wherever an addon is in
both, so the popular half stays current and the long tail is a snapshot.

## How the extraction works

Each listing page carries links to `/addons/{slug}`; each addon page carries a `manifestUrl` and
the addon's manifest, both inside the React flight payload as escaped JSON. Paging stops after two
consecutive pages that add nothing new, because the site keeps answering past the last real page.

Run from anywhere: `python3 docs/stremio/build-addon-directory.py`
"""

import gzip
import json
import re
import time
import urllib.request
from pathlib import Path

SITE = "https://stremio-addons.net"
ASSET = Path(__file__).parent.parent.parent / "anime/services/src/main/assets/stremio-addons.json"

# Everything the app can do something with. An addon serving none of these is dropped here rather
# than on the device, so the asset stays as small as it can be.
USEFUL = {"catalog", "meta", "stream", "subtitles"}

# Marked, not dropped. This app already hides NSFW sources by default and shows them when somebody
# turns the setting on; a directory that quietly removed them would be making that decision twice,
# and in the place where it cannot be undone. Matched against the addon's own words, which is all
# there is — the manifest format has no flag for this.
ADULT_MARKERS = (
    "adult", "porn", "xxx", "nsfw", "hentai", "18+", "erotic", "javdb", " jav ", "sukebei",
    "onlyfans",
)

# The site answers past its own last page, repeating what it already returned. Two barren pages in
# a row is the end; one is not, because a page can legitimately be all duplicates.
BARREN_PAGES_MEANING_END = 2
POLITE_DELAY_SECONDS = 0.12

# What counts as a run worth keeping — see the check at the end of main().
#
# Two numbers rather than one: the absolute floor catches a first run or a catastrophically broken
# one, and the proportion catches the subtler case of a site change that halves the yield. Addons
# do get delisted, so some shrinkage is real; losing a third of them in a month is not.
MINIMUM_PLAUSIBLE = 100
ACCEPTABLE_SHRINK = 0.66


def get(url):
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Mozilla/5.0", "Accept-Encoding": "gzip"},
    )
    response = urllib.request.urlopen(request, timeout=45)
    body = response.read()
    if response.headers.get("Content-Encoding") == "gzip":
        body = gzip.decompress(body)
    return body.decode("utf8", "replace")


def slugs():
    """Every addon's slug, walked page by page."""
    found, barren, page = [], 0, 1
    seen = set()
    while barren < BARREN_PAGES_MEANING_END:
        html = get(f"{SITE}/addons?sort=popular&page={page}")
        fresh = [s for s in sorted(set(re.findall(r'/addons/([a-z0-9][a-z0-9._~-]*)\\"', html)))
                 if s not in seen]
        if fresh:
            barren = 0
            seen.update(fresh)
            found += fresh
        else:
            barren += 1
        print(f"  page {page}: {len(fresh)} new ({len(found)} total)")
        page += 1
        time.sleep(POLITE_DELAY_SECONDS)
    return found


def brace_match(text, start):
    """The JSON object beginning at `start`, respecting strings and escapes."""
    depth, in_string, escaped = 0, False, False
    for i in range(start, len(text)):
        char = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start:i + 1]
    return None


def addon(slug):
    """One addon, or None if its page does not carry what we need."""
    page = get(f"{SITE}/addons/{slug}").replace('\\"', '"')
    url = re.search(r'"manifestUrl":"(https?://[^"]+)"', page)
    if not url:
        return None

    manifest = None
    for match in re.finditer(r'"manifest":\{', page):
        blob = brace_match(page, match.end() - 1)
        if not blob:
            continue
        try:
            candidate = json.loads(blob)
        except ValueError:
            continue
        if isinstance(candidate, dict) and candidate.get("name") and "resources" in candidate:
            manifest = candidate
            break
    if manifest is None:
        return None

    # A resource is either a bare name or an object carrying its own type and id constraints; only
    # the name matters here, since the app re-reads the real manifest before it installs anything.
    #
    # Kept as a set until after the intersection below. It used to be sorted into a list on this
    # line, which made `resources & USEFUL` a TypeError — caught by the deliberately broad handler
    # in main(), once per addon, so the script did not fail, it simply skipped every single one and
    # wrote an empty file. See the floor in main() for why that can no longer pass silently.
    resources = {
        (r if isinstance(r, str) else r.get("name", "")).lower()
        for r in manifest.get("resources", [])
    } - {""}
    if not (resources & USEFUL):
        return None

    name = manifest.get("name") or slug
    description = (manifest.get("description") or "").strip()
    entry = {
        "name": name,
        "description": description,
        "url": url.group(1),
        "resources": sorted(resources),
        "types": [str(t) for t in manifest.get("types", [])],
    }
    if any(marker in f"{name} {description}".lower() for marker in ADULT_MARKERS):
        entry["adult"] = True
    return entry


def main():
    print("collecting slugs")
    every = slugs()
    print(f"{len(every)} addons listed\n\nreading each addon's page")

    out, skipped = [], 0
    for index, slug in enumerate(every, 1):
        try:
            entry = addon(slug)
        except Exception as error:  # noqa: BLE001 - one bad page must not end the run
            print(f"  {slug}: {error}")
            entry = None
        if entry:
            out.append(entry)
        else:
            skipped += 1
        if index % 50 == 0:
            print(f"  {index}/{len(every)} — {len(out)} kept, {skipped} skipped")
        time.sleep(POLITE_DELAY_SECONDS)

    # Same address twice is the same addon, and the site does carry a few.
    unique = {}
    for entry in out:
        unique.setdefault(entry["url"].rstrip("/").lower(), entry)
    final = sorted(unique.values(), key=lambda e: e["name"].lower())

    # Nothing is written unless the run produced a plausible list.
    #
    # The per-addon handler above is deliberately broad, so that one bad page cannot end a run of
    # five hundred. The cost of that is the failure mode this script actually had: a bug in the
    # shared path raised on every addon, was swallowed every time, and the script reported success
    # while writing an empty file over a good one. A run that is not obviously a directory is a
    # broken run, and a broken run must leave the last good snapshot exactly where it is.
    existing = json.loads(ASSET.read_text(encoding="utf8")) if ASSET.exists() else []
    floor = max(MINIMUM_PLAUSIBLE, int(len(existing) * ACCEPTABLE_SHRINK))
    if len(final) < floor:
        raise SystemExit(
            f"Refusing to write {len(final)} addons: expected at least {floor}. "
            f"The previous snapshot ({len(existing)}) is untouched.",
        )

    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_text(json.dumps(final, indent=1, ensure_ascii=False) + "\n", encoding="utf8")
    print(f"\n{ASSET.name}  {len(final)} addons")


if __name__ == "__main__":
    main()
