"""Code-owned mineral water, carved bath toad, nozzle and nine-part geyser models."""
import json, math, random
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/gungang'
D=ROOT/'src/main/resources/data/gungang'
def write(p,value):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def texture(name,im):
    p=A/f'textures/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
for name,size in [('hot_spring_still',16),('hot_spring_flow',32)]:
    im=Image.new('RGBA',(size,size*16))
    for f in range(16):
        for y in range(size):
            for x in range(size):
                w=math.sin(x*.7+y*.3+f*math.tau/16)*6+math.sin(y*.8-f*math.tau/16)*5
                im.putpixel((x,y+size*f),(int(55+w),int(173+w),int(236+w),210))
    texture('block/'+name,im);write(A/f'textures/block/{name}.png.mcmeta',{'animation':{'frametime':3}})
for name,color in [('spring_stone',(172,169,143)),('toad_bronze',(104,132,106)),('spring_metal',(73,100,110)),('spring_brass',(193,162,81)),('spring_mouth',(24,43,45)),('spring_eye',(228,191,83))]:
    im=Image.new('RGBA',(16,16));rng=random.Random(23)
    for y in range(16):
        for x in range(16):
            shade=rng.randrange(-13,14);im.putpixel((x,y),tuple(max(0,min(255,c+shade)) for c in color)+(255,))
    if name=='spring_eye':ImageDraw.Draw(im).rectangle((6,2,9,13),fill=(12,25,21,255))
    texture('block/'+name,im)
def box(a,b,t):return {'from':a,'to':b,'faces':{f:{'texture':'#'+t} for f in ['up','down','north','south','east','west']}}
textures={k:'gungang:block/'+v for k,v in {'stone':'spring_stone','body':'toad_bronze','metal':'spring_metal','brass':'spring_brass','mouth':'spring_mouth','eye':'spring_eye','water':'hot_spring_still'}.items()}
def model(name,parts):
    for part in parts:
        if part['faces']['north']['texture']=='#eye':part['faces']['north']['uv']=[0,0,16,16]
    write(A/f'models/block/{name}.json',{'parent':'minecraft:block/block','textures':dict(textures,particle=textures['stone']),'elements':parts})
model('bath_toad',[
    box([1,0,2],[15,2,15],'stone'),box([3,2,4],[13,8,14],'body'),box([2,5,1],[14,11,9],'body'),
    box([1,2,8],[5,6,15],'body'),box([11,2,8],[15,6,15],'body'),box([2,1,1],[5,4,7],'body'),box([11,1,1],[14,4,7],'body'),
    box([2,10,2],[6,14,6],'body'),box([10,10,2],[14,14,6],'body'),box([2.5,10.5,1.8],[5.5,13,2],'eye'),box([10.5,10.5,1.8],[13.5,13,2],'eye'),
    box([4,5,0.7],[12,8.5,1],'brass'),box([5,5.5,0.5],[11,8,0.7],'mouth'),box([6,6,0.3],[10,7.4,0.5],'water'),
    box([5,8,10],[7,9,12],'body'),box([9,8,11],[11,9,13],'body')])
model('hot_spring_nozzle',[box([3,0,3],[13,2,13],'stone'),box([5,2,6],[11,11,12],'metal'),box([4,7,3],[12,12,10],'metal'),box([3,6,1],[13,13,3],'brass'),box([4,7,.8],[12,12,1],'mouth'),box([5,8,.6],[11,11,.8],'water')])
variants={}
for part in range(9):
    x,z=part%3,part//3
    parts=[box([0,0,0],[16,2,16],'stone')]
    if x==0:parts.append(box([0,2,0],[5,4,16],'stone'))
    if x==2:parts.append(box([11,2,0],[16,4,16],'stone'))
    if z==0:parts.append(box([0,2,0],[16,4,5],'stone'))
    if z==2:parts.append(box([0,2,11],[16,4,16],'stone'))
    parts.append(box([5 if x==0 else 0,2,5 if z==0 else 0],[11 if x==2 else 16,2.2,11 if z==2 else 16],'water'))
    if part==4:parts += [box([4,2,4],[12,3,12],'mouth'),box([6,3,6],[10,3.2,10],'water')]
    model('geyser_'+str(part),parts);variants['part='+str(part)]={'model':'gungang:block/geyser_'+str(part)}
write(A/'blockstates/geyser.json',{'variants':variants})
for name in ['bath_toad','hot_spring_nozzle']:
    write(A/f'blockstates/{name}.json',{'variants':{'facing='+face:{'model':'gungang:block/'+name,'y':angle} for face,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    write(A/f'models/item/{name}.json',{'parent':'gungang:block/'+name})
model('geyser_item',[box([0,0,0],[16,2,16],'stone'),box([0,2,0],[16,4,3],'stone'),box([0,2,13],[16,4,16],'stone'),box([0,2,3],[3,4,13],'stone'),box([13,2,3],[16,4,13],'stone'),box([3,2,3],[13,2.2,13],'water')])
write(A/'models/item/geyser.json',{'parent':'gungang:block/geyser_item'})
write(A/'blockstates/hot_spring_water.json',{'variants':{'':{'model':'gungang:block/empty'}}})
write(A/'models/item/hot_spring_bucket.json',{'parent':'forge:item/bucket','loader':'forge:fluid_container','fluid':'gungang:hot_spring_water'})
for name in ['bath_toad','hot_spring_nozzle','geyser']:
    conditions=[{'condition':'minecraft:survives_explosion'}]
    if name=='geyser':conditions.append({'condition':'minecraft:block_state_property','block':'gungang:geyser','properties':{'part':'4'}})
    write(D/f'loot_tables/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'gungang:'+name}],'conditions':conditions}]})
for lang,names in [('ko_kr',['온천수','온천수 양동이','온천수 노즐','간헐천','목욕탕 두꺼비','온천 치유']),('en_us',['Hot Spring Water','Hot Spring Water Bucket','Hot Spring Nozzle','Geyser','Bath Toad','Hot Spring Healing'])]:
    path=A/f'lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8-sig'))
    for key,value in zip(['block.gungang.hot_spring_water','item.gungang.hot_spring_bucket','block.gungang.hot_spring_nozzle','block.gungang.geyser','block.gungang.bath_toad','effect.gungang.hot_spring_healing'],names):data[key]=value
    write(path,data)
im=Image.new('RGBA',(18,18));draw=ImageDraw.Draw(im)
draw.ellipse((1,8,16,16),fill=(70,185,242,240));draw.line([(5,9),(4,6),(6,3)],fill=(231,249,255,255),width=2);draw.line([(11,8),(10,5),(12,1)],fill=(231,249,255,255),width=2)
texture('mob_effect/hot_spring_healing',im)
write(D/'recipes/hot_spring_bucket.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:water_bucket'},{'item':'minecraft:lapis_lazuli'},{'item':'minecraft:quartz'}],'result':{'item':'gungang:hot_spring_bucket'}})
for name,material in [('bath_toad','minecraft:mossy_cobblestone'),('hot_spring_nozzle','minecraft:iron_ingot'),('geyser','minecraft:calcite')]:
    write(D/f'recipes/{name}.json',{'type':'minecraft:crafting_shaped','pattern':['MMM','MWM','MMM'],'key':{'M':{'item':material},'W':{'item':'gungang:hot_spring_bucket'}},'result':{'item':'gungang:'+name}})
print('Generated hot spring water, nozzle, bath toad and geyser resources.')
