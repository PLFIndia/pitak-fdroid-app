#!/usr/bin/env python3
"""Render the F-Droid listing icon from the app's adaptive launcher icon.

Reproduces the on-device composition of
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml:
  background = @color/pitaka_saffron_400 (#E25822)
  foreground = @drawable/ic_launcher_foreground (108x108 vector)

The three foreground <path> elements are simple closed polygons, so they are
drawn directly (no SVG parser needed). Supersampled 8x then downscaled with
LANCZOS for crisp edges. Output: 512x512 PNG, the size F-Droid expects for the
fastlane metadata listing icon.
"""
from PIL import Image, ImageDraw

VIEWPORT = 108          # vector viewportWidth/Height
TARGET = 512            # F-Droid listing icon size
SS = 8                  # supersampling factor
SIZE = TARGET * SS
SCALE = SIZE / VIEWPORT

BG = (0xE2, 0x58, 0x22)        # pitaka_saffron_400
PAGE = (0xFF, 0xFA, 0xF5)      # #FFFAF5
SPINE = (0x80, 0x80, 0x80)     # #808080 (F-Droid variant)

# (points in 108-viewport coords, fill)
PATHS = [
    ([(36, 40), (36, 72), (52, 68), (52, 36)], PAGE),
    ([(72, 40), (72, 72), (56, 68), (56, 36)], PAGE),
    ([(52, 36), (52, 68), (56, 68), (56, 36)], SPINE),
]


def main() -> None:
    img = Image.new("RGB", (SIZE, SIZE), BG)
    draw = ImageDraw.Draw(img)
    for pts, fill in PATHS:
        scaled = [(x * SCALE, y * SCALE) for (x, y) in pts]
        draw.polygon(scaled, fill=fill)
    img = img.resize((TARGET, TARGET), Image.LANCZOS)
    out = "fastlane/metadata/android/en-US/images/icon.png"
    img.save(out, "PNG")
    print(f"wrote {out} ({TARGET}x{TARGET})")


if __name__ == "__main__":
    main()
