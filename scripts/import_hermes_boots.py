"""Convert boots-of-swiftness GLB (a Minecraft-scale boot in 1/1000 px units) to the 74 Hermes' Boots OBJ item model.

Usage: python scripts/import_hermes_boots.py <boots.glb>
The GLB holds one boot: a 4x6x4 px shaft (x/z -2..2, y 17..23), a 1x2x4 px toe on -x and flat wing sprites on
both +-z sides. It keeps its pixel scale (unlike import_glb_item.py, which normalizes size) so the client layer can
fit it over the lower 6 px of a player leg: the shaft centre (0, 20, 0) px becomes the model centre (0.5, 0.5, 0.5).
Untextured vertex-colour primitives (zero-thickness wing edges, the toe face hidden against the shaft) are dropped,
and every face is pushed 0.01 px along its normal so the back-to-back wing sprites do not z-fight.
Writes models/hermes_boots.obj/.mtl, textures/item/hermes_boots_<n>.png, models/item/hermes_boots.json.
"""
import io, json, struct, sys
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/gungang'
item = 'hermes_boots'
data = Path(sys.argv[1]).read_bytes()
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
    size = np.dtype(dtype).itemsize
    return np.ndarray((a['count'], width), dtype=dtype, buffer=blob,
                      offset=v.get('byteOffset', 0) + a.get('byteOffset', 0),
                      strides=(v.get('byteStride', size * width), size)).copy()


CENTER = np.array([0.0, 20.0, 0.0])
lines = [f'mtllib {item}.mtl']; offset = 1; used = set()
for n, node in enumerate(scene['nodes']):
    m = np.array(node['matrix'], dtype=float).reshape((4, 4)).T
    for p in scene['meshes'][node['mesh']]['primitives']:
        if 'material' not in p or 'TEXCOORD_0' not in p['attributes']:
            continue
        pos = (accessor(p['attributes']['POSITION']) @ m[:3, :3].T + m[:3, 3]) * 1000  # GLB units -> pixels
        uv = accessor(p['attributes']['TEXCOORD_0'])
        faces = accessor(p['indices']).reshape((-1, 3))
        a, b, c = pos[faces[0]]
        normal = np.cross(b - a, c - a); normal /= max(np.linalg.norm(normal), 1e-9)
        pos = pos + normal * .01
        pos = (pos - CENTER) / 16 + .5
        lines += [f'o part_{n}_{p["material"]}', f'usemtl material_{p["material"]}']
        lines += ['v ' + ' '.join(f'{v:.6f}' for v in row) for row in pos]
        lines += [f'vt {u:.6f} {1 - v:.6f}' for u, v in uv]
        lines += ['vn ' + ' '.join(f'{v:.6f}' for v in normal) for _ in pos]
        lines += ['f ' + ' '.join(f'{j + offset}/{j + offset}/{j + offset}' for j in row) for row in faces]
        offset += len(pos); used.add(p['material'])
(assets / f'models/{item}.obj').write_text('\n'.join(lines) + '\n', encoding='utf-8')

mtl = []
for i in sorted(used):
    source = scene['images'][scene['textures'][scene['materials'][i]['pbrMetallicRoughness']['baseColorTexture']['index']]['source']]
    Image.open(io.BytesIO(view(source['bufferView']))).convert('RGBA').save(assets / f'textures/item/{item}_{i}.png')
    mtl += [f'newmtl material_{i}', 'Kd 1 1 1', f'map_Kd gungang:item/{item}_{i}']
(assets / f'models/{item}.mtl').write_text('\n'.join(mtl) + '\n', encoding='utf-8')

# Worn on the legs by HermesBootsLayer; the hands hide it (scale 0) like the jetpack so it is not drawn twice.
hidden = {'scale': [0, 0, 0]}
model = {'loader': 'forge:obj', 'model': f'gungang:models/{item}.obj', 'automatic_culling': False,
         'flip_v': True, 'render_type': 'minecraft:cutout', 'textures': {'particle': f'gungang:item/{item}_4'},
         'display': {'gui': {'rotation': [20, 135, 0], 'scale': [1.7] * 3},
                     'ground': {'scale': [.9] * 3, 'translation': [0, 3, 0]},
                     'fixed': {'rotation': [0, 90, 0], 'scale': [1.6] * 3},
                     'firstperson_righthand': hidden, 'firstperson_lefthand': hidden,
                     'thirdperson_righthand': hidden, 'thirdperson_lefthand': hidden}}
(assets / f'models/item/{item}.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
license_path = root / f'src/main/resources/META-INF/licenses/{item}.txt'
license_path.write_text('"Boots of Swiftness" model (boots-of-swiftness.zip), supplied by the project owner.\n'
                        'The GLB carries no author or license metadata; confirm the terms before redistribution.\n'
                        'Modifications: glTF (GLB) converted to OBJ; untextured edge faces dropped; textures exported to PNG.\n',
                        encoding='utf-8')
print(f'Wrote {offset - 1} vertices, {len(used)} textures')
