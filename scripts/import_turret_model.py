"""Convert the supplied "Turret" glTF (FeelsBadMan, CC-BY-4.0) to Forge OBJ: a static base and a rotating head.

Usage: python scripts/import_turret_model.py <turret.zip>
Pure Python; no dependencies. Model JSON files (render type, item display) are left as they are.
"""
import json
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/gungang'
SCALE = 1 / 800 / 2  # 1/800 block per source unit; model units are half blocks (TurretRenderer scales by 2 around 0.5)
HEAD_MIN_Y = 520     # parts starting above the bearing ring (yoke, gun, trunnion) turn with the target
CREDIT = ['# Based on "Turret" by FeelsBadMan, CC-BY-4.0',
          '# https://sketchfab.com/3d-models/turret-e0e03e8eed174fa082c9a890c2217596']


def convert(archive):
    with zipfile.ZipFile(archive) as z:
        g = json.loads(z.read('scene.gltf'))
        binary = z.read('scene.bin')
        sources = {name: z.read(name) for name in z.namelist() if not name.endswith('/')}

    def accessor(index):
        a = g['accessors'][index]
        v = g['bufferViews'][a['bufferView']]
        width = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[a['type']]
        fmt = '<' + ('I' if a['componentType'] == 5125 else 'f') * width
        stride = v.get('byteStride', 4 * width)
        offset = v.get('byteOffset', 0) + a.get('byteOffset', 0)
        return [struct.unpack_from(fmt, binary, offset + k * stride) for k in range(a['count'])]

    # Root node turns Z-up into Y-up; then a quarter turn about Y points the barrel (-X) at +Z, entity yaw 0.
    def transform(x, y, z):
        return (-y, z, -x)

    primitive = g['meshes'][0]['primitives'][0]
    positions = [transform(*p) for p in accessor(primitive['attributes']['POSITION'])]
    normals = [transform(*n) for n in accessor(primitive['attributes']['NORMAL'])]
    uvs = accessor(primitive['attributes']['TEXCOORD_0'])
    indices = [i[0] for i in accessor(primitive['indices'])]
    triangles = [indices[k:k + 3] for k in range(0, len(indices), 3)]

    # Parts are the connected pieces of the mesh; vertices split at UV seams are joined by position.
    parent = list(range(len(positions)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    seen = {}
    for i, p in enumerate(positions):
        key = tuple(round(c, 2) for c in p)
        if key in seen:
            parent[find(i)] = find(seen[key])
        else:
            seen[key] = i
    for t in triangles:
        for v in t[1:]:
            parent[find(v)] = find(t[0])
    parts = {}
    for t in triangles:
        parts.setdefault(find(t[0]), []).append(t)
    base, head = [], []
    for part in parts.values():
        (head if min(positions[v][1] for t in part for v in t) >= HEAD_MIN_Y else base).extend(part)

    models = ASSETS / 'models'

    def write(name, part):
        vertices = sorted({v for t in part for v in t})
        index = {v: i + 1 for i, v in enumerate(vertices)}
        lines = CREDIT + ['mtllib turret.mtl', 'usemtl turret', 'g turret']
        for v in vertices:
            x, y, z = positions[v]
            lines.append('v %.6f %.6f %.6f' % (.5 + x * SCALE, y * SCALE, .5 + z * SCALE))
        # glTF V runs downward like Minecraft textures; flip_v in the model JSON flips it back.
        lines += ['vt %.6f %.6f' % (uvs[v][0], 1 - uvs[v][1]) for v in vertices]
        lines += ['vn %.6f %.6f %.6f' % normals[v] for v in vertices]
        lines += ['f ' + ' '.join('%d/%d/%d' % ((index[v],) * 3) for v in t) for t in part]
        (models / (name + '.obj')).write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
        print(name, len(part), 'triangles')

    write('turret_base', base)
    write('turret_head', head)
    write('turret', base + head)
    (models / 'turret.mtl').write_text('newmtl turret\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd gungang:item/turret\n', encoding='utf-8', newline='\n')
    (ASSETS / 'textures/item/turret.png').write_bytes(sources['textures/initialShadingGroup_baseColor.png'])
    source = ROOT / 'models/turret'
    for name, data in sources.items():
        (source / name).parent.mkdir(parents=True, exist_ok=True)
        (source / name).write_bytes(data)
    (ROOT / 'src/main/resources/META-INF/licenses/turret.txt').write_bytes(sources['license.txt'])


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit('usage: python scripts/import_turret_model.py <turret.zip>')
    convert(sys.argv[1])
