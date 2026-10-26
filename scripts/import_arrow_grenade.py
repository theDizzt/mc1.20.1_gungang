"""Convert the supplied Athom glTF archive to Forge OBJ (requires numpy/Pillow)."""
import io, json, sys, zipfile
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/gungang'
with zipfile.ZipFile(sys.argv[1]) as archive:
    scene = json.loads(archive.read('scene.gltf'))
    blob = archive.read('scene.bin')
    def accessor(index):
        a = scene['accessors'][index]; v = scene['bufferViews'][a['bufferView']]
        dtype = {5126:'<f4',5125:'<u4',5123:'<u2',5121:'u1'}[a['componentType']]
        width = {'VEC2':2,'VEC3':3,'VEC4':4,'SCALAR':1}[a['type']]
        return np.ndarray((a['count'],width), dtype=dtype, buffer=blob,
                          offset=v.get('byteOffset',0)+a.get('byteOffset',0),
                          strides=(v.get('byteStride',np.dtype(dtype).itemsize*width),np.dtype(dtype).itemsize)).copy()
    parts = []
    def visit(index, parent):
        node = scene['nodes'][index]
        if 'matrix' in node:
            local = np.array(node['matrix']).reshape((4,4),order='F')
        else:
            local = np.eye(4)
            assert 'rotation' not in node, 'Add quaternion support before importing another model'
            local[:3,:3] = np.diag(node.get('scale',[1,1,1]))
            local[:3,3] = node.get('translation',[0,0,0])
        world = parent @ local
        if 'mesh' in node:
            for p in scene['meshes'][node['mesh']]['primitives']:
                assert p.get('mode',4)==4
                pos = accessor(p['attributes']['POSITION'])
                pos = (world @ np.column_stack((pos,np.ones(len(pos)))).T).T[:,:3]
                normal = accessor(p['attributes']['NORMAL']) @ np.linalg.inv(world[:3,:3])
                normal /= np.linalg.norm(normal,axis=1)[:,None]
                faces = accessor(p['indices']).reshape((-1,3))
                if np.linalg.det(world[:3,:3])<0: faces=faces[:,::-1]
                parts.append((pos,normal,accessor(p['attributes']['TEXCOORD_0']),faces,p['material']))
        for child in node.get('children',[]): visit(child,world)
    for index in scene['scenes'][scene.get('scene',0)]['nodes']: visit(index,np.eye(4))
    points=np.concatenate([p[0] for p in parts]); low=points.min(0); high=points.max(0)
    size=max(high-low); center=(low+high)/2
    lines=['mtllib arrow_grenade.mtl']; offset=1
    for i,(pos,normal,uv,faces,material) in enumerate(parts):
        pos=(pos-center)/size+.5
        lines += [f'o part_{i}',f'usemtl material_{material}']
        lines += ['v '+' '.join(f'{v:.7f}' for v in row) for row in pos]
        lines += [f'vt {u:.7f} {1-v:.7f}' for u,v in uv]
        lines += ['vn '+' '.join(f'{v:.7f}' for v in row) for row in normal]
        lines += ['f '+' '.join(f'{j+offset}/{j+offset}/{j+offset}' for j in row) for row in faces]
        offset+=len(pos)
    (assets/'models/arrow_grenade.obj').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    mtl=[]
    for i,m in enumerate(scene['materials']):
        ti=m['pbrMetallicRoughness']['baseColorTexture']['index']
        uri=scene['images'][scene['textures'][ti]['source']]['uri']
        image=Image.open(io.BytesIO(archive.read(uri))).convert('RGBA')
        image.thumbnail((2048,2048),Image.Resampling.LANCZOS)
        image.save(assets/f'textures/item/arrow_grenade_{i}.png')
        mtl += [f'newmtl material_{i}','Kd 1 1 1',f'map_Kd gungang:item/arrow_grenade_{i}']
    (assets/'models/arrow_grenade.mtl').write_text('\n'.join(mtl)+'\n',encoding='utf-8')
    license_path=root/'src/main/resources/META-INF/licenses/arrow_grenade.txt'
    license_path.parent.mkdir(parents=True,exist_ok=True)
    license_path.write_bytes(archive.read('license.txt')+b'\nModifications: node transforms baked; scale normalized; glTF converted to OBJ; base-color textures resized and converted to PNG; normal and metallic maps omitted.\n')
model={'loader':'forge:obj','model':'gungang:models/arrow_grenade.obj','automatic_culling':False,
       'flip_v':True,'render_type':'minecraft:cutout','textures':{'particle':'gungang:item/arrow_grenade_0'},
       'display':{'gui':{'rotation':[20,225,0],'scale':[.85]*3},'ground':{'scale':[.3]*3,'translation':[0,3,0]},
                  'firstperson_righthand':{'rotation':[0,135,0],'scale':[.5]*3},
                  'firstperson_lefthand':{'rotation':[0,225,0],'scale':[.5]*3},
                  'thirdperson_righthand':{'scale':[.4]*3},'thirdperson_lefthand':{'scale':[.4]*3}}}
(assets/'models/item/arrow_grenade.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
for language,name in [('ko_kr','화살분열 수류탄'),('en_us','Arrow Fragmentation Grenade')]:
    path=assets/f'lang/{language}.json'; data=json.loads(path.read_text(encoding='utf-8-sig'))
    data['item.gungang.arrow_grenade']=name
    data['death.attack.gungang.arrow_fragment']= '%1$s was struck by a grenade arrow' if language=='en_us' else '%1$s이(가) 수류탄 화살에 맞았습니다'
    data['death.attack.gungang.arrow_fragment.player']= '%1$s was struck by %2$s\'s grenade arrow' if language=='en_us' else '%1$s이(가) %2$s의 수류탄 화살에 맞았습니다'
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Converted {len(parts)} parts, {offset-1} vertices; original bounds {low} .. {high}')
