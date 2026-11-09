"""Generate the 방벽 (barrier, sub weapon 94) textures and item model: weathered gray boards with a no-entry sign.

Usage: python scripts/make_barrier_resources.py  (requires Pillow)
Writes textures/entity/barrier_planks.png and barrier_sign.png (the placed 3x3 wall, 32 px per block),
textures/item/barrier_face.png / barrier_edge.png and models/item/barrier.json (a thin panel item).
"""
import json, math, random
from pathlib import Path
from PIL import Image

assets = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/gungang'
rng = random.Random(94)

def clamp(c): return tuple(max(0, min(255, round(v))) for v in c)
def shade(c, f): return clamp(v * f for v in c)
def mix(a, b, t): return clamp(x + (y - x) * t for x, y in zip(a, b))

GAP = (38, 36, 34)
WOOD = [(128, 126, 120), (116, 114, 109), (138, 135, 128), (108, 106, 101), (124, 121, 114), (132, 130, 124)]
RED_PAINT = (150, 40, 38)
SIGN_RED = (214, 52, 46)
WHITE = (228, 230, 226)

def planks(size, boards, seed, paint=True, nails=True):
    """Horizontal weathered boards with dark gaps, grain streaks, butt joints and a couple of faded red stripes."""
    r = random.Random(seed); img = Image.new('RGBA', (size, size)); px = img.load()
    h = size // boards; gap = max(1, h // 8)
    for b in range(boards):
        base = WOOD[(b * 7 + seed) % len(WOOD)]
        top = b * h
        joints = [r.randrange(size // 4, size * 3 // 4)] if size >= 32 and r.random() < .6 else []
        # Long grain streaks: each row of the board gets its own slight tone, broken into runs.
        rows = {}
        for y in range(top, top + h - gap):
            x = 0
            while x < size:
                run = r.randrange(3, max(4, size // 3)); tone = r.uniform(.88, 1.08)
                for i in range(x, min(size, x + run)): rows[(i, y)] = tone
                x += run
        striped = paint and size >= 32 and b in (0, boards - 2)
        for y in range(top, top + h):
            for x in range(size):
                if y >= top + h - gap: px[x, y] = GAP + (255,); continue
                c = shade(base, rows[(x, y)] * r.uniform(.97, 1.03))
                if y == top: c = shade(c, 1.12)                     # lit upper edge
                if y == top + h - gap - 1: c = shade(c, .8)          # shadowed lower edge
                if striped and top + h // 4 <= y < top + h // 4 + h // 3 and r.random() < .8:
                    c = mix(c, RED_PAINT, .65)
                if any(abs(x - j) == 0 for j in joints): c = GAP
                elif any(abs(x - j) == 1 for j in joints): c = shade(c, .82)
                px[x, y] = c + (255,)
        if nails and size >= 32:
            for nx in [3, size - 4] + [j + d for j in joints for d in (-3, 3)]:
                for ny in (top + (h - gap) // 3, top + 2 * (h - gap) // 3):
                    if 0 <= nx < size: px[nx, ny] = (58, 54, 50, 255)
    return img

def sign(size, scale):
    """The no-entry sign alone (transparent elsewhere): white rim, red disk, white bar with two bolts, worn paint."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0)); px = img.load()
    c = (size - 1) / 2; outer = 38 * scale; rim = max(1.2, 3 * scale); bar_w, bar_h, corner = 27 * scale, 7 * scale, 2.5 * scale
    r = random.Random(7)
    cracks = []
    if size >= 32:  # thin dark cracks across the paint, like the photographed sign
        for _ in range(9):
            a = r.uniform(0, 2 * math.pi); d = r.uniform(5, 30) * scale; L = r.uniform(5, 16) * scale; t = r.uniform(0, math.pi)
            sx, sy = c + d * math.cos(a), c + d * math.sin(a)
            cracks.append((sx, sy, sx + L * math.cos(t), sy + L * math.sin(t)))
    def seg(x, y, s):
        x0, y0, x1, y1 = s; dx, dy = x1 - x0, y1 - y0
        t = max(0, min(1, ((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy)))
        return math.hypot(x - x0 - t * dx, y - y0 - t * dy)
    def bar(x, y):
        qx = max(abs(x - c) - (bar_w - corner), 0); qy = max(abs(y - c) - (bar_h - corner), 0)
        return math.hypot(qx, qy) <= corner
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if d > outer + .5: continue
            if size >= 32 and d > outer - .5: col = (150, 152, 150)        # metal edge
            elif d > outer - rim: col = WHITE
            elif bar(x, y): col = WHITE
            else:
                col = shade(SIGN_RED, 1.04 - .1 * (d / outer) + r.uniform(-.03, .03))
                if size >= 32 and any(seg(x, y, s) < .6 for s in cracks): col = shade(col, .72)
            if col == WHITE: col = shade(col, r.uniform(.94, 1.0))
            px[x, y] = clamp(col) + (255,)
    for bx in ((-1, 1) if size >= 32 else ()):  # bolts
        x0, y0 = round(c + bx * (bar_w - 4 * scale)), round(c)
        for dx in range(-max(1, round(scale)) + 1, max(1, round(scale)) + 1):
            for dy in range(-max(1, round(scale)) + 1, max(1, round(scale)) + 1):
                px[x0 + dx, y0 + dy] = (70, 72, 74, 255)
    if size >= 32:  # chipped paint on the bar
        for _ in range(5):
            x, y = round(c + r.uniform(-.5, .5) * bar_w), round(c + r.uniform(-.6, .6) * bar_h)
            px[x, y] = (150, 190, 200, 255)
    return img

def shadow_under(board, mark, offset):
    """Darken the boards where the slightly raised sign casts a shadow."""
    out = board.copy(); bp = out.load(); mp = mark.load()
    for y in range(out.height):
        for x in range(out.width):
            sx, sy = x - offset[0], y - offset[1]
            if 0 <= sx < out.width and 0 <= sy < out.height and mp[sx, sy][3]:
                bp[x, y] = shade(bp[x, y][:3], .62) + (255,)
    return out

# Placed wall: 3x3 blocks at 32 px per block.
mark = sign(96, 1.0)
shadow_under(planks(96, 6, 3), mark, (1, 2)).save(assets / 'textures/entity/barrier_planks.png')
mark.save(assets / 'textures/entity/barrier_sign.png')

# Item: one 16 px panel with the sign baked in.
face = planks(16, 4, 1, paint=False, nails=False)
# Hand-drawn 12 px sign: W white rim, R red, B white bar.
SMALL = ['    WWWW    ',
         '  WWRRRRWW  ',
         ' WRRRRRRRRW ',
         ' WRRRRRRRRW ',
         'WRRRRRRRRRRW',
         'WRBBBBBBBBRW',
         'WRBBBBBBBBRW',
         'WRRRRRRRRRRW',
         ' WRRRRRRRRW ',
         ' WRRRRRRRRW ',
         '  WWRRRRWW  ',
         '    WWWW    ']
small = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y, row in enumerate(SMALL):
    for x, k in enumerate(row):
        if k != ' ': small.putpixel((x + 2, y + 2), {'W': WHITE, 'R': SIGN_RED, 'B': WHITE}[k] + (255,))
face.alpha_composite(small)
face.save(assets / 'textures/item/barrier_face.png')
planks(16, 4, 2, paint=False, nails=False).save(assets / 'textures/item/barrier_edge.png')

all_faces = {'north': {'uv': [0, 0, 16, 16], 'texture': '#face'}, 'south': {'uv': [16, 0, 0, 16], 'texture': '#face'},
             'east': {'uv': [5, 0, 11, 16], 'texture': '#edge'}, 'west': {'uv': [5, 0, 11, 16], 'texture': '#edge'},
             'up': {'uv': [0, 5, 16, 11], 'texture': '#edge'}, 'down': {'uv': [0, 5, 16, 11], 'texture': '#edge'}}
model = {'parent': 'minecraft:block/block',
         'textures': {'face': 'gungang:item/barrier_face', 'edge': 'gungang:item/barrier_edge', 'particle': 'gungang:item/barrier_face'},
         'elements': [{'from': [0, 0, 6], 'to': [16, 16, 10], 'faces': all_faces}],
         'display': {'gui': {'rotation': [20, 200, 0], 'scale': [.68] * 3},
                     'fixed': {'rotation': [0, 180, 0], 'scale': [.6] * 3}}}
(assets / 'models/item/barrier.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
print('ok')
