"""Muzzle 'flash' of the 35 classic blower: pale swirling gust streaks on transparency (192 px, like TaCZ flash textures)."""
import math, random, sys
from PIL import Image, ImageDraw, ImageFilter
S = 192
out = sys.argv[1]
random.seed(35)
layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(layer)
c = S / 2
for i in range(14):
    r0 = random.uniform(18, 80); turn = random.uniform(1.6, 3.4); start = random.uniform(0, 2 * math.pi)
    width = random.randint(3, 8); alpha = random.randint(90, 190)
    tint = random.choice([(255, 255, 255), (225, 245, 255), (200, 235, 250)])
    pts = []
    for k in range(40):
        t = k / 39
        a = start + turn * t
        r = r0 * (1 - .55 * t) + 6
        pts.append((c + math.cos(a) * r, c + math.sin(a) * r * .8))
    for k in range(len(pts) - 1):
        fade = math.sin(math.pi * k / (len(pts) - 1))
        d.line([pts[k], pts[k + 1]], fill=tint + (int(alpha * fade),), width=width)
glow = layer.filter(ImageFilter.GaussianBlur(6))
sharp = layer.filter(ImageFilter.GaussianBlur(1.2))
img = Image.alpha_composite(glow, sharp)
# soft round falloff so the quad's edges never show
mask = Image.new('L', (S, S), 0); md = ImageDraw.Draw(mask)
for k in range(48):
    md.ellipse([c - (96 - k * 2), c - (96 - k * 2), c + (96 - k * 2), c + (96 - k * 2)], fill=int(255 * min(1, k / 20)))
r, g, b, a = img.split()
a = Image.composite(a, Image.new('L', (S, S), 0), mask)
img = Image.merge('RGBA', (r, g, b, a))
img.save(out)
print('saved', out)
