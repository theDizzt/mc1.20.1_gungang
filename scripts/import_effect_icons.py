"""Copy the health bar status icons from design/logo/effect-*.png into the mod, scaled to 64 px.

Usage: python scripts/import_effect_icons.py   (requires Pillow)
Writes assets/gungang/textures/gui/effect/<name>.png for heal, flag, swift, invisible, burn, poison, shock, bleed,
exposure and nausea (StatusIcons draws them inside the health bar).
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/logo'
TARGET = ROOT / 'src/main/resources/assets/gungang/textures/gui/effect'
NAMES = ['heal', 'flag', 'swift', 'invisible', 'burn', 'poison', 'shock', 'bleed', 'exposure', 'nausea']
SIZE = 64

TARGET.mkdir(parents=True, exist_ok=True)
for name in NAMES:
    image = Image.open(SOURCE / f'effect-{name}.png').convert('RGBA')
    # Trim the transparent margin, then fit the artwork into a centred square so every icon reads at the same size.
    box = image.getbbox()
    if box: image = image.crop(box)
    side = max(image.size)
    square = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    square.alpha_composite(image, ((side - image.width) // 2, (side - image.height) // 2))
    square.resize((SIZE, SIZE), Image.LANCZOS).save(TARGET / f'{name}.png')
print('Imported %d effect icons at %d px' % (len(NAMES), SIZE))
