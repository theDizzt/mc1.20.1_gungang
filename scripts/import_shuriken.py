"""Convert the Sketchfab "Shuriken" glTF (zip with scene.gltf/scene.bin/textures) to the 33 shuriken OBJ item model.

Usage: python scripts/import_shuriken.py <shuriken.zip>
Writes assets/gungang/models/shuriken.obj/.mtl, textures/item/shuriken.png, models/item/shuriken.json and
META-INF/licenses/shuriken.txt. The flat star is stood up to face +Z (like a vanilla flat item), centered and scaled to
one block, so the GUI icon shows it face-on and the thrown entity can spin it about its own axis.
"""
import io, json, sys, zipfile
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/gungang'
archive = zipfile.ZipFile(sys.argv[1])
scene = json.loads(archive.read('scene.gltf'))
blob = archive.read(scene['buffers'][0]['uri'])

def accessor(index):
    a = scene['accessors'][index]; v = scene['bufferViews'][a['bufferView']]
    dtype = {5126: '<f4', 5125: '<u4', 5123: '<u2', 5121: 'u1'}[a['componentType']]
    width = {'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'SCALAR': 1}[a['type']]
    item = np.dtype(dtype).itemsize
    return np.ndarray((a['count'], width), dtype=dtype, buffer=blob,
                      offset=v.get('byteOffset', 0) + a.get('byteOffset', 0),
                      strides=(v.get('byteStride', item * width), item)).astype(float)

primitive = scene['meshes'][0]['primitives'][0]
assert primitive.get('mode', 4) == 4
pos = accessor(primitive['attributes']['POSITION'])
normal = accessor(primitive['attributes']['NORMAL'])
uv = accessor(primitive['attributes']['TEXCOORD_0'])
faces = accessor(primitive['indices']).astype(int).reshape((-1, 3))
# The star lies in the mesh's XZ plane; a +90° turn about X (a rotation, so faces keep their winding) faces it to +Z.
turn = np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], dtype=float)
pos = pos @ turn.T; normal = normal @ turn.T
low = pos.min(0); high = pos.max(0)
size = max(high - low); center = (low + high) / 2
pos = (pos - center) / size + .5

lines = ['mtllib shuriken.mtl', 'o shuriken', 'usemtl shuriken']
lines += ['v ' + ' '.join(f'{v:.7f}' for v in row) for row in pos]
lines += [f'vt {u:.7f} {1 - v:.7f}' for u, v in uv]
lines += ['vn ' + ' '.join(f'{v:.7f}' for v in row) for row in normal]
lines += ['f ' + ' '.join(f'{j + 1}/{j + 1}/{j + 1}' for j in row) for row in faces]
(assets / 'models/shuriken.obj').write_text('\n'.join(lines) + '\n', encoding='utf-8')
(assets / 'models/shuriken.mtl').write_text('newmtl shuriken\nKd 1 1 1\nmap_Kd gungang:item/shuriken\n', encoding='utf-8')

image = Image.open(io.BytesIO(archive.read(scene['images'][0]['uri']))).convert('RGBA')
image.thumbnail((256, 256), Image.Resampling.LANCZOS)
image.save(assets / 'textures/item/shuriken.png')

model = {'loader': 'forge:obj', 'model': 'gungang:models/shuriken.obj', 'automatic_culling': False,
         'flip_v': True, 'render_type': 'minecraft:cutout', 'textures': {'particle': 'gungang:item/shuriken'},
         'display': {'gui': {'rotation': [0, 0, 20], 'scale': [1.0] * 3},
                     'ground': {'rotation': [90, 0, 0], 'scale': [.45] * 3, 'translation': [0, 1, 0]},
                     'fixed': {'scale': [1.0] * 3},
                     # Held flat between the fingers, ready to be flicked forward.
                     'firstperson_righthand': {'rotation': [80, 0, -20], 'translation': [0, 4, 0], 'scale': [.55] * 3},
                     'firstperson_lefthand': {'rotation': [80, 0, 20], 'translation': [0, 4, 0], 'scale': [.55] * 3},
                     'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 3, 1], 'scale': [.45] * 3},
                     'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 3, 1], 'scale': [.45] * 3}}}
(assets / 'models/item/shuriken.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
extras = scene.get('asset', {}).get('extras', {})
license_path = root / 'src/main/resources/META-INF/licenses/shuriken.txt'
license_path.write_text(f'"{extras.get("title", "Shuriken")}" by {extras.get("author", "unknown author")}, {extras.get("source", "")}\n'
                        f'Licensed under {extras.get("license", "see source")}.\n'
                        'Modifications: glTF converted to OBJ; turned to face +Z; scale normalized; texture downsized to PNG.\n',
                        encoding='utf-8')
print(f'Converted {len(pos)} vertices, {len(faces)} triangles; bounds {low} .. {high}')
