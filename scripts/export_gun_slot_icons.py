"""Write the inventory icons of TaCZ guns into design/weapon-icons (requires Pillow).

TaCZ draws a gun's inventory icon from its gun pack's "slot" texture, which the client only indexes after joining a
server, so the in-game icon export (-PgungangIconExport) renders guns as missing textures. This script takes the same
slot textures straight from the server gun packs and fits them onto the same transparent 256x256 canvas.

Usage: python scripts/export_gun_slot_icons.py
"""
import json, re
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACKS = ROOT / 'server_resource/tacz'
CONFIG = ROOT / 'server_resource/config/gungang/server'
OUT = ROOT / 'design/weapon-icons'
SIZE, MARGIN = 256, 8

def key(text, name):
    match = re.search(r'"%s"\s*:\s*"([^"]+)"' % name, text)
    return match.group(1) if match else None

def find(relative):
    for pack in PACKS.iterdir():
        path = pack / relative
        if pack.is_dir() and path.is_file():
            return path
    return None

def slot_texture(gun):
    namespace, name = gun.split(':', 1)
    index = find(f'data/{namespace}/index/guns/{name}.json')
    if index is None: return None
    display = key(index.read_text(encoding='utf-8-sig'), 'display')
    if display is None: return None
    dns, dname = display.split(':', 1)
    display_file = find(f'assets/{dns}/display/guns/{dname}.json')
    if display_file is None: return None
    slot = key(display_file.read_text(encoding='utf-8-sig'), 'slot')
    if slot is None: return None
    sns, spath = slot.split(':', 1)
    return find(f'assets/{sns}/textures/{spath}.png')

def fit(image):
    image = image.convert('RGBA')
    box = image.getbbox()
    if box: image = image.crop(box)
    scale = (SIZE - 2 * MARGIN) / max(image.size)
    # Pixel-art slot icons stay crisp; large painted ones are resampled smoothly.
    resample = Image.Resampling.NEAREST if scale >= 1 and max(image.size) <= 64 else Image.Resampling.LANCZOS
    image = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), resample)
    canvas = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(image, ((SIZE - image.width) // 2, (SIZE - image.height) // 2))
    return canvas

count = missing = 0
for folder, kind in (('weapons', 'primary'), ('sub_weapons', 'secondary')):
    for file in sorted((CONFIG / folder).glob('*.json')):
        weapon = json.loads(file.read_text(encoding='utf-8-sig'))
        gun = re.search(r'GunId:"([^"]+)"', weapon.get('itemNbt', ''))
        if not gun: continue
        name = re.sub(r'[\\/:*?"<>|]', '_', weapon.get('name', weapon['id'])).strip()
        texture = slot_texture(gun.group(1))
        if texture is None:
            print('missing slot texture:', weapon['id'], name, gun.group(1)); missing += 1; continue
        (OUT / kind).mkdir(parents=True, exist_ok=True)
        fit(Image.open(texture)).save(OUT / kind / f"{weapon['id']}_{name}.png")
        count += 1
print(f'gun icons written: {count}, missing: {missing}')
