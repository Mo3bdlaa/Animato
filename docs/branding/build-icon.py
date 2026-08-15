#!/usr/bin/env python3
"""
Builds the launcher icon's foreground layers from one 512px source.

Run from the repository root:

    python3 docs/branding/build-icon.py light

`light` or `dark` picks which of the two variants in this directory becomes the launcher icon. It
rewrites `animato_icon_foreground.png` at all five densities; it does not touch the monochrome
layer, which is a silhouette and the same either way.

## The geometry

An adaptive icon is a 108dp canvas of which the launcher may show as little as the central 72dp
circle, and it is free to shift the foreground against the background for parallax. So the artwork
is drawn at 72dp, centred: a 72dp square fully contains the 72dp mask circle, which means the whole
visible area is artwork whatever shape the device masks to, and the rounding comes from the launcher
rather than being baked into the PNG.

## The keying

The artwork's own background is removed rather than kept, and the flat `<background>` layer supplies
it instead. That is what makes the parallax work: a foreground carrying its own opaque background
would show its edges the moment the launcher shifted it.

Removal is by distance from the background colour, with a soft band rather than a threshold, so
antialiased edges keep their partial alpha instead of turning into a staircase. The colour is read
from the source itself rather than written down here, so the two variants need no separate
configuration and a re-exported source cannot silently drift away from a constant.

The order matters: **resize first, then key.** Keying first and resizing after washes the artwork
out, because Pillow interpolates RGB and alpha separately — a black frame line one pixel wide gets
averaged with the paper-coloured RGB still sitting under its transparent neighbours, and comes out
grey. Scaling while the source is still opaque has no such neighbours to average with.
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

# Density bucket -> canvas size in pixels. 108dp at 1x, 1.5x, 2x, 3x and 4x.
DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# The artwork occupies the inner 72dp of the 108dp canvas.
ARTWORK_FRACTION = 72 / 108

# Distances from the background colour, summed across RGB, between which alpha ramps from 0 to 1.
# Below the first the pixel is background; above the second it is artwork; between, it is an edge.
KEY_SOFT_START = 24
KEY_SOFT_END = 90

BRANDING = Path(__file__).parent
RES = BRANDING.parent.parent / "animato-app" / "src" / "main" / "res"


def background_colour(image):
    """
    The artwork's own background: the colour most of it is.

    Not a sampled point. Both variants round their corners with transparency and both are edged in
    black, so every obvious place to sample — a corner, the middle of an edge — lands on something
    that is not the background, and reading one silently poisons the distances below: sampling the
    black edge of the light variant made the black panel frame *nearly* background, and it came out
    at two-thirds alpha, grey. The background is the one colour a flat-coloured icon is mostly made
    of, and that is what this measures.
    """
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
    variant = sys.argv[1] if len(sys.argv) > 1 else "light"
    if variant not in ("light", "dark"):
        print(f"Unknown variant {variant!r}; expected 'light' or 'dark'.")
        return 1

    source = Image.open(BRANDING / f"icon-{variant}.png").convert("RGBA")

    for density, canvas_size in DENSITIES.items():
        artwork_size = round(canvas_size * ARTWORK_FRACTION)
        offset = (canvas_size - artwork_size) // 2

        artwork = keyed(source.resize((artwork_size, artwork_size), Image.LANCZOS))

        canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        canvas.paste(artwork, (offset, offset))

        target = RES / f"drawable-{density}" / "animato_icon_foreground.png"
        canvas.save(target, optimize=True)
        print(f"{target}  {canvas_size}px canvas, {artwork_size}px artwork")

    print(f"\nSet <background> in mipmap/ic_launcher.xml to the {variant} variant's colour.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
