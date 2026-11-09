"""Convert a Sketchfab-style GLB to a GunGang Forge OBJ item model (requires numpy/Pillow).

Usage: python scripts/import_glb_item.py <model.glb> <item id>
  e.g. potion_bottle.glb recovery_potion, autoinjector.glb adrenaline
Writes models/<item>.obj/.mtl, textures/item/<item>_<n>.png, models/item/<item>.json and
META-INF/licenses/<item>.txt (from the GLB's author / license / source extras).
The longest axis of the model is stood upright, so long thin props (injectors) read like the bottle.
"""
import io, json, struct, sys
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/gungang'
data = Path(sys.argv[1]).read_bytes(); item = sys.argv[2]
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

def local(node):
    if 'matrix' in node: return np.array(node['matrix'], dtype=float).reshape((4, 4)).T
    assert 'rotation' not in node, 'quaternion rotations not supported'
    m = np.diag(list(node.get('scale', [1, 1, 1])) + [1.0]); m[:3, 3] = node.get('translation', [0, 0, 0])
    return m

parts = []
def walk(index, parent):
    node = scene['nodes'][index]; m = parent @ local(node)
    if 'mesh' in node:
        for p in scene['meshes'][node['mesh']]['primitives']:
            assert p.get('mode', 4) == 4
            pos = accessor(p['attributes']['POSITION']); pos = pos @ m[:3, :3].T + m[:3, 3]
            normal = accessor(p['attributes']['NORMAL']) @ np.linalg.inv(m[:3, :3])
            normal /= np.maximum(np.linalg.norm(normal, axis=1)[:, None], 1e-9)
            faces = accessor(p['indices']).reshape((-1, 3))
            parts.append((pos, normal, accessor(p['attributes']['TEXCOORD_0']), faces, p['material']))
    for child in node.get('children', []): walk(child, m)
for top in scene['scenes'][scene.get('scene', 0)]['nodes']: walk(top, np.eye(4))

points = np.concatenate([p[0] for p in parts]); extent = points.max(0) - points.min(0)
up = int(np.argmax(extent))
if up != 1:  # swap the long axis onto Y (a rotation, so faces keep their winding)
    turn = np.eye(3); turn[[up, 1]] = turn[[1, up]]; turn[up] *= -1
    parts = [(pos @ turn.T, normal @ turn.T, uv, faces, material) for pos, normal, uv, faces, material in parts]
    points = np.concatenate([p[0] for p in parts])
low = points.min(0); high = points.max(0)
size = max(high - low); center = (low + high) / 2
lines = [f'mtllib {item}.mtl']; offset = 1
# The glass last, so the liquid and cork behind it are already drawn when it blends over them.
for i, (pos, normal, uv, faces, material) in enumerate(sorted(parts, key=lambda p: scene['materials'][p[4]].get('alphaMode') == 'BLEND')):
    pos = (pos - center) / size + .5
    lines += [f'o part_{i}', f'usemtl material_{material}']
    lines += ['v ' + ' '.join(f'{v:.7f}' for v in row) for row in pos]
    lines += [f'vt {u:.7f} {1 - v:.7f}' for u, v in uv]
    lines += ['vn ' + ' '.join(f'{v:.7f}' for v in row) for row in normal]
    lines += ['f ' + ' '.join(f'{j + offset}/{j + offset}/{j + offset}' for j in row) for row in faces]
    offset += len(pos)
(assets / f'models/{item}.obj').write_text('\n'.join(lines) + '\n', encoding='utf-8')

mtl = []
for i, m in enumerate(scene['materials']):
    pbr = m.get('pbrMetallicRoughness', {})
    if 'baseColorTexture' in pbr:
        source = scene['images'][scene['textures'][pbr['baseColorTexture']['index']]['source']]
        image = Image.open(io.BytesIO(view(source['bufferView']))).convert('RGBA')
        image.thumbnail((512, 512), Image.Resampling.LANCZOS)
    else:
        r, g, b, a = [round(c * 255) for c in pbr.get('baseColorFactor', [1, 1, 1, 1])]
        image = Image.new('RGBA', (16, 16), (r, g, b, a))
    if m.get('alphaMode') != 'BLEND': image.putalpha(255)
    image.save(assets / f'textures/item/{item}_{i}.png')
    mtl += [f'newmtl material_{i}', 'Kd 1 1 1', f'map_Kd gungang:item/{item}_{i}']
(assets / f'models/{item}.mtl').write_text('\n'.join(mtl) + '\n', encoding='utf-8')

model = {'loader': 'forge:obj', 'model': f'gungang:models/{item}.obj', 'automatic_culling': False,
         'flip_v': True, 'render_type': 'minecraft:translucent', 'textures': {'particle': f'gungang:item/{item}_0'},
         'display': {'gui': {'rotation': [15, 225, 0], 'scale': [.95] * 3},
                     'ground': {'scale': [.4] * 3, 'translation': [0, 3, 0]},
                     'fixed': {'scale': [.85] * 3},
                     'firstperson_righthand': {'rotation': [0, 135, 0], 'translation': [0, 3, 0], 'scale': [.5] * 3},
                     'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 3, 0], 'scale': [.5] * 3},
                     'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 3, 1], 'scale': [.4] * 3},
                     'thirdperson_lefthand': {'rotation': [0, 270, 0], 'translation': [0, 3, 1], 'scale': [.4] * 3}}}
(assets / f'models/item/{item}.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
license_path = root / f'src/main/resources/META-INF/licenses/{item}.txt'
license_path.parent.mkdir(parents=True, exist_ok=True)
extras = scene.get("asset", {}).get("extras", {})
title, author, source, terms = (extras.get(k, d) for k, d in [("title", item), ("author", "unknown author"), ("source", ""), ("license", "see source")])
license_path.write_text(f'"{title}" by {author}, {source}\n'
                        f'Licensed under {terms}.\n'
                        'Modifications: glTF (GLB) converted to OBJ; reoriented upright; scale normalized; '
                        'textures downsized to PNG; emissive / metallic maps dropped.\n', encoding='utf-8')
print(f'Converted {len(parts)} parts, {offset - 1} vertices; bounds {low} .. {high}')
