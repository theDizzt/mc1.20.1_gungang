# models/glue_gun/scene.gltf (MAXDONE Mini Glue Gun, CC-BY-4.0, see models/glue_gun/license.txt) -> item model.
# Mesh BG_2_03_low1 is the gun; BG_1_02_low (the wire kickstand) is deliberately excluded.
# Item space: nozzle toward -Z (forward in first person), top +Y, the whole gun fitted to one block and centred on
# (0.5, 0.5, 0.5), which is the pivot of every display transform. Run: python scripts/import_glue_gun_model.py
import json, math, pathlib
import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'models/glue_gun'
ASSETS = ROOT / 'src/main/resources/assets/gungang'
GUN_MESH = 'BG_2_03_low1'
# The body (and the glue stick through it) leans 31 degrees up toward the back in the source scene.
TILT = math.radians(31)
LENGTH = 1.0  # blocks, nozzle tip to the back of the grip
TEXTURE = 512

g = json.loads((SOURCE / 'scene.gltf').read_text(encoding='utf-8'))
buf = (SOURCE / 'scene.bin').read_bytes()

def accessor(i):
    a = g['accessors'][i]; v = g['bufferViews'][a['bufferView']]
    width = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[a['type']]
    dtype = np.dtype({5126: '<f4', 5125: '<u4', 5123: '<u2', 5121: 'u1'}[a['componentType']])
    stride = v.get('byteStride') or width * dtype.itemsize
    start = v.get('byteOffset', 0) + a.get('byteOffset', 0)
    rows = [np.frombuffer(buf, dtype=dtype, count=width, offset=start + r * stride) for r in range(a['count'])]
    return np.array(rows)

def local(node):
    return np.array(node['matrix'], dtype=float).reshape(4, 4).T if 'matrix' in node else np.eye(4)

found = []
def walk(index, parent):
    node = g['nodes'][index]; matrix = parent @ local(node)
    if 'mesh' in node and node.get('name', '').startswith(GUN_MESH):
        for p in g['meshes'][node['mesh']]['primitives']: found.append((matrix, p))
    for child in node.get('children', []): walk(child, matrix)
for s in g['scenes'][g.get('scene', 0)]['nodes']: walk(s, np.eye(4))
assert len(found) == 1, found
matrix, primitive = found[0]

positions = accessor(primitive['attributes']['POSITION']).astype(float)
normals = accessor(primitive['attributes']['NORMAL']).astype(float)
uvs = accessor(primitive['attributes']['TEXCOORD_0']).astype(float)
indices = accessor(primitive['indices']).astype(int).flatten()

world = (matrix @ np.c_[positions, np.ones(len(positions))].T).T[:, :3]
normal_matrix = np.linalg.inv(matrix[:3, :3]).T
world_normals = (normal_matrix @ normals.T).T

# Level the body (rotate -TILT about Z), then turn it so the nozzle (scene -X) faces item -Z: (x, y, z) -> (-z, y, x).
c, s = math.cos(-TILT), math.sin(-TILT)
level = np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
turn = np.array([[0, 0, -1], [0, 1, 0], [1, 0, 0]])
rotation = turn @ level
points = (rotation @ world.T).T
point_normals = (rotation @ world_normals.T).T
point_normals /= np.linalg.norm(point_normals, axis=1, keepdims=True).clip(1e-9)

low, high = points.min(0), points.max(0)
scale = LENGTH / (high - low).max()
points = (points - (low + high) / 2) * scale + .5

lines = ['mtllib glue_gun.mtl', 'o glue_gun', 'usemtl glue_gun']
lines += [f'v {x:.5f} {y:.5f} {z:.5f}' for x, y, z in points]
lines += [f'vt {u:.5f} {v:.5f}' for u, v in uvs]
lines += [f'vn {x:.4f} {y:.4f} {z:.4f}' for x, y, z in point_normals]
for i in range(0, len(indices), 3):
    lines.append('f ' + ' '.join(f'{n + 1}/{n + 1}/{n + 1}' for n in indices[i:i + 3]))
(ASSETS / 'models/glue_gun.obj').write_text('\n'.join(lines) + '\n', encoding='utf-8')
(ASSETS / 'models/glue_gun.mtl').write_text('newmtl glue_gun\nKd 1 1 1\nd 1\nmap_Kd gungang:item/glue_gun\n', encoding='utf-8')
Image.open(SOURCE / 'source.png').convert('RGB').resize((TEXTURE, TEXTURE), Image.LANCZOS).save(ASSETS / 'textures/item/glue_gun.png', optimize=True)

display = {
    # Side view, nozzle to the right.
    'gui': {'rotation': [0, -90, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
    'fixed': {'rotation': [0, -90, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    # First person: the view looks down -Z, so the nozzle points ahead; a slight turn toward the crosshair.
    'firstperson_righthand': {'rotation': [0, 4, 0], 'translation': [0, 1, -2], 'scale': [.75, .75, .75]},
    'firstperson_lefthand': {'rotation': [0, 4, 0], 'translation': [0, 1, -2], 'scale': [.75, .75, .75]},
    # Third person: the hand layer maps item +Y to the body's forward, so +90 about X levels the nozzle forward.
    'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 2, -1], 'scale': [.6, .6, .6]},
    'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 2, -1], 'scale': [.6, .6, .6]},
}
model = {'loader': 'forge:obj', 'model': 'gungang:models/glue_gun.obj', 'automatic_culling': False, 'flip_v': True,
         'render_type': 'minecraft:cutout', 'textures': {'particle': 'gungang:item/glue_gun'}, 'display': display}
(ASSETS / 'models/item/glue_gun.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
print('vertices', len(points), 'triangles', len(indices) // 3, 'size (blocks)', ((high - low) * scale).round(3))
