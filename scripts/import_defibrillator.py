"""Convert the supplied defibrillator (AED) low-poly GLB archive to a Forge OBJ item model (requires numpy/Pillow).

Usage: python scripts/import_defibrillator.py <defibrillator-low-poly.zip>
Writes models/defibrillator.obj/.mtl, textures/item/defibrillator_<n>.png and models/item/defibrillator.json.
"""
import io, json, struct, sys, zipfile
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/gungang'
with zipfile.ZipFile(sys.argv[1]) as archive:
    name = next(n for n in archive.namelist() if n.lower().endswith('.glb'))
    data = archive.read(name)
length = struct.unpack_from('<I', data, 12)[0]
scene = json.loads(data[20:20 + length])
blob = data[20 + length + 8:]

def view(index):
    v = scene['bufferViews'][index]
    return blob[v.get('byteOffset', 0):v.get('byteOffset', 0) + v['byteLength']]

def accessor(index):
    a = scene['accessors'][index]; v = scene['bufferViews'][a['bufferView']]
    dtype = {5126: '<f4', 5125: '<u4', 5123: '<u2', 5121: 'u1'}[a['componentType']]
    width = {'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'SCALAR': 1}[a['type']]
    item = np.dtype(dtype).itemsize
    return np.ndarray((a['count'], width), dtype=dtype, buffer=blob,
                      offset=v.get('byteOffset', 0) + a.get('byteOffset', 0),
                      strides=(v.get('byteStride', item * width), item)).copy()

parts = []
for node in scene['nodes']:
    assert 'matrix' not in node and 'rotation' not in node and 'children' not in node, 'node transforms not supported'
    scale = np.array(node.get('scale', [1, 1, 1])); move = np.array(node.get('translation', [0, 0, 0]))
    if 'mesh' not in node: continue
    for p in scene['meshes'][node['mesh']]['primitives']:
        assert p.get('mode', 4) == 4
        pos = accessor(p['attributes']['POSITION']) * scale + move
        normal = accessor(p['attributes']['NORMAL']) / scale
        normal /= np.linalg.norm(normal, axis=1)[:, None]
        faces = accessor(p['indices']).reshape((-1, 3))
        parts.append((pos, normal, accessor(p['attributes']['TEXCOORD_0']), faces, p['material']))

points = np.concatenate([p[0] for p in parts]); low = points.min(0); high = points.max(0)
size = max(high - low); center = (low + high) / 2
lines = ['mtllib defibrillator.mtl']; offset = 1
for i, (pos, normal, uv, faces, material) in enumerate(parts):
    pos = (pos - center) / size + .5
    lines += [f'o part_{i}', f'usemtl material_{material}']
    lines += ['v ' + ' '.join(f'{v:.7f}' for v in row) for row in pos]
    lines += [f'vt {u:.7f} {1 - v:.7f}' for u, v in uv]
    lines += ['vn ' + ' '.join(f'{v:.7f}' for v in row) for row in normal]
    lines += ['f ' + ' '.join(f'{j + offset}/{j + offset}/{j + offset}' for j in row) for row in faces]
    offset += len(pos)
(assets / 'models/defibrillator.obj').write_text('\n'.join(lines) + '\n', encoding='utf-8')

mtl = []
for i, m in enumerate(scene['materials']):
    pbr = m.get('pbrMetallicRoughness', {})
    if 'baseColorTexture' in pbr:
        source = scene['images'][scene['textures'][pbr['baseColorTexture']['index']]['source']]
        image = Image.open(io.BytesIO(view(source['bufferView']))).convert('RGBA')
        image.thumbnail((1024, 1024), Image.Resampling.LANCZOS)
    else:
        # Flat-coloured materials (black rubber, chrome) become a small solid texture.
        r, g, b, a = [round(c * 255) for c in pbr.get('baseColorFactor', [1, 1, 1, 1])]
        image = Image.new('RGBA', (16, 16), (r, g, b, a))
    image.save(assets / f'textures/item/defibrillator_{i}.png')
    mtl += [f'newmtl material_{i}', 'Kd 1 1 1', f'map_Kd gungang:item/defibrillator_{i}']
(assets / 'models/defibrillator.mtl').write_text('\n'.join(mtl) + '\n', encoding='utf-8')

model = {'loader': 'forge:obj', 'model': 'gungang:models/defibrillator.obj', 'automatic_culling': False,
         'flip_v': True, 'render_type': 'minecraft:cutout', 'textures': {'particle': 'gungang:item/defibrillator_0'},
         'display': {'gui': {'rotation': [20, 225, 0], 'scale': [.9] * 3},
                     'ground': {'scale': [.35] * 3, 'translation': [0, 3, 0]},
                     'fixed': {'scale': [.8] * 3},
                     'firstperson_righthand': {'rotation': [0, 135, 0], 'translation': [0, 2, 0], 'scale': [.55] * 3},
                     'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 2, 0], 'scale': [.55] * 3},
                     'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 2, 1], 'scale': [.45] * 3},
                     'thirdperson_lefthand': {'rotation': [0, 270, 0], 'translation': [0, 2, 1], 'scale': [.45] * 3}}}
(assets / 'models/item/defibrillator.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
for language, label in [('ko_kr', '제세동기'), ('en_us', 'Defibrillator')]:
    # Insert one line next to the arrow grenade, keeping the file's own formatting.
    path = assets / f'lang/{language}.json'; raw = path.read_bytes(); text = raw.decode('utf-8-sig')
    if '"item.gungang.defibrillator"' not in text:
        nl = '
' if '
' in text else '
'; at = text.index(nl, text.index('"item.gungang.arrow_grenade"')) + len(nl)
        text = text[:at] + f'    "item.gungang.defibrillator":  "{label}",' + nl + text[at:]
        path.write_bytes((b'ï»¿' if raw.startswith(b'ï»¿') else b'') + text.encode('utf-8'))
license_path = root / 'src/main/resources/META-INF/licenses/defibrillator.txt'
license_path.parent.mkdir(parents=True, exist_ok=True)
license_path.write_text('"AED / defibrillator (low poly)" by theappxr, from the supplied archive defibrillator-low-poly.zip.\n'
                        'Modifications: glTF (GLB) converted to OBJ; scale normalized; base-colour texture converted to PNG; '
                        'flat-coloured materials baked to solid textures.\n', encoding='utf-8')
print(f'Converted {len(parts)} parts, {offset - 1} vertices; original bounds {low} .. {high}')
