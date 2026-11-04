"""Themed-icon layer: the foreground's shape in white, its alpha scaled by brightness so the details survive.
Run from the repo root after render-icons.js."""
import glob

from PIL import Image

for path in glob.glob("app/src/main/res/mipmap-*/ic_launcher_foreground.png"):
    src = Image.open(path).convert("RGBA")
    out = Image.new("RGBA", src.size)
    pixels, dest = src.load(), out.load()
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = pixels[x, y]
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            dest[x, y] = (255, 255, 255, int(a * (0.35 + 0.65 * lum)))
    out.save(path.replace("foreground", "monochrome"))
