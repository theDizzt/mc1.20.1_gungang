"""Generate code-owned gimmick models/textures and fill missing example settings."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/gungang'
COLORS=['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def texture(name,image):
    path=ASSETS/f'textures/block/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
def cube(start,end,tex='side'):
    return dict(from_=start,to=end,faces={face:{'texture':'#'+tex} for face in ['north','south','east','west','up','down']})
def element(start,end,tex='side'):
    c=cube(start,end,tex);c['from']=c.pop('from_');return c

cracks=Image.new('RGBA',(16,16));d=ImageDraw.Draw(cracks)
for points in [[(4,0),(6,4),(5,7),(8,10),(7,15)],[(6,4),(11,5),(15,3)],[(5,7),(1,10),(0,14)],[(8,10),(12,12),(15,11)]]:
    d.line(points,fill=(25,24,21,210),width=1)
texture('gimmick_cracks',cracks)
for name,color in [('barrel_red',(155,33,24,255)),('uxo_green',(83,87,48,255)),('nozzle_metal',(116,127,133,255))]:
    im=Image.new('RGBA',(16,16),color);d=ImageDraw.Draw(im)
    d.line((0,1,15,1),fill=(40,43,40,255));d.line((0,14,15,14),fill=(40,43,40,255))
    if name=='barrel_red':
        d.polygon([(8,3),(2,12),(14,12)],fill=(245,207,32),outline=(30,25,15));d.line((8,6,8,9),fill=(20,20,15),width=1);d.point((8,11),fill=(20,20,15))
    texture(name,im)

models={
 'uxo':dict(textures={'side':'gungang:block/uxo_green','particle':'gungang:block/uxo_green'},elements=[element([3,0,3],[13,1,13]),element([5,1,5],[11,2,11])]),
 'gas_barrel':dict(textures={'side':'gungang:block/barrel_red','particle':'gungang:block/barrel_red'},elements=[element([2,0,2],[14,16,14])]),
 'gas_barrel_top':dict(textures={'side':'gungang:block/barrel_red','particle':'gungang:block/barrel_red'},elements=[element([2,0,2],[14,8,14])]),
 'nozzle':dict(textures={'side':'gungang:block/nozzle_metal','particle':'gungang:block/nozzle_metal'},elements=[element([6,0,6],[10,16,10]),element([5,0,5],[11,2,11]),element([5,14,5],[11,16,11])]),
 'puddle':dict(render_type='minecraft:translucent',textures={'side':'minecraft:block/water_still','particle':'minecraft:block/water_still'},elements=[element([0,0,0],[16,1,16])])}
models['acid_puddle']=dict(render_type='minecraft:translucent',textures={'side':'gungang:block/acid_still','particle':'gungang:block/acid_still'},elements=[element([0,0,0],[16,1,16])])
for face in models['puddle']['elements'][0]['faces'].values():
    face['tintindex']=0
walls={'cracked_oak_planks':'oak_planks','cracked_glass':'glass','cracked_glass_pane':'glass',**{'cracked_'+c+'_concrete':c+'_concrete' for c in COLORS}}
for name,base in walls.items():
    boxes=[([0,0,0],[16,16,16])] if name!='cracked_glass_pane' else [([7,0,0],[9,16,16]),([0,0,7],[16,16,9])]
    elements=[]
    for lo,hi in boxes:
        elements.extend([element(lo,hi),element([v-.01 for v in lo],[v+.01 for v in hi],'crack')])
    models[name]=dict(render_type='minecraft:translucent' if 'glass' in name else 'minecraft:cutout',textures={'side':'minecraft:block/'+base,'crack':'gungang:block/gimmick_cracks','particle':'minecraft:block/'+base},elements=elements)
for name,model in models.items():
    model['parent']='minecraft:block/block';write(ASSETS/f'models/block/{name}.json',model)
    if name=='gas_barrel_top':continue
    if name in('puddle','acid_puddle'):state={'variants':{'':{'model':'gungang:block/'+name}}}
    elif name=='gas_barrel':state={'variants':{'active=true,top=false':{'model':'gungang:block/gas_barrel'},'active=true,top=true':{'model':'gungang:block/gas_barrel_top'},'active=false':{'model':'gungang:block/empty'}}}
    else:state={'variants':{'active=true':{'model':'gungang:block/'+name},'active=false':{'model':'gungang:block/empty'}}}
    write(ASSETS/f'blockstates/{name}.json',state)
    write(ASSETS/f'models/item/{name}.json',{'parent':'gungang:block/'+name})
    write(ROOT/f'src/main/resources/data/gungang/loot_tables/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'gungang:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
# The acid nozzle reuses the nozzle model; only its spray particles differ.
write(ASSETS/'blockstates/acid_nozzle.json',{'variants':{'active=true':{'model':'gungang:block/nozzle'},'active=false':{'model':'gungang:block/empty'}}})
write(ASSETS/'models/item/acid_nozzle.json',{'parent':'gungang:block/nozzle'})
write(ROOT/'src/main/resources/data/gungang/loot_tables/blocks/acid_nozzle.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'gungang:acid_nozzle'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(ASSETS/'models/block/empty.json',{'textures':{'particle':'minecraft:block/water_still'},'elements':[]})
write(ASSETS/'blockstates/acid.json',{'variants':{'':{'model':'gungang:block/empty'}}})
write(ASSETS/'models/item/acid_bucket.json',{'parent':'forge:item/bucket','loader':'forge:fluid_container','fluid':'gungang:acid'})
soundpath=ASSETS/'sounds.json';sounds=json.loads(soundpath.read_text()) if soundpath.exists() else {};sounds['puddle_step']={'sounds':[{'name':'minecraft:entity.generic.splash','type':'event'}]};write(soundpath,sounds)
write(ROOT/'src/main/resources/data/minecraft/tags/fluids/water.json',{'replace':False,'values':['gungang:acid','gungang:flowing_acid']})
names={'uxo':'불발 지뢰 (UXO)','gas_barrel':'가스통','nozzle':'노즐','acid_nozzle':'산성 노즐','puddle':'웅덩이','acid_puddle':'산성 웅덩이','acid':'산성 용액','cracked_oak_planks':'금이 간 나무판자','cracked_glass':'깨진 유리','cracked_glass_pane':'깨진 유리판'}
ko_colors=['하얀색','주황색','자홍색','하늘색','노란색','연두색','분홍색','회색','회백색','청록색','보라색','파란색','갈색','초록색','빨간색','검은색']
names.update({'cracked_'+c+'_concrete':k+' 금이 간 콘크리트' for c,k in zip(COLORS,ko_colors)})
for lang in ['ko_kr','en_us']:
    path=ROOT/f'src/main/resources/assets/gungang_acoustics/lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8'))
    for name,ko in names.items():data['block.gungang.'+name]=ko if lang=='ko_kr' else name.replace('_',' ').title()
    data['item.gungang.acid_bucket']='산성 액체가 든 양동이' if lang=='ko_kr' else 'Bucket of Acid';data['fluid_type.gungang.acid']='산성 용액' if lang=='ko_kr' else 'Acid';write(path,data)

configs={
 'molotov':dict(radius=5,durationSeconds=8,burnSeconds=3,cooldownSeconds=36,burnDamage=1),
 'orbital_strike':dict(radius=24,centerDamage=256,edgeDamage=84,delaySeconds=5,range=64,cooldownSeconds=240),
 'life_support':dict(absorption=30,durationSeconds=3,drainAmount=1,drainIntervalSeconds=.1,cooldownSeconds=120),
 'jetpack':dict(speed=.85,durationSeconds=3,collisionDamage=25,knockback=1.5,burnSeconds=3,trailSeconds=1,cooldownSeconds=24,verticalFlight=False,burnDamage=1),
 'infrared_scanner':dict(durationSeconds=10,cooldownSeconds=120),'shield':dict(capacity=75,repairSeconds=90,movementPenaltyPercent=15),
 'adrenaline':dict(healthCostPercent=50,durationSeconds=7,movementBonusPercent=20,attackBonusPercent=20,damageBonusPercent=20),
 'landmine':dict(damage=100,radius=3,triggerRadius=1.2,maxPlaced=3,disarmSeconds=5,range=5,showHealthBar=False),
 'barrier':dict(health=200,range=5),'recovery_potion':dict(heal=50,durationSeconds=5),
 'small_health_pack':dict(heal=35,cooldownSeconds=10),'large_health_pack':dict(heal=70,cooldownSeconds=15),
 'uxo':dict(damage=80,radius=5,respawnSeconds=30),'gas_barrel':dict(damage=80,radius=5,respawnSeconds=30),
 'puddle':dict(soundVolume=.7,soundIntervalTicks=6),'acid':dict(poisonSeconds=8,amplifier=0),'nozzle':dict(height=3,particlesPerSecond=40),
 'acid_nozzle':dict(height=3,particlesPerSecond=40,damage=2,damageIntervalTicks=10)}
configs.update({name:dict(health=40 if 'pane' in name else 60 if 'glass' in name else 100 if 'planks' in name else 200) for name in walls})
for folder in [ROOT/'server_resource/config/gungang',ROOT/'docs/server-examples/config/gungang']:
    for name,defaults in configs.items():
        path=folder/f'item/{name}.json';current=json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else {}
        for k,v in defaults.items():current.setdefault(k,v)
        write(path,current)
    for path in (folder/'server/maps').glob('*.json'):
        current=json.loads(path.read_text(encoding='utf-8-sig'));current.setdefault('weather','clear');current.setdefault('timeOfDay','day');write(path,current)
from import_gas_model import import_model as import_gas_model
import_gas_model()
print('Generated gimmick models, textures, translations and',len(configs),'item settings (existing values preserved).')
