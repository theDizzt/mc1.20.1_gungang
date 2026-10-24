"""Apply the supplied map bounds and Italy bomb-site configuration; retain unrelated map settings."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FOLDERS=[ROOT/'server_resource/config/gungang/server/maps',ROOT/'docs/server-examples/config/gungang/server/maps']
BOUNDS={
    'ilios':(-103,32,-15,99,83,79),
    'lijang':(-229,2,-255,-147,45,-95),
    'normandy':(-110,1,162,129,60,407),
    'busan':(125,2,-112,285,75,157),
    'brazil-downrun':(196,14,262,427,49,436),
    'italy':(-230,2,-504,-24,117,-271),
}
def area(values):return dict(zip(('minX','minY','minZ','maxX','maxY','maxZ'),values))
def spawn(x,y,z,yaw):return dict(x=x,y=y,z=z,yaw=yaw,pitch=0)
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
italy=dict(id='06',name='이탈리아',description='폭탄전 / 팀 데스매치 · A 또는 B 영역 바닥에 폭탄 설치',enabled=True,timeLimitSeconds=210,
    gameMode='adventure',dimension='minecraft:overworld',spectatorSpawn=spawn(-127,80,-387,0),
    bomb=dict(rounds=5,swapEveryRounds=1,preparationSeconds=25,
        attackSpawn=spawn(-145.5,12,-286.5,180),attackArea=area((-164,11,-301,-128,36,-273)),
        defenseSpawn=spawn(-118.5,13,-486.5,0),defenseArea=area((-137,12,-501,-101,37,-473)),
        areaA=area((-203,6,-429,-177,37,-407)),areaB=area((-75,12,-423,-54,12,-400))),
    onPrepare=[],onStart=[],onEnd=[],deathZones=[],previewImage='',weather='clear',timeOfDay='day')
for folder in FOLDERS:
    for name,bounds in BOUNDS.items():
        path=folder/f'{name}.json'
        if path.exists():data=json.loads(path.read_text(encoding='utf-8-sig'))
        elif name=='italy':data=italy.copy()
        else:
            source=FOLDERS[0]/f'{name}.json'
            if not source.exists():continue
            data=json.loads(source.read_text(encoding='utf-8-sig'))
        data['previewArea']=area(bounds)
        write(path,data)
standalone=ROOT/'docs/server-examples/normandy.json'
data=json.loads(standalone.read_text(encoding='utf-8-sig'));data['previewArea']=area(BOUNDS['normandy']);write(standalone,data)
print('Updated six map preview regions and Italy (06) in server resources and examples.')
