"""Derive objective textures from installed vanilla Minecraft assets (no replacement art)."""
import io
import json
import colorsys
from pathlib import Path
from zipfile import ZipFile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/gungang'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def vanilla_archive():
    cache = Path.home()/'.gradle/caches/forge_gradle'
    candidates = list(cache.glob('minecraft_repo/versions/1.20.1/client.jar'))
    candidates += list(cache.glob('minecraft_user_repo/net/minecraftforge/forge/*1.20.1*/*recomp.jar'))
    for path in candidates:
        archive = ZipFile(path)
        if 'assets/minecraft/textures/block/tnt_side.png' in archive.namelist():
            return archive
        archive.close()
    raise RuntimeError('Minecraft 1.20.1 vanilla asset JAR not found; run Gradle setup first')

with vanilla_archive() as archive:
    for name in ['tnt_side', 'tnt_top', 'tnt_bottom', 'shears']:
        kind = 'item' if name == 'shears' else 'block'
        im = Image.open(io.BytesIO(archive.read(f'assets/minecraft/textures/{kind}/{name}.png'))).convert('RGBA')
        pixels = []
        for r,g,b,a in im.getdata():
            h,s,v = colorsys.rgb_to_hsv(r/255,g/255,b/255)
            if name == 'shears':
                # Preserve transparent pixels and shading, recolor the vanilla silhouette red.
                r,g,b = (round(c*255) for c in colorsys.hsv_to_rgb(0,.8,v))
            elif s > .25 and (h < .1 or h > .93):
                r,g,b = (round(c*255) for c in colorsys.hsv_to_rgb(.145,s,v))
            pixels.append((r,g,b,a))
        im.putdata(pixels)
        path = ASSETS/f'textures/{kind}/{"defuser" if name=="shears" else "bomb_"+name[4:]}.png'
        path.parent.mkdir(parents=True,exist_ok=True)
        im.save(path)

write(ASSETS/'models/block/planted_bomb.json', {'parent':'minecraft:block/cube_bottom_top','textures':{
    'side':'gungang:block/bomb_side','top':'gungang:block/bomb_top','bottom':'gungang:block/bomb_bottom'}})
write(ASSETS/'blockstates/planted_bomb.json', {'variants':{'':{'model':'gungang:block/planted_bomb'}}})
write(ASSETS/'models/item/bomb.json', {'parent':'gungang:block/planted_bomb'})
write(ASSETS/'models/item/defuser.json', {'parent':'minecraft:item/generated','textures':{'layer0':'gungang:item/defuser'}})
for language,names in [('ko_kr',('폭탄','폭탄 해체기','설치된 폭탄')),('en_us',('Bomb','Defuser','Planted Bomb'))]:
    path=ROOT/f'src/main/resources/assets/gungang_acoustics/lang/{language}.json'
    data=json.loads(path.read_text(encoding='utf-8'))
    for key,value in zip(['item.gungang.bomb','item.gungang.defuser','block.gungang.planted_bomb'],names):data[key]=value
    write(path,data)

rules={'rounds':5,'swapEveryRounds':1,'preparationSeconds':25,
       'attackSpawn':{'x':.5,'y':65,'z':.5,'yaw':0,'pitch':0},
       'attackArea':{'minX':-5,'minY':64,'minZ':-5,'maxX':5,'maxY':72,'maxZ':5},
       'defenseSpawn':{'x':40.5,'y':65,'z':.5,'yaw':180,'pitch':0},
       'defenseArea':{'minX':35,'minY':64,'minZ':-5,'maxX':45,'maxY':72,'maxZ':5},
       'pointA':{'x':20,'y':65,'z':-10},'pointB':{'x':20,'y':65,'z':10}}
arena={'id':'bomb','name':'폭탄전','description':'5라운드 · 3선승 · 매 라운드 공수 교대','enabled':False,
       'timeLimitSeconds':210,'gameMode':'adventure','dimension':'minecraft:overworld',
       'spectatorSpawn':{'x':20.5,'y':80,'z':.5,'yaw':0,'pitch':30},'bomb':rules,
       'onPrepare':[],'onStart':[],'onEnd':[],'weather':'clear','timeOfDay':'day','previewImage':'',
       'deathZones':[{'minX':-100,'minY':-64,'minZ':-100,'maxX':100,'maxY':0,'maxZ':100}]}
for directory in [ROOT/'docs/server-examples/config/gungang',ROOT/'server_resource/config/gungang']:
    write(directory/'server/maps/bomb.json',arena)
    for tool in [False,True]:
        name='defuser' if tool else 'bomb'
        weapon={'id':'99' if tool else '98','itemNbt':"{id:'gungang:"+name+"',Count:1b}",
                'name':'폭탄 해체기' if tool else '폭탄','description':'폭탄전에서 무작위 담당자에게만 지급됩니다.',
                'usage':'대상을 조준하고 우클릭을 유지합니다.','category':'폭탄전 전용','cooldownSeconds':0}
        weapon.update({'defuseSeconds':7} if tool else {'plantSeconds':4,'fuseSeconds':45})
        write(directory/f'server/sub_weapons/{weapon["id"]}_{name}.json',weapon)
print('Generated bomb/defuser textures, models, translations and config examples.')
