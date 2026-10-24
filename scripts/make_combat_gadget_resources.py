"""Generate code-native item models and numbered secondary weapon examples."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/gungang'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def box(start,end,texture):
    return {'from':start,'to':end,'faces':{face:{'texture':'#'+texture} for face in ['north','south','east','west','up','down']}}

display={
    'gui':{'rotation':[15,25,0],'translation':[0,0,0],'scale':[.85,.85,.85]},
    'firstperson_righthand':{'rotation':[0,-20,0],'translation':[0,1,-2],'scale':[.65,.65,.65]},
    'firstperson_lefthand':{'rotation':[0,20,0],'translation':[0,1,-2],'scale':[.65,.65,.65]},
    'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,1,0],'scale':[.65,.65,.65]},
    'thirdperson_lefthand':{'rotation':[0,-90,0],'translation':[0,1,0],'scale':[.65,.65,.65]},
    'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]}}
# Reimport the supplied model so regeneration cannot restore the old placeholder.
import subprocess
subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File',
                str(ROOT/'scripts/import_infrared_scanner.ps1')],check=True)
from import_shield_model import import_model
import_model()

for locale,names in [('ko_kr',['적외선 스캐너','방패']),('en_us',['Infrared Scanner','Shield'])]:
    path=ASSETS/'lang'/f'{locale}.json'
    values=json.loads(path.read_text(encoding='utf-8-sig'))
    for kind,name in zip(['infrared_scanner','shield'],names):values['item.gungang.'+kind]=name
    write(path,values)

for ident,kind,name,cooldown,description,usage in [
    ('96_infrared-scanner','infrared_scanner','적외선 스캐너',120,'적 위치를 10초간 팀에 공유합니다.','사용 키로 스캔. 적 팀 색상 실루엣이 벽 너머로 표시됩니다.'),
    ('97_shield','shield','방패',90,'정면 피해 총 75 방어. 파손 후 90초 수리. 초과 피해는 체력에 적용됩니다.','주 손 또는 보조 손에 들면 방어합니다. 방패를 든 동안 무기 사용 불가.')]:
    write(ROOT/'server_resource/config/gungang/server/sub_weapons'/f'{ident}.json',{
        'id':ident,'itemNbt':"{id:'gungang:"+kind+"',Count:1b}",'name':name,'description':description,'usage':usage,
        'category':'지원' if cooldown==120 else '방어','cooldownSeconds':cooldown})
