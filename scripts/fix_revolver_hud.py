"""Remove the speed-strip rod drawn behind the 09 revolver (bf1:sw_model3) in its TaCZ ammo HUD icons.

Usage: python scripts/fix_revolver_hud.py <pack assets/bf1 dir> [...]   (requires Pillow)
The Apocalypse pack's hud/sw_model3.png and sw_model3_empty.png show a long rod with three spikes across the revolver.
The rod is a horizontal band whose colour is constant along each row, so a band pixel is the rod's when it matches that
row's colour where only the rod is drawn (x = 200); the spikes left of the revolver go entirely. Where the rod crossed
the grip, the grip is filled in from the pixel above the band. Originals are kept next to the files as *.orig.png.
"""
import shutil, sys
from pathlib import Path
from PIL import Image

PURE_ROD_X = 200      # below the barrel, only the rod is drawn in this column (its lowest opaque run is the band)
SPIKES_END = 125      # left of this and below the barrel (y >= BARREL_BOTTOM) is the spiked end of the rod
BARREL_BOTTOM = 30
END_CAP_X = 360      # the rod's square end cap sits over the grip from here
CAP_MARGIN = 10      # the cap is a little taller than the band
GRIP_X = 320         # the solid grip, the only revolver part the rod covered (the trigger guard is hollow)

def fix(path):
    original = path.with_suffix('.orig.png')
    if not original.exists(): shutil.copyfile(path, original)
    image = Image.open(original).convert('RGBA'); px = image.load(); w, h = image.size
    empty = path.stem.endswith('_empty')
    column = [y for y in range(h) if px[PURE_ROD_X, y][3] > 0]
    bottom = column[-1]; top = bottom
    while top - 1 >= 0 and px[PURE_ROD_X, top - 1][3] > 0: top -= 1
    assert top > BARREL_BOTTOM and bottom - top < 30, (path, top, bottom)
    removed = set()
    for x in range(w):
        for y in range(h):
            p = px[x, y]
            if p[3] == 0: continue
            if (x < SPIKES_END and y >= BARREL_BOTTOM) or (top <= y <= bottom and p == px[PURE_ROD_X, y]):
                removed.add((x, y))
            # The rod's end cap over the grip: grey where the grip is light grey (normal) or red (empty).
            elif x >= END_CAP_X and top - CAP_MARGIN <= y <= bottom + CAP_MARGIN and p[0] == p[1] == p[2] and (empty or p[0] < 150):
                removed.add((x, y))
    for x, y in removed: px[x, y] = (0, 0, 0, 0)
    # Where the rod crossed the revolver (kept pixels above and below in the same column), continue the revolver
    # through it with the nearest kept pixel above.
    for x in range(GRIP_X, w):
        for y in range(h):
            if (x, y) not in removed: continue
            up = next((px[x, k] for k in range(y - 1, -1, -1) if (x, k) not in removed and px[x, k][3] > 0), None)
            down = any((x, k) not in removed and px[x, k][3] > 0 for k in range(y + 1, h))
            if up is not None and down: px[x, y] = up
    image.save(path)
    print('fixed', path, 'band', top, bottom, 'removed', len(removed))

for root in sys.argv[1:]:
    for name in ('sw_model3.png', 'sw_model3_empty.png'):
        fix(Path(root) / 'textures/gun/hud' / name)
