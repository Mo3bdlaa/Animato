#!/usr/bin/env python3
"""
Builds the header wordmark from docs/branding/wordmark-on-light.png and wordmark-on-dark.png.

The sources are the name set in the brand's geometric capitals — ANIMATO, with the M in the
accent blue — on transparency, one with ink letters for light surfaces and one with white letters
for dark ones. Run from anywhere: `python3 docs/branding/build-wordmark.py`

## Why two files, and not one tinted

The previous wordmark was a single colour, so it was shipped white and tinted to the top bar's
content colour at the point of use. The new one is two colours: tinting it would paint the blue M
the same as the rest and lose the one thing that makes it the mark. So both versions ship, and the
composable picks by the surface it is drawn on.

## What this does to them

Trims each to its own ink, so the height the app asks for is the height of the letters rather than
of the margin around them, and writes one file per density so a device never scales up. The splash
is not built here any more — build-splash.py draws it from the dragon.
"""

from pathlib import Path

import numpy as np
from PIL import Image

BRANDING = Path(__file__).parent
RES = BRANDING.parent.parent / "animato-app" / "src" / "main" / "res"

SOURCES = {
    "animato_wordmark_on_light": BRANDING / "wordmark-on-light.png",
    "animato_wordmark_on_dark": BRANDING / "wordmark-on-dark.png",
}

# One nominal height for the interface mark, in dp. It renders a little smaller in the top bar —
# deliberately, because a downscale stays crisp and an upscale does not.
UI_HEIGHT_DP = 24
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def trimmed(path):
    image = Image.open(path).convert("RGBA")
    alpha = np.asarray(image)[:, :, 3]
    rows = np.where((alpha > 8).any(axis=1))[0]
    cols = np.where((alpha > 8).any(axis=0))[0]
    return image.crop((cols.min(), rows.min(), cols.max() + 1, rows.max() + 1))


def main():
    for name, source in SOURCES.items():
        mark = trimmed(source)
        ratio = mark.width / mark.height
        for bucket, scale in DENSITIES.items():
            height = round(UI_HEIGHT_DP * scale)
            resized = mark.resize((round(height * ratio), height), Image.LANCZOS)
            folder = RES / f"drawable-{bucket}"
            folder.mkdir(parents=True, exist_ok=True)
            resized.save(folder / f"{name}.png", optimize=True)
            print(f"  drawable-{bucket}/{name}.png  {resized.width}x{resized.height}")

    # The single-colour mark these replace.
    for bucket in DENSITIES:
        stale = RES / f"drawable-{bucket}" / "animato_wordmark.png"
        if stale.exists():
            stale.unlink()


if __name__ == "__main__":
    main()
