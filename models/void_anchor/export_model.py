from pathlib import Path
import bpy,json
from mathutils import Vector
root=Path(bpy.path.abspath('//')).parent.parent
out=root/'src/main/resources/assets/gungang/models/item';out.mkdir(parents=True,exist_ok=True)
tex=root/'src/main/resources/assets/gungang/textures/item';tex.mkdir(parents=True,exist_ok=True)
materials=[m for m in bpy.data.materials if m.use_nodes]
colors=[]
for m in materials:
 n=next((n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED'),None)
 colors.append(tuple(n.inputs['Base Color'].default_value) if n else (.8,.8,.8,1))
img=bpy.data.images.new('void_anchor_palette',width=16,height=16,alpha=True)
pixels=[]
for y in range(16):
 for x in range(16): pixels.extend(colors[min(x//2,len(colors)-1)])
img.pixels=pixels;img.filepath_raw=str(tex/'void_anchor.png');img.file_format='PNG';img.save()
scene=bpy.context.scene;deps=bpy.context.evaluated_depsgraph_get();meshes=[];points=[]
for o in scene.objects:
 if o.type not in {'MESH','CURVE'}:continue
 ev=o.evaluated_get(deps);m=bpy.data.meshes.new_from_object(ev);m.transform(o.matrix_world);m.calc_loop_triangles();meshes.append((o,m));points.extend([v.co.copy() for v in m.vertices])
lo=min(v.y for v in points);hi=max(v.y for v in points);scale=2/(hi-lo)
lines=['mtllib void_anchor.mtl','usemtl spear']
for i in range(len(colors)):lines.append(f'vt {(i*2+1)/16} 0.5')
offset=1;triangles=0
for o,m in meshes:
 lines.append('o '+o.name.replace('.','_'))
 for v in m.vertices:lines.append('v %.7f %.7f %.7f'%(v.co.x*scale+.5,(v.co.y-lo)*scale,v.co.z*scale+.5))
 for t in m.loop_triangles:
  mat=m.materials[t.material_index] if len(m.materials)>t.material_index else None
  idx=materials.index(mat) if mat in materials else 0
  lines.append('f '+' '.join(f'{v+offset}/{idx+1}' for v in t.vertices));triangles+=1
 offset+=len(m.vertices)
(out/'void_anchor.obj').write_text('\n'.join(lines)+'\n',encoding='utf8')
(out/'void_anchor.mtl').write_text('newmtl spear\nKd 1 1 1\nd 1\nmap_Kd gungang:item/void_anchor\n',encoding='utf8')
display={
 'gui':{'rotation':[0,0,-35],'translation':[0,-3,0],'scale':[.46,.46,.46]},
 'firstperson_righthand':{'rotation':[0,-90,15],'translation':[2,-2,-1],'scale':[.65,.65,.65]},
 'firstperson_lefthand':{'rotation':[0,90,-15],'translation':[2,-2,-1],'scale':[.65,.65,.65]},
 'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.8,.8,.8]},
 'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.8,.8,.8]},
 'ground':{'translation':[0,5,0],'scale':[.5,.5,.5]},
 'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}
(out/'void_anchor.json').write_text(json.dumps({'loader':'forge:obj','model':'gungang:models/item/void_anchor.obj','automatic_culling':False,'flip_v':False,'textures':{'particle':'gungang:item/void_anchor'},'display':display},indent=2)+'\n',encoding='utf8')
print('EXPORTED',offset-1,'vertices',triangles,'triangles','height=2',root)
