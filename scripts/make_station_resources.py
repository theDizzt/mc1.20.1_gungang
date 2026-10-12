"""Generate vanilla-textured station models and additive server config examples."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/gungang'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

names = {
    'war_table': ('전쟁 탁자', 'War Table', 'cartography_table_top'),
    'armory': ('무기고', 'Armory', 'smithing_table_top'),
    'alpha_team': ('ALPHA 팀 설정 블록', 'ALPHA Team Station', 'white_concrete'),
    'bravo_team': ('BRAVO 팀 설정 블록', 'BRAVO Team Station', 'white_concrete'),
    'small_health_pack': ('소형 생명력 팩', 'Small Health Pack', 'diamond_block'),
    'large_health_pack': ('대형 생명력 팩', 'Large Health Pack', 'emerald_block'),
}
for name, (ko, en, texture) in names.items():
    model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'minecraft:block/' + texture}}
    if name in ('war_table', 'armory'):
        base = 'cartography_table' if name == 'war_table' else 'smithing_table'
        model = {'parent': 'minecraft:block/cube', 'textures': {
            'particle': f'minecraft:block/{base}_side', 'up': f'minecraft:block/{base}_top',
            'down': 'minecraft:block/dark_oak_planks',
            **{side: f'minecraft:block/{base}_side' for side in ('north','south','east','west')}}}
        if name == 'war_table':
            for side in ('particle','north','south','east','west'):
                model['textures'][side] = 'minecraft:block/cartography_table_side1'
    if name in ('alpha_team','bravo_team'):
        model['elements'] = [{'from':[0,0,0], 'to':[16,16,16], 'faces': {
            face:{'texture':'#all', 'tintindex':0, 'cullface':face}
            for face in ('up','down','north','south','east','west')}}]
    write(ASSETS / f'models/block/{name}.json', model)
    variants = {'cooling=false': {'model':f'gungang:block/{name}'}, 'cooling=true': {'model':f'gungang:block/{name}'}}
    if 'health_pack' in name:
        glass = 'light_blue_stained_glass' if name.startswith('small') else 'lime_stained_glass'
        write(ASSETS / f'models/block/{name}_cooling.json', {'parent':'minecraft:block/cube_all',
            'render_type':'minecraft:translucent','textures':{'all':'minecraft:block/'+glass}})
        variants['cooling=true']['model'] += '_cooling'
        target = ROOT / f'server_resource/config/gungang/item/{name}.json'
        if not target.exists():
            write(target, {'heal':35 if name.startswith('small') else 70,'cooldownSeconds':10 if name.startswith('small') else 15})
    write(ASSETS / f'blockstates/{name}.json', {'variants':variants})
    write(ASSETS / f'models/item/{name}.json', {'parent':f'gungang:block/{name}'})
    write(ROOT / f'src/main/resources/data/gungang/loot_tables/blocks/{name}.json', {
        'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'gungang:{name}'}],
        'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for locale,index in [('ko_kr',0),('en_us',1)]:
    path = ASSETS / f'lang/{locale}.json'
    values = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
    values.update({f'block.gungang.{name}': labels[index] for name,labels in names.items()})
    write(path, values)

# Keep the supplied multi-block furniture when regenerating station resources.
from import_station_models import generate
generate()
