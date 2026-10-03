#!/usr/bin/env python3
"""
Builds the Android TV banner from docs/branding/tv-banner-light.png, the banner on white.

The dark banner, docs/branding/tv-banner.png, is kept for promotion; the light one is the default,
to match the launcher icon.

A television launcher does not draw an app icon. It draws a **banner** — a fixed 320×180
landscape tile, named by `android:banner`, with no adaptive layers, no mask and no monochrome
variant. An app with no banner still installs on a TV and simply cannot be found on the home
screen, which is a worse failure than looking wrong.

## Why the source is its own artwork now

This used to composite the square launcher artwork onto a cream tile. However large it was drawn,
a square mark in a 16:9 tile left cream down both sides, and next to other apps' banners — every
one of which fills its tile — it read as a stamp. The banner is drawn for the shape now, at full
resolution, and this only scales it down.

The source has to be 16:9 and opaque. It is checked rather than cropped: a source in the wrong
shape is a mistake to fix in the artwork, not something to quietly cut a strip off.

Run from anywhere: `python3 docs/branding/build-tv-banner.py`
"""

from pathlib import Path

from PIL import Image

# The banner is a single fixed size. Television densities vary, but the launcher scales one tile
# rather than picking per density, and xhdpi is where Android expects to find it.
BANNER_SIZE = (320, 180)

BRANDING = Path(__file__).parent
SOURCE = BRANDING / "tv-banner-light.png"
RES = BRANDING.parent.parent / "animato-app" / "src" / "main" / "res"


def main():
    source = Image.open(SOURCE)
    width, height = source.size
    wanted = BANNER_SIZE[0] / BANNER_SIZE[1]
    if abs(width / height - wanted) > 0.01:
        raise SystemExit(f"{SOURCE.name} is {width}×{height}; a banner has to be 16:9.")

    # Flattened rather than converted: a banner with an alpha channel draws however the launcher
    # decides to, and transparent pixels over a TV's background are not a decision we get to make.
    if source.mode in ("RGBA", "LA", "P"):
        source = source.convert("RGBA")
        flat = Image.new("RGB", source.size, (255, 255, 255))
        flat.paste(source, mask=source.getchannel("A"))
        source = flat
    else:
        source = source.convert("RGB")

    banner = source.resize(BANNER_SIZE, Image.LANCZOS)

    target = RES / "drawable-xhdpi" / "animato_tv_banner.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    banner.save(target, optimize=True)
    print(f"wrote {target.relative_to(RES.parent.parent.parent)} at {BANNER_SIZE[0]}×{BANNER_SIZE[1]}")


if __name__ == "__main__":
    main()
