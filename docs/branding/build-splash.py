#!/usr/bin/env python3
"""
Builds the splash mark from the unframed lockup.

## Why this exists rather than reusing the launcher icon

The splash used to point at `animato_icon_foreground` — the launcher artwork, which carries the
brand's **panel frame**: a dark rectangle edged in white sketch lines. On a launcher that frame is
the design, sitting on a paper-coloured background layer. On an ink-black splash there is no
background layer, so the frame's dark interior merges with the screen and what is left reads as a
grey slab floating in the void. It looked, in the owner's words, disgusting.

So the splash gets the **lockup**: blue ANIMATO over アニマト, on transparency, no frame, no panel.
Same artwork the design pass used in its splash mock.

## The two variants, and the thing that has already cost time once

Android resolves `@drawable/…` per configuration, and an override replaces **one** configuration at
a time. The dark lockup has black Japanese text, which vanishes on ink black; the light lockup has
white, which vanishes on Paper. So this writes both `drawable-nodpi` and `drawable-night-nodpi`, and
`@color/splash` needs its `night` variant defined for exactly the same reason — see
docs/BRANDING.md §10, which records this failure mode after it happened to the launch colour.

Run from anywhere: `python3 docs/branding/build-splash.py`
"""

from pathlib import Path

import numpy as np
from PIL import Image

# The splash window draws its icon at 288 dp with the inner 192 dp as content, so the artwork is
# built at a size that stays crisp when the system scales it down on any density.
CANVAS = 768
ARTWORK_WIDTH_FRACTION = 0.86

BRANDING = Path(__file__).parent
MOCKUPS = BRANDING / "mockups" / "assets"
RES = BRANDING.parent.parent / "animato-app" / "src" / "main" / "res"


def recoloured_japanese(lockup, to_white):
    """
    The lockup's Japanese line, flipped between black and white.

    The supplied asset is drawn for a light background: blue wordmark, **black** アニマト. On ink
    black that line disappears entirely, which is worse than wrong — it silently drops a third of
    the mark. Only near-black opaque pixels move, so the blue wordmark and its speed lines are
    untouched.
    """
    pixels = np.asarray(lockup, dtype=np.uint8).copy()
    if not to_white:
        return Image.fromarray(pixels, "RGBA")

    rgb = pixels[:, :, :3].astype(int)
    # Only substantially opaque pixels. Recolouring the faint anti-aliased edges too was the first
    # attempt, and it put a grey halo round the whole mark: a black pixel at alpha 30 is invisible
    # on ink black, and the same pixel turned white at alpha 30 is a visible smudge. The edges are
    # left dark and simply fade into the background, which is what they were already doing.
    dark = (rgb.sum(axis=2) < 200) & (pixels[:, :, 3] > 200)
    pixels[dark, 0:3] = 255
    return Image.fromarray(pixels, "RGBA")


def write(variant_dir, to_white):
    lockup = Image.open(MOCKUPS / "logo-lockup-blue.png").convert("RGBA")
    lockup = recoloured_japanese(lockup, to_white)

    width = round(CANVAS * ARTWORK_WIDTH_FRACTION)
    height = round(width * lockup.height / lockup.width)
    lockup = lockup.resize((width, height), Image.LANCZOS)

    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.alpha_composite(lockup, ((CANVAS - width) // 2, (CANVAS - height) // 2))

    target = RES / variant_dir / "animato_splash_mark.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(target, optimize=True)
    print(f"wrote {target.relative_to(RES.parent.parent.parent)}")


def main():
    # Default configuration is the dark screen — that is the app's identity, and the one a launch
    # actually shows most of the time.
    write("drawable-nodpi", to_white=True)
    write("drawable-night-nodpi", to_white=True)
    write("drawable-notnight-nodpi", to_white=False)


if __name__ == "__main__":
    main()
