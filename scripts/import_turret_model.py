"""Bake the supplied IRON-NEST glTF bind pose to Forge OBJ, preserving skinning and UVs.

Requires numpy. Run with the bundled Codex Python or any Python with numpy installed.
The static base and rotating head share a scale and yaw pivot; all rotations fit a 2-block footprint.
"""
import json
import sys
import zipfile
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/gungang'


def convert(archive):
    with zipfile.ZipFile(archive) as z:
        g = json.loads(z.read('scene.gltf'))
        binary = z.read('scene.bin')
        texture = z.read('textures/IRON-NEST_baseColor.png')
        license_text = z.read('license.txt')

    def accessor(index):
        a = g['accessors'][index]
        v = g['bufferViews'][a['bufferView']]
        dtype = np.dtype({5120:'i1',5121:'u1',5122:'<i2',5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']])
        width = {'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
        out = np.ndarray((a['count'],width), dtype=dtype, buffer=binary,
                         offset=v.get('byteOffset',0)+a.get('byteOffset',0),
                         strides=(v.get('byteStride',width*dtype.itemsize),dtype.itemsize)).copy()
        if a.get('normalized'):
            limit = np.iinfo(dtype)
            out = np.maximum(out.astype(float)/limit.max, -1)
        return out

    def matrix(node):
        if 'matrix' in node:
            return np.array(node['matrix']).reshape(4,4).T
        x,y,z,w = node.get('rotation',[0,0,0,1])
        out = np.eye(4)
        out[:3,:3] = np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
                              [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
                              [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]]) @ np.diag(node.get('scale',[1,1,1]))
        out[:3,3] = node.get('translation',[0,0,0])
        return out

    world = {}
    def walk(i,parent):
        world[i] = parent @ matrix(g['nodes'][i])
        for child in g['nodes'][i].get('children',[]): walk(child,world[i])
    for node in g['scenes'][g.get('scene',0)]['nodes']: walk(node,np.eye(4))
    pieces = []
    for i,node in enumerate(g['nodes']):
        if 'mesh' not in node: continue
        mesh = g['meshes'][node['mesh']]
        # The source cable mesh has an inconsistent bind space (100x coordinates)
        # and floats away from the body even after unit correction. Keep the supplied
        # body, six legs, turret and cannons; omit this disconnected decorative mesh.
        if mesh['name'] == 'Cables_IRON-NEST_0': continue
        for primitive in mesh['primitives']:
            assert primitive.get('mode',4) == 4
            a = primitive['attributes']
            positions, normals, uv = accessor(a['POSITION']),accessor(a['NORMAL']),accessor(a['TEXCOORD_0'])
            if 'skin' in node:
                skin = g['skins'][node['skin']]
                inverse = accessor(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
                joints = np.array([world[j] @ bind for j,bind in zip(skin['joints'],inverse)])
                weights = accessor(a['WEIGHTS_0']).astype(float)
                weights /= weights.sum(axis=1,keepdims=True)
                transforms = (joints[accessor(a['JOINTS_0']).astype(int)]*weights[:,:,None,None]).sum(axis=1)
            else:
                transforms = np.broadcast_to(world[i],(len(positions),4,4))
            positions = np.einsum('nij,nj->ni',transforms,np.column_stack((positions,np.ones(len(positions)))))[:,:3]
            normal_matrices = np.linalg.inv(transforms[:,:3,:3]).transpose(0,2,1)
            normals = np.einsum('nij,nj->ni',normal_matrices,normals)
            normals /= np.linalg.norm(normals,axis=1,keepdims=True)
            pieces.append(dict(name=mesh['name'],head=node['mesh'] in (2,4),p=positions,n=normals,uv=uv,faces=accessor(primitive['indices']).reshape(-1,3)))
    all_positions = np.concatenate([p['p'] for p in pieces])
    pivot = world[31][:3,3].copy()
    pivot[1] = all_positions[:,1].min()
    # Align the middle cannon's bind-pose forward direction with Minecraft +Z.
    forward = world[33][:3,3]-world[32][:3,3]
    angle = -np.arctan2(forward[0],forward[2])
    c,s = np.cos(angle),np.sin(angle)
    rotation = np.array([[c,0,s],[0,1,0],[-s,0,c]])
    radius = np.linalg.norm((all_positions-pivot)[:,[0,2]],axis=1).max()
    scale = min(.5/radius,1/(all_positions[:,1].max()-pivot[1]))
    for p in pieces:
        p['p'] = (p['p']-pivot) @ rotation.T * scale + [.5,0,.5]
        p['n'] = p['n'] @ rotation.T
    models = ASSETS/'models'
    models.mkdir(parents=True,exist_ok=True)
    def obj(name,selected):
        lines=['mtllib turret.mtl','usemtl turret'];base=1
        for p in selected:
            lines.append('g '+str(len(lines)))
            lines += ['v '+' '.join(f'{n:.7f}' for n in row) for row in p['p']]
            # glTF UV origin is top-left; OBJ texture coordinates are bottom-left.
            lines += [f'vt {u:.7f} {1-v:.7f}' for u,v in p['uv']]
            lines += ['vn '+' '.join(f'{n:.7f}' for n in row) for row in p['n']]
            lines += ['f '+' '.join(f'{i+base}/{i+base}/{i+base}' for i in row) for row in p['faces']]
            base += len(p['p'])
        (models/(name+'.obj')).write_text('\n'.join(lines)+'\n',encoding='utf-8')
    for name,selected in [('turret',pieces),('turret_base',[p for p in pieces if not p['head']]),('turret_head',[p for p in pieces if p['head']])]:
        obj(name,selected)
        model={'loader':'forge:obj','model':'gungang:models/'+name+'.obj','automatic_culling':False,'flip_v':True,
               'render_type':'minecraft:cutout','textures':{'particle':'gungang:item/turret'}}
        path = models/(name+'.json')
        if name == 'turret':
            path = models/'item/turret.json'
            model['display']={'gui':{'rotation':[25,225,0],'scale':[.8,.8,.8]},
                              'ground':{'scale':[.4,.4,.4],'translation':[0,3,0]},
                              'fixed':{'rotation':[0,180,0]},
                              'thirdperson_righthand':{'scale':[.4,.4,.4],'translation':[0,2,0]},
                              'thirdperson_lefthand':{'scale':[.4,.4,.4],'translation':[0,2,0]},
                              'firstperson_righthand':{'scale':[.5,.5,.5],'rotation':[0,135,0]},
                              'firstperson_lefthand':{'scale':[.5,.5,.5],'rotation':[0,225,0]}}
        path.write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
    (models/'turret.mtl').write_text('newmtl turret\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd gungang:item/turret\n',encoding='utf-8')
    (ASSETS/'textures/item/turret.png').write_bytes(texture)
    source = ROOT/'models/turret'
    source.mkdir(parents=True,exist_ok=True)
    (source/'license.txt').write_bytes(license_text)
    packaged_license = ROOT/'src/main/resources/META-INF/licenses/iron-nest.txt'
    packaged_license.parent.mkdir(parents=True,exist_ok=True)
    packaged_license.write_bytes(license_text)
    positions=np.concatenate([p['p'] for p in pieces])
    assert np.isfinite(positions).all() and positions.min()>=-1e-6 and positions.max()<=1+1e-6
    muzzle = (world[33][:3,3]-pivot) @ rotation.T * scale
    print('Imported',sum(len(p['faces']) for p in pieces),'triangles; normalized bounds',positions.min(axis=0),positions.max(axis=0),'muzzle',muzzle*2)
    # Lightweight geometry snapshot for a separate visual inspection, excluded from the mod.
    diagnostic = ROOT/'build/turret-model'
    diagnostic.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(diagnostic/'preview.npz',positions=positions,faces=np.concatenate([p['faces']+sum(len(q['p']) for q in pieces[:i]) for i,p in enumerate(pieces)]))


if __name__ == '__main__':
    convert(sys.argv[1])
