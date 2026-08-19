#!/usr/bin/env python3
"""
Builds the white wordmark: the brand's own brush letters, cut out and levelled.

## Why cut the artwork instead of setting the name in a typeface

Home's top bar said "Animato" in the app's UI font. That is a label, not a mark — and the actual
logo could not go there, because `animato_logo` is a *picture*: cream ground, black panel, speed
lines. You cannot tint a picture white, and on an ink-black bar its panel reads as a grey slab.

The alternative considered was setting the name in a heavy italic typeface. It was rejected on the
evidence: the brand is recognisable by its **brush strokes**, and no typeface has them. So this
takes the letters that already exist and makes them usable as an interface element — one shape, one
colour, transparent everywhere else, so a `tint` decides what colour it is at the point of use.

## The measurement that made this easy

The word was assumed to be tilted, and it is not. Fitting a line through the feet of NIMATO across
290 columns puts the baseline at **0.46°** — visually level already. What reads as a tilt is the
*panel frame* the launcher icon wraps it in, not the lettering. So the correction here is half a
degree, and the "make the word horizontal" request is answered by dropping the frame rather than by
touching a single letter.

## How the cut works, and why it is not a threshold

The letters are saturated blue; everything discarded — the Japanese line, the frame, the halftone —
is grey or black. Chroma separates them perfectly. But the gate is *soft*: alpha is scaled by how
colourful a pixel is rather than switched on or off, so every anti-aliased edge pixel keeps its own
weight. A hard mask would return a stencil with sawtooth edges, which on brush lettering is the one
thing you would notice.

Run from anywhere: `python3 docs/branding/build-wordmark.py`
"""

import math
from pathlib import Path

import numpy as np
from PIL import Image

BRANDING = Path(__file__).parent
SOURCE = BRANDING / "mockups" / "assets" / "wordmark-blue.png"
RES = BRANDING.parent.parent / "animato-app" / "src" / "main" / "res"

# Measured on the source: the baseline of NIMATO, fitted across every column it spans and clipped of
# the outliers where letter bowls dip below the line.
BASELINE_TILT_DEGREES = 0.458

# Work several times larger than the output, then come back down. Rotating and trimming at final
# size would chew the brush edges; at 4x the resampling has room to be invisible.
SUPERSAMPLE = 4

# The underline stroke lives in its own band under the letters, with clear air above it.
STROKE_BAND_STARTS_AT = 0.74

# One nominal height for the interface mark, in dp. It renders smaller than this in the top bar —
# deliberately, because a downscale stays crisp and an upscale does not.
UI_HEIGHT_DP = 28
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# The splash window draws its icon at 288 dp with the inner 192 dp as content, so the artwork is
# built large enough to stay crisp wherever the system scales it.
SPLASH_CANVAS = 768

# 192/288. The system does not merely centre the icon inside that square — it *masks* it to a circle
# of that diameter, and everything outside is cut away. A wide mark sized by its width therefore
# loses its ends: at 0.86 of the canvas the word's corners sat well outside the circle and the
# splash read "ANIMAT", with the O gone. So the fit is measured on the mark's diagonal, which is the
# only measurement a circle cares about, with a little air left over.
SPLASH_CIRCLE_FRACTION = 192 / 288
SPLASH_FIT = 0.94


def white_alpha(source):
    """
    The letters' coverage, as an alpha channel, with the colourless parts removed.

    Returns a float array the same shape as the image: how much of each pixel is brush.
    """
    rgba = np.array(source.convert("RGBA")).astype(np.float32)
    chroma = np.max(rgba[..., :3], axis=-1) - np.min(rgba[..., :3], axis=-1)
    # 18..60 rather than a single cut: below 18 is grey and gone, above 60 is unambiguously ink,
    # and the ramp between them is where the anti-aliasing lives.
    gate = np.clip((chroma - 18.0) / 42.0, 0.0, 1.0)
    return rgba[..., 3] * gate


def levelled(alpha, keep_stroke):
    """The mark, upright and trimmed to its own ink, as a white RGBA image."""
    coverage = alpha.copy()
    if not keep_stroke:
        coverage[int(coverage.shape[0] * STROKE_BAND_STARTS_AT):, :] = 0.0

    height, width = coverage.shape
    big = Image.fromarray(coverage.astype(np.uint8), "L")
    big = big.resize((width * SUPERSAMPLE, height * SUPERSAMPLE), Image.LANCZOS)
    big = big.rotate(-BASELINE_TILT_DEGREES, resample=Image.BICUBIC, expand=True, fillcolor=0)

    ink = np.array(big)
    rows, cols = np.nonzero(ink > 3)
    trimmed = Image.fromarray(ink[rows.min():rows.max() + 1, cols.min():cols.max() + 1], "L")

    out = Image.new("RGBA", trimmed.size, (255, 255, 255, 0))
    out.putalpha(trimmed)
    return out


def write_ui_mark(mark):
    """The top-bar mark, one file per density, so nothing is ever scaled up on a device."""
    ratio = mark.width / mark.height
    for bucket, scale in DENSITIES.items():
        height = round(UI_HEIGHT_DP * scale)
        resized = mark.resize((round(height * ratio), height), Image.LANCZOS)
        folder = RES / f"drawable-{bucket}"
        folder.mkdir(parents=True, exist_ok=True)
        resized.save(folder / "animato_wordmark.png")
        print(f"  drawable-{bucket}/animato_wordmark.png  {resized.width}x{resized.height}")


def write_splash_mark(mark):
    """
    The splash art: the same white letters, centred on a transparent square and inside its mask.

    One file, not three. The old mark carried the Japanese line, which had to be recoloured between
    the light and dark configurations or it vanished into whichever background it was not drawn for
    — a failure this repo has already paid for once. White brush on `@color/splash`, which is ink
    black in *both* configurations, has nothing to vary, so `drawable-nodpi` answers every case and
    the night and notnight overrides are deleted rather than kept in sync.
    """
    # Fit the mark's *diagonal* into the mask circle, not its width. See SPLASH_CIRCLE_FRACTION.
    diameter = SPLASH_CANVAS * SPLASH_CIRCLE_FRACTION * SPLASH_FIT
    scale = diameter / math.hypot(mark.width, mark.height)
    scaled = mark.resize((round(mark.width * scale), round(mark.height * scale)), Image.LANCZOS)

    canvas = Image.new("RGBA", (SPLASH_CANVAS, SPLASH_CANVAS), (255, 255, 255, 0))
    canvas.paste(scaled, ((SPLASH_CANVAS - scaled.width) // 2, (SPLASH_CANVAS - scaled.height) // 2), scaled)

    target = RES / "drawable-nodpi"
    target.mkdir(parents=True, exist_ok=True)
    canvas.save(target / "animato_splash_mark.png")
    print(f"  drawable-nodpi/animato_splash_mark.png  {SPLASH_CANVAS}x{SPLASH_CANVAS}")

    for stale in ("drawable-night-nodpi", "drawable-notnight-nodpi"):
        old = RES / stale / "animato_splash_mark.png"
        if old.exists():
            old.unlink()
            print(f"  removed {stale}/animato_splash_mark.png (one mark now serves both)")


def main():
    source = Image.open(SOURCE)
    alpha = white_alpha(source)

    print("interface mark — letters only, the stroke is a grey hair at 22dp:")
    write_ui_mark(levelled(alpha, keep_stroke=False))

    print("splash mark — with the stroke, which gives the word a floor at size:")
    write_splash_mark(levelled(alpha, keep_stroke=True))


if __name__ == "__main__":
    main()
