"""Update supplied server examples for the requested built-in equipment."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SERVER=ROOT/'server_resource/config/gungang/server'
for file in (SERVER/'weapons').glob('*.json'):
    value=json.loads(file.read_text(encoding='utf-8-sig'))
    value.setdefault('maxHealth',100)
    value.setdefault('movementSpeed',100)
    file.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
replacements={'86_landmine':('landmine','사용 후 우클릭 설치 / 좌클릭 취소. 자기 지뢰 조준 후 사용 키 5초 유지로 철거.'),
              '91_recovery-potion':('recovery_potion','사용 시 5초 동안 총 50 체력 회복.'),
              '95_adrenaline':('adrenaline','최대 체력의 50%를 소모하고 7초 동안 이동/공격 속도 +20%.'),
              '94_barrier':('barrier','사용 후 우클릭 설치 / 좌클릭 취소. 체력 200, 동시에 1개 설치 가능.')}
for file in (SERVER/'sub_weapons').glob('*.json'):
    value=json.loads(file.read_text(encoding='utf-8-sig'))
    value.setdefault('cooldownSeconds',0)
    if value['id'] in replacements:
        kind,usage=replacements[value['id']]
        value['itemNbt']="{id:'gungang:"+kind+"',Count:1b}"
        value['usage']=usage
        value['description']='세부 수치는 config/gungang/item/'+kind+'.json에서 수정합니다.'
    file.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
configs={'landmine':dict(damage=100,radius=3,triggerRadius=1.2,maxPlaced=3,disarmSeconds=5,range=5,showHealthBar=False),
         'recovery_potion':dict(heal=50,durationSeconds=5),
         'adrenaline':dict(healthCostPercent=50,durationSeconds=7,movementBonusPercent=20,attackBonusPercent=20,damageBonusPercent=20),
         'barrier':dict(health=200,range=5)}
for kind,value in configs.items():
    path=SERVER.parent/'item'/f'{kind}.json'
    path.parent.mkdir(parents=True,exist_ok=True)
    if not path.exists():path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for language in ['ko_kr','en_us']:
    path=ROOT/f'src/main/resources/assets/gungang_acoustics/lang/{language}.json'
    value=json.loads(path.read_text(encoding='utf-8'))
    names=['지뢰','회복약','전투자극제','방벽'] if language=='ko_kr' else ['Landmine','Recovery Potion','Adrenaline','Barrier']
    for kind,name in zip(configs,names):value[f'item.gungang.{kind}']=name
    value['entity.gungang_acoustics.deployable']='설치물' if language=='ko_kr' else 'Deployable'
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
