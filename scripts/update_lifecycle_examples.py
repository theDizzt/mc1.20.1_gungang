"""Update supplied server JSONs without replacing existing commands or custom rules."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'server_resource/config/gungang'
for path in (root/'server/sub_weapons').glob('*.json'):
    data=json.loads(path.read_text(encoding='utf-8-sig'))
    if 'command' not in data:
        data['command']=''
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
path=root/'item/landmine.json'
data=json.loads(path.read_text(encoding='utf-8-sig'));data['maxPlaced']=min(3,data.get('maxPlaced',3))
path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
