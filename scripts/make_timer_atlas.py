"""Bake the supplied LCD font into centered, fixed-width HUD cells (Pillow)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONT = ROOT / "font/advanced_pixel_lcd-7.ttf"
TARGET = ROOT / "src/main/resources/assets/gungang_acoustics/textures/gui/timer.png"
CHARS = "0123456789:"
CELL_W, CELL_H = 30, 48  # Draw at 1/3 scale: 10 x 16 HUD pixels.


def build():
    font = ImageFont.truetype(str(FONT), 24)
    atlas = Image.new("RGBA", (CELL_W * len(CHARS), CELL_H))
    for index, char in enumerate(CHARS):
        source = Image.new("RGBA", (100, CELL_H))
        mask = Image.new("L", source.size)
        ImageDraw.Draw(mask).text((30, 42), char, font=font, anchor="ls", fill=255)
        left, _, right, _ = mask.getbbox()
        # Font.getbbox includes advance whitespace; measure rasterized ink instead.
        ImageDraw.Draw(source).text((30, 42), char,
                  font=font, anchor="ls", fill="white", stroke_width=1, stroke_fill="black")
        glyph=source.crop((left-1, 0, right+1, CELL_H))
        cell = Image.new("RGBA", (CELL_W, CELL_H))
        cell.alpha_composite(glyph, (round((CELL_W-glyph.width)/2), 0))
        atlas.alpha_composite(cell, (index * CELL_W, 0))
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(TARGET)


if __name__ == "__main__":
    build()
