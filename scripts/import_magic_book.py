"""Convert the "golden academy book" Blockbench glTF (zip with source/model.gltf, embedded buffers and textures) to the
37 magic book OBJ item model.

Usage: python scripts/import_magic_book.py <golden-academy-book-ia.zip>
Writes assets/gungang/models/magic_book.obj/.mtl, textures/item/magic_book.png (+ magic_book_e.png, the emissive
layer, drawn as a second material) and models/item/magic_book.json. Node transforms are baked in; the book keeps its
Blockbench scale (1 unit = 1 block) and is centered on the block.
"""
import base64, io, json, sys, zipfile
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/gungang'
archive = zipfile.ZipFile(sys.argv[1])
scene = json.loads(archive.read('source/model.gltf'))

def data(uri):
    return base64.b64decode(uri.split(',', 1)[1])
blobs = [data(b['uri']) for b in scene['buffers']]

def accessor(index):
    a = scene['accessors'][index]; v = scene['bufferViews'][a['bufferView']]
    dtype = {5126: '<f4', 5125: '<u4', 5123: '<u2', 5121: 'u1'}[a['componentType']]
    width = {'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'SCALAR': 1}[a['type']]
    item = np.dtype(dtype).itemsize
    return np.ndarray((a['count'], width), dtype=dtype, buffer=blobs[v['buffer']],
                      offset=v.get('byteOffset', 0) + a.get('byteOffset', 0),
                      strides=(v.get('byteStride', item * width), item)).astype(float)

def matrix(node):
    m = np.eye(4)
    if 'matrix' in node: return np.array(node['matrix'], dtype=float).reshape(4, 4).T
    x, y, z, w = node.get('rotation', [0, 0, 0, 1])
    r = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                  [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    m[:3, :3] = r * np.array(node.get('scale', [1, 1, 1]))
    m[:3, 3] = node.get('translation', [0, 0, 0])
    return m

groups = {0: [], 1: []}  # material -> list of (positions, normals, uvs, faces)
def walk(index, parent):
    node = scene['nodes'][index]; world = parent @ matrix(node)
    if 'mesh' in node:
        for primitive in scene['meshes'][node['mesh']]['primitives']:
            assert primitive.get('mode', 4) == 4
            pos = accessor(primitive['attributes']['POSITION'])
            normal = accessor(primitive['attributes']['NORMAL'])
            uv = accessor(primitive['attributes']['TEXCOORD_0'])
            faces = accessor(primitive['indices']).astype(int).reshape((-1, 3))
            pos = (np.c_[pos, np.ones(len(pos))] @ world.T)[:, :3]
            normal = normal @ world[:3, :3].T
            normal /= np.maximum(np.linalg.norm(normal, axis=1, keepdims=True), 1e-9)
            groups[primitive.get('material', 0)].append((pos, normal, uv, faces))
    for child in node.get('children', []): walk(child, world)
for top in scene['scenes'][scene.get('scene', 0)]['nodes']: walk(top, np.eye(4))

every = np.vstack([p for parts in groups.values() for p, *_ in parts])
low = every.min(0); high = every.max(0); shift = np.array([.5, .5, .5]) - (low + high) / 2

lines = ['mtllib magic_book.mtl', 'o magic_book']
names = {0: 'book', 1: 'glow'}
base = 0
for material, parts in groups.items():
    if not parts: continue
    lines.append(f'usemtl {names[material]}')
    for pos, normal, uv, faces in parts:
        lines += ['v ' + ' '.join(f'{v:.7f}' for v in row + shift) for row in pos]
        lines += [f'vt {u:.7f} {1 - v:.7f}' for u, v in uv]
        lines += ['vn ' + ' '.join(f'{v:.7f}' for v in row) for row in normal]
        lines += ['f ' + ' '.join(f'{base + j + 1}/{base + j + 1}/{base + j + 1}' for j in row) for row in faces]
        base += len(pos)
(assets / 'models/magic_book.obj').write_text('\n'.join(lines) + '\n', encoding='utf-8')
(assets / 'models/magic_book.mtl').write_text('newmtl book\nKd 1 1 1\nmap_Kd gungang:item/magic_book\n'
                                             'newmtl glow\nKd 1 1 1\nmap_Kd gungang:item/magic_book_e\n', encoding='utf-8')
for image, name in zip(scene['images'], ['magic_book', 'magic_book_e']):
    Image.open(io.BytesIO(data(image['uri']))).convert('RGBA').save(assets / f'textures/item/{name}.png')

model = {'loader': 'forge:obj', 'model': 'gungang:models/magic_book.obj', 'automatic_culling': False,
         'flip_v': True, 'render_type': 'minecraft:cutout', 'textures': {'particle': 'gungang:item/magic_book'},
         # The book lies open in the model's XZ plane, pages up (+Y): a turn about X tilts the pages toward the viewer.
         'display': {'gui': {'rotation': [50, -30, 0], 'scale': [1.3] * 3},
                     'ground': {'scale': [.8] * 3, 'translation': [0, 2, 0]},
                     'fixed': {'rotation': [90, 0, 0], 'scale': [1.4] * 3},
                     'firstperson_righthand': {'rotation': [40, 0, 0], 'translation': [-2, 3, 0], 'scale': [1.0] * 3},
                     'firstperson_lefthand': {'rotation': [40, 0, 0], 'translation': [-2, 3, 0], 'scale': [1.0] * 3},
                     'thirdperson_righthand': {'rotation': [60, 0, 0], 'translation': [0, 3, 2], 'scale': [.8] * 3},
                     'thirdperson_lefthand': {'rotation': [60, 0, 0], 'translation': [0, 3, 2], 'scale': [.8] * 3}}}
(assets / 'models/item/magic_book.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
print(f'Converted {sum(len(p) for parts in groups.values() for p, *_ in parts)} vertices; bounds {low} .. {high}')
