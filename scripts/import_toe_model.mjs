// mecha-boost/source/model.gltf: shoe shell, trim and sole only (meshes 3,4,5).
// Mesh 2 is the bare leg; all body, arm and head meshes are deliberately excluded.
import fs from 'node:fs/promises';
const root=new URL('../',import.meta.url),assets=new URL('src/main/resources/assets/gungang/',root);
const scene=JSON.parse(await fs.readFile(new URL('models/toe/source.gltf',root),'utf8'));
const buffers=scene.buffers.map(b=>Buffer.from(b.uri.split(',')[1],'base64'));
function read(index){
    const a=scene.accessors[index],v=scene.bufferViews[a.bufferView];
    const width={SCALAR:1,VEC2:2,VEC3:3,VEC4:4}[a.type],size={5126:4,5125:4,5123:2,5121:1}[a.componentType];
    const method={5126:'readFloatLE',5125:'readUInt32LE',5123:'readUInt16LE',5121:'readUInt8'}[a.componentType];
    return Array.from({length:a.count},(_,r)=>Array.from({length:width},(_,c)=>buffers[v.buffer][method]((v.byteOffset||0)+(a.byteOffset||0)+r*(v.byteStride||width*size)+c*size)));
}
let lines=['mtllib toe.mtl','o mecha_boost_shoe','usemtl shoe'],base=0;
for(const mesh of [3,4,5])for(const p of scene.meshes[mesh].primitives){
    const positions=read(p.attributes.POSITION),uv=read(p.attributes.TEXCOORD_0),normals=read(p.attributes.NORMAL),indices=read(p.indices).flat();
    // Remove the original character pose; centre the 6-pixel-high boot at the item pivot.
    for(const v of positions)lines.push(`v ${v[0]+.5} ${v[1]+.625+.5} ${v[2]+.5}`);
    for(const v of uv)lines.push(`vt ${v[0]} ${v[1]}`);
    for(const v of normals)lines.push(`vn ${v.join(' ')}`);
    for(let i=0;i<indices.length;i+=3)lines.push('f '+indices.slice(i,i+3).map(n=>`${n+base+1}/${n+base+1}/${n+base+1}`).join(' '));
    base+=positions.length;
}
await fs.writeFile(new URL('models/toe.obj',assets),lines.join('\n')+'\n');
await fs.writeFile(new URL('models/toe.mtl',assets),'newmtl shoe\nKd 1 1 1\nd 1\nmap_Kd gungang:item/toe\n');
await fs.copyFile(new URL('models/toe/source.png',root),new URL('textures/item/toe.png',assets));
let display={gui:{rotation:[25,-35,0],scale:[2.2,2.2,2.2]},ground:{scale:[1,1,1]},fixed:{scale:[2,2,2]}};
for(const key of ['firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'])display[key]={scale:[0,0,0]};
await fs.writeFile(new URL('models/item/toe.json',assets),JSON.stringify({loader:'forge:obj',model:'gungang:models/toe.obj',automatic_culling:false,flip_v:true,render_type:'minecraft:cutout',textures:{particle:'gungang:item/toe'},display},null,2)+'\n');
export const summary={shoeMeshes:[3,4,5],vertices:base};
