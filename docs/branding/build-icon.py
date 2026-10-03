#!/usr/bin/env python3
"""
Builds the launcher icon's foreground and monochrome layers.

The foreground comes from docs/branding/logo-on-light.png, the mark drawn for white; the monochrome
layer from docs/branding/logo.png, the mark drawn for black. Run from anywhere:
`python3 docs/branding/build-icon.py`

## The foreground

An adaptive icon is a 108dp canvas of which the launcher may show as little as a 66dp circle in the
middle, and it is free to shift the foreground against the background for parallax. The mark is a
ring, so it is scaled until the ring sits inside that circle — measured on the artwork's own
opaque pixels rather than on the file's edges, which have a margin of their own. Drawn any larger,
the circular mask a good half of launchers apply takes the dragon's head off, and the head is the
part of the mark people recognise it by.

The background layer is @color/animato_icon_background, white, and is not built here. The light
mark comes on an opaque white ground, so the white is taken out of it — colour to alpha: every
pixel becomes the least-transparent colour that, laid over white, gives back exactly the pixel it
was. Over the white background layer that is lossless, edges and highlights included, and it is
what lets the launcher slide the layers for parallax without showing a square.

## The monochrome layer

Android 13's themed icons tint this layer one colour and throw everything else away, so what goes
in it is a *shape*, and the shape has to still be the mark. The source's alpha alone is the wrong
shape: the figure in the middle is opaque, so the ring and the figure fill in to a disc and the
dragon disappears. The mask is therefore the source's brightness as well as its opacity — the
dragon is drawn in light blue and the figure in near-black, so keeping only what is both opaque and
bright keeps the ring and the head and lets the figure go dark, which is the right silhouette.
"""

from pathlib import Path

import numpy as np
from PIL import Image

BRANDING = Path(__file__).parent
SOURCE = BRANDING / "logo-on-light.png"
MONOCHROME_SOURCE = BRANDING / "logo.png"
RES = BRANDING.parent.parent / "animato-app" / "src" / "main" / "res"

# Density bucket -> canvas size in pixels. 108dp at 1x, 1.5x, 2x, 3x and 4x.
DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# How much of the 108dp canvas the mark's opaque pixels span: the 66dp circle every launcher shows.
ARTWORK_FRACTION = 66 / 108

# What counts as part of the silhouette in the monochrome layer. Brightness on 0-255, before alpha.
MONOCHROME_BRIGHTNESS_FLOOR = 70
MONOCHROME_BRIGHTNESS_FULL = 150


def cropped(source):
    """The source cut to its opaque pixels, so scaling measures the mark rather than the file."""
    alpha = np.asarray(source)[:, :, 3]
    rows = np.where((alpha > 20).any(axis=1))[0]
    cols = np.where((alpha > 20).any(axis=0))[0]
    box = (cols.min(), rows.min(), cols.max() + 1, rows.max() + 1)
    return source.crop(box)


def on_canvas(mark, canvas_size, fraction):
    """The mark scaled to `fraction` of a square canvas, keeping its aspect, centred."""
    longest = round(canvas_size * fraction)
    scale = longest / max(mark.size)
    sized = mark.resize((round(mark.width * scale), round(mark.height * scale)), Image.LANCZOS)
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    canvas.alpha_composite(sized, ((canvas_size - sized.width) // 2, (canvas_size - sized.height) // 2))
    return canvas


def white_to_alpha(image):
    """The light mark with its white ground taken out. See the module docstring."""
    rgb = np.asarray(image.convert("RGB")).astype(float)
    alpha = (255.0 - rgb.min(axis=2)) / 255.0
    safe = np.where(alpha > 0, alpha, 1.0)[..., None]
    colour = (rgb - (1.0 - alpha[..., None]) * 255.0) / safe
    out = np.zeros(rgb.shape[:2] + (4,))
    out[..., :3] = np.clip(colour, 0, 255)
    out[..., 3] = alpha * 255.0
    return Image.fromarray(out.round().astype(np.uint8), "RGBA")


def monochrome(mark):
    pixels = np.asarray(mark).astype(float)
    brightness = pixels[:, :, :3].max(axis=2)
    ramp = (brightness - MONOCHROME_BRIGHTNESS_FLOOR) / (MONOCHROME_BRIGHTNESS_FULL - MONOCHROME_BRIGHTNESS_FLOOR)
    alpha = np.clip(ramp, 0.0, 1.0) * pixels[:, :, 3]
    out = np.zeros_like(pixels)
    out[:, :, :3] = 255
    out[:, :, 3] = alpha
    return Image.fromarray(out.round().astype(np.uint8), "RGBA")


def main():
    mark = cropped(white_to_alpha(Image.open(SOURCE)))
    # From the dark mark: the light one's figure is pale, so a brightness mask of it would keep the
    # figure and lose the ring — the opposite of the silhouette wanted.
    silhouette = monochrome(cropped(Image.open(MONOCHROME_SOURCE).convert("RGBA")))
    for density, canvas_size in DENSITIES.items():
        folder = RES / f"drawable-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        on_canvas(mark, canvas_size, ARTWORK_FRACTION).save(folder / "animato_icon_foreground.png", optimize=True)
        on_canvas(silhouette, canvas_size, ARTWORK_FRACTION).save(folder / "animato_icon_monochrome.png", optimize=True)
        print(f"wrote drawable-{density} foreground and monochrome at {canvas_size}px")


if __name__ == "__main__":
    main()
