#!/usr/bin/env python3
"""
Builds the Android TV banner from the same artwork the launcher icon uses.

A television launcher does not draw an app icon. It draws a **banner** — a fixed 320×180 landscape
tile, named by `android:banner`, with no adaptive layers, no mask and no monochrome variant. An app
with no banner still installs on a TV and simply cannot be found on the home screen, which is a
worse failure than looking wrong.

## Why this is not build-icon.py with different numbers

The launcher icon is artwork keyed onto transparency, so the launcher can supply the background and
slide the layers for parallax. A banner is the opposite: one flat opaque image, and everything it
needs to say has to be inside it. So this composites rather than keys — the artwork goes onto the
brand's paper colour, at the size the tile wants, centred.

The keying is still borrowed, and for the same reason build-icon.py gives: both source variants are
edged in black and rounded with transparency, so pasting one straight in would carry its own
background as a visible rectangle inside ours.

Run from anywhere: `python3 docs/branding/build-tv-banner.py`
"""

from pathlib import Path

import numpy as np
from PIL import Image

# The banner is a single fixed size. Television densities vary, but the launcher scales one tile
# rather than picking per density, and xhdpi is where Android expects to find it.
BANNER_SIZE = (320, 180)

# How much of the tile's height the mark occupies. Leaving room around it matters more here than on
# a launcher icon: TV home screens draw a focus border tight against the banner, and artwork that
# reaches the edge collides with it.
ARTWORK_HEIGHT_FRACTION = 0.72

# Matches @color/animato_paper, the launcher icon's background layer. Written out rather than parsed
# from the XML so the two can be compared by eye in a review; if the brand colour changes, both move.
PAPER = (0xF2, 0xEE, 0xE5)

KEY_SOFT_START = 24
KEY_SOFT_END = 90

BRANDING = Path(__file__).parent
RES = BRANDING.parent.parent / "animato-app" / "src" / "main" / "res"


def background_colour(image):
    """The artwork's own background: the colour most of it is. See build-icon.py."""
    opaque = image[image[:, :, 3] > 250][:, :3]
    quantised = opaque // 8
    packed = (quantised[:, 0].astype(int) << 12) | (quantised[:, 1].astype(int) << 6) | quantised[:, 2]
    commonest = np.bincount(packed).argmax()
    return opaque[packed == commonest].mean(axis=0)


def keyed(source):
    """The artwork with its background removed and its edges left soft."""
    pixels = np.asarray(source, dtype=float)
    distance = np.abs(pixels[:, :, :3] - background_colour(np.asarray(source))).sum(axis=2)

    ramp = (distance - KEY_SOFT_START) / (KEY_SOFT_END - KEY_SOFT_START)
    alpha = np.clip(ramp, 0.0, 1.0) * pixels[:, :, 3]

    pixels[:, :, 3] = alpha
    return Image.fromarray(pixels.round().astype(np.uint8), "RGBA")


def main():
    source = Image.open(BRANDING / "icon-light.png").convert("RGBA")

    # Resize before keying, for the reason build-icon.py records: keying first and resizing after
    # blends keyed-out pixels back in and washes the artwork out.
    side = round(BANNER_SIZE[1] * ARTWORK_HEIGHT_FRACTION)
    artwork = keyed(source.resize((side, side), Image.LANCZOS))

    banner = Image.new("RGBA", BANNER_SIZE, (*PAPER, 255))
    banner.alpha_composite(
        artwork,
        ((BANNER_SIZE[0] - side) // 2, (BANNER_SIZE[1] - side) // 2),
    )

    target = RES / "drawable-xhdpi" / "animato_tv_banner.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    banner.convert("RGB").save(target)
    print(f"wrote {target.relative_to(RES.parent.parent.parent)} at {BANNER_SIZE[0]}×{BANNER_SIZE[1]}")


if __name__ == "__main__":
    main()
