"""Import the 경계 감시 포격 (73, gungang:perimeter_barrage) walkie-talkie model: glTF -> Forge OBJ item model.

Usage: python scripts/import_perimeter_barrage.py [walkie-_talkie.zip]   (requires Pillow)
With an archive, its scene.gltf / scene.bin / textures / license.txt are copied to models/perimeter_barrage first.
Writes models/item/perimeter_barrage.{obj,mtl,json}, textures/item/perimeter_barrage.png (base colour, 512 px) and
META-INF/licenses/perimeter_barrage.txt (CC-BY-4.0 credit).
"""
import json, struct, sys, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'models/perimeter_barrage'
ASSETS = ROOT / 'src/main/resources/assets/gungang'
NAMES = ['scene.gltf', 'scene.bin', 'license.txt', 'textures/Scene_-_Root_baseColor.png']
# Upright, standing on its base: the tallest side (antenna included) is this many blocks.
HEIGHT = .85

def extract(archive):
    with zipfile.ZipFile(archive) as z:
        for name in NAMES:
            target = SOURCE / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(z.read(name))

def mat_mul(a, b):
    # glTF matrices are column-major 16-element lists.
    return [sum(a[k * 4 + r] * b[c * 4 + k] for k in range(4)) for c in range(4) for r in range(4)]

def apply(m, v, w):
    return [m[0 * 4 + r] * v[0] + m[1 * 4 + r] * v[1] + m[2 * 4 + r] * v[2] + m[3 * 4 + r] * w for r in range(3)]

def accessor(g, data, index):
    a = g['accessors'][index]; view = g['bufferViews'][a['bufferView']]
    size = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[a['type']]
    fmt = {5126: 'f', 5125: 'I', 5123: 'H'}[a['componentType']]
    width = struct.calcsize(fmt)
    stride = view.get('byteStride', width * size)
    start = view.get('byteOffset', 0) + a.get('byteOffset', 0)
    return [struct.unpack_from('<%d%s' % (size, fmt), data, start + i * stride) for i in range(a['count'])]

def world_matrices(g):
    identity = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
    out = {}
    def walk(index, parent):
        node = g['nodes'][index]
        if any(k in node for k in ('translation', 'rotation', 'scale')): raise ValueError('TRS node transforms are not handled')
        m = mat_mul(parent, node.get('matrix', identity))
        if 'mesh' in node: out[index] = m
        for child in node.get('children', []): walk(child, m)
    for root in g['scenes'][g.get('scene', 0)]['nodes']: walk(root, identity)
    return out

def number(n): return ('%.6f' % n).rstrip('0').rstrip('.') or '0'

def main():
    if len(sys.argv) > 1: extract(sys.argv[1])
    g = json.loads((SOURCE / 'scene.gltf').read_text(encoding='utf-8'))
    data = (SOURCE / 'scene.bin').read_bytes()
    parts = []
    for index, m in world_matrices(g).items():
        for p in g['meshes'][g['nodes'][index]['mesh']]['primitives']:
            if p.get('mode', 4) != 4: raise ValueError('expected triangles')
            pos = [apply(m, v, 1) for v in accessor(g, data, p['attributes']['POSITION'])]
            nor = [apply(m, v, 0) for v in accessor(g, data, p['attributes']['NORMAL'])]
            uv = accessor(g, data, p['attributes']['TEXCOORD_0'])
            idx = [i[0] for i in accessor(g, data, p['indices'])]
            parts.append((pos, nor, uv, idx, g['materials'][p['material']].get('doubleSided', False)))
    every = [v for part in parts for v in part[0]]
    lo = [min(v[i] for v in every) for i in range(3)]; hi = [max(v[i] for v in every) for i in range(3)]
    # glTF is Y-up like Minecraft; keep the radio standing up and scale its height to HEIGHT, centred on the item.
    scale = HEIGHT / (hi[1] - lo[1])
    centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
    if max(hi[i] - lo[i] for i in (0, 2)) * scale > 1: raise ValueError('model wider than a block after scaling')
    lines, base, triangles = ['mtllib perimeter_barrage.mtl'], 1, 0
    for n, (pos, nor, uv, idx, double) in enumerate(parts):
        lines += ['g part_%d' % n, 'usemtl material_0']
        lines += ['v %s %s %s' % tuple(number(.5 + (v[i] - centre[i]) * scale) for i in range(3)) for v in pos]
        for u, v in uv:
            if not (0 <= u <= 1 and 0 <= v <= 1): raise ValueError('UV outside the texture')
            lines.append('vt %s %s' % (number(u), number(1 - v)))
        for v in nor:
            length = sum(c * c for c in v) ** .5 or 1
            lines.append('vn %s %s %s' % tuple(number(c / length) for c in v))
            lines.append('vn %s %s %s' % tuple(number(-c / length) for c in v))
        for i in range(0, len(idx), 3):
            tri = [base + idx[i + j] for j in range(3)]
            lines.append('f ' + ' '.join('%d/%d/%d' % (k, k, 2 * (k - 1) + 1) for k in tri)); triangles += 1
            if double:
                lines.append('f ' + ' '.join('%d/%d/%d' % (k, k, 2 * (k - 1) + 2) for k in reversed(tri))); triangles += 1
        base += len(pos)
    (ASSETS / 'models/item/perimeter_barrage.obj').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    (ASSETS / 'models/item/perimeter_barrage.mtl').write_text(
        'newmtl material_0\nKd 1 1 1\nd 1\nmap_Kd gungang:item/perimeter_barrage\n', encoding='utf-8')
    Image.open(SOURCE / 'textures/Scene_-_Root_baseColor.png').convert('RGBA').resize((512, 512), Image.LANCZOS).save(
        ASSETS / 'textures/item/perimeter_barrage.png')
    display = {
        'gui': {'rotation': [20, 135, 0], 'scale': [1.1, 1.1, 1.1]},
        'firstperson_righthand': {'rotation': [0, -20, 0], 'translation': [0, 1, -1], 'scale': [.65, .65, .65]},
        'firstperson_lefthand': {'rotation': [0, 20, 0], 'translation': [0, 1, -1], 'scale': [.65, .65, .65]},
        'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.6, .6, .6]},
        'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.6, .6, .6]},
        'ground': {'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
        'fixed': {'rotation': [0, 180, 0]},
    }
    model = {'loader': 'forge:obj', 'model': 'gungang:models/item/perimeter_barrage.obj', 'automatic_culling': False,
             'flip_v': False, 'render_type': 'minecraft:cutout', 'textures': {'particle': 'gungang:item/perimeter_barrage'},
             'display': display}
    (ASSETS / 'models/item/perimeter_barrage.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
    credit = (SOURCE / 'license.txt').read_text(encoding='utf-8') + (
        '\nModifications: converted glTF to Forge OBJ, scaled and centred for handheld use; base colour texture '
        'downscaled to 512 px; normal and metallic-roughness maps omitted.\n')
    licenses = ROOT / 'src/main/resources/META-INF/licenses'
    licenses.mkdir(parents=True, exist_ok=True)
    (licenses / 'perimeter_barrage.txt').write_text(credit, encoding='utf-8')
    print('Imported perimeter_barrage: %d vertices, %d triangles, %.3f x %.3f x %.3f blocks' % (
        base - 1, triangles, *((hi[i] - lo[i]) * scale for i in range(3))))

if __name__ == '__main__':
    main()
