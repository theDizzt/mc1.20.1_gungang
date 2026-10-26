"""Convert FedeOde's supplied Rapier Sword glTF to a handheld Forge OBJ (no dependencies)."""
import json, struct, sys, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/gungang'
with zipfile.ZipFile(sys.argv[1]) as archive:
    g=json.loads(archive.read('scene.gltf')); data=archive.read('scene.bin')
    def read(i):
        a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];n={'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']]
        fmt='<'+{5126:'f',5125:'I',5123:'H'}[a['componentType']]*n
        start=v.get('byteOffset',0)+a.get('byteOffset',0);stride=v.get('byteStride',struct.calcsize(fmt))
        return [struct.unpack_from(fmt,data,start+k*stride) for k in range(a['count'])]
    p=g['meshes'][0]['primitives'][0];pos=read(p['attributes']['POSITION']);norm=read(p['attributes']['NORMAL']);uv=read(p['attributes']['TEXCOORD_0']);idx=read(p['indices'])
    # The mesh itself is straight along Z; discard the Sketchfab presentation transform.
    lo=min(v[2] for v in pos);hi=max(v[2] for v in pos)
    for k in range(9):
        part=[v for v in pos if lo+(hi-lo)*k/9<=v[2]<=lo+(hi-lo)*(k+1)/9]
        print('section',k,'width',round(max(v[0] for v in part)-min(v[0] for v in part),3) if part else 0)
    # Detect the hilt end by the wider guard; map the blade toward +Y, centre at (0.5,0.5,0.5).
    guard=max(pos,key=lambda v:abs(v[0]))[2];sign=1 if guard<(lo+hi)/2 else -1
    scale=1.5/(hi-lo);mid=(lo+hi)/2
    def normal(v):return (v[0],sign*v[2],-sign*v[1])
    lines=['# Rapier Sword by FedeOde, CC-BY-4.0; see META-INF/licenses/rapier.txt','mtllib rapier.mtl','usemtl rapier']
    for x,y,z in pos:lines.append('v %.7f %.7f %.7f'%(.5+x*scale,.5+sign*(z-mid)*scale,.5-sign*y*scale))
    lines+=['vt %.7f %.7f'%(u,1-v) for u,v in uv]
    lines+=['vn %.7f %.7f %.7f'%normal(v) for v in norm]
    for i in range(0,len(idx),3):lines.append('f '+' '.join('%d/%d/%d'%((idx[j][0]+1,)*3) for j in range(i,i+3)))
    model=ASSETS/'models/item';model.mkdir(parents=True,exist_ok=True)
    (model/'rapier.obj').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    (model/'rapier.mtl').write_text('newmtl rapier\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd gungang:item/rapier\n',encoding='utf-8')
    (ASSETS/'textures/item/rapier.png').write_bytes(archive.read('textures/Ropera_baseColor.png'))
    display={
        'gui':dict(rotation=[0,0,-40],translation=[0,0,0],scale=[.58]*3),
        'ground':dict(rotation=[0,0,90],translation=[0,3,0],scale=[.5]*3),
        'fixed':dict(rotation=[0,0,-40],scale=[.65]*3),
    }
    for hand in ['righthand','lefthand']:
        display['firstperson_'+hand]=dict(rotation=[0,0,0],translation=[0,9,0],scale=[1]*3)
        display['thirdperson_'+hand]=dict(rotation=[0,0,0],translation=[0,9,0],scale=[.9]*3)
    config=dict(loader='forge:obj',model='gungang:models/item/rapier.obj',automatic_culling=False,flip_v=True,render_type='minecraft:cutout',textures=dict(particle='gungang:item/rapier'),display=display)
    (model/'rapier.json').write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8')
    credit=archive.read('license.txt').decode()+'\nModifications: converted to OBJ, aligned blade along Y, scaled for Minecraft; base color retained, PBR maps omitted.\n'
    (ROOT/'src/main/resources/META-INF/licenses/rapier.txt').write_text(credit,encoding='utf-8')
    print('Converted',len(idx)//3,'triangles; blade direction',sign)
