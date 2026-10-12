"""Update built-in gadget resources and supplied configuration without changing other IDs."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
kinds=['landmine','recovery_potion','adrenaline','barrier']
for relative in ['src/main/java/dev/gungang/acoustics/server/Loadouts.java','scripts/update_gadget_examples.py','docs/ITEMS.md']:
    path=root/relative
    text=path.read_text(encoding='utf-8')
    # Preserve the compatibility parser's explicit legacy IDs.
    if relative.endswith('Loadouts.java'):
        before,after=text.split('    private static void ensureGadgets',1)
        text=before+'    private static void ensureGadgets'+after.replace('gungang_acoustics:', 'gungang:')
        text=text.replace('w.itemNbt().contains("gungang:"+weapon.id())','(w.itemNbt().contains("gungang:"+weapon.id())||w.itemNbt().contains("gungang_acoustics:"+weapon.id()))')
    else:
        text=text.replace('gungang_acoustics:', 'gungang:').replace('item.gungang_acoustics.', 'item.gungang.')
        if relative.endswith('.py'):text=text.replace('disarmSeconds=5,range=5)', 'disarmSeconds=5,range=5,showHealthBar=False)')
    path.write_text(text,encoding='utf-8')
for kind in kinds:
    source=root/f'src/main/resources/assets/gungang_acoustics/models/item/{kind}.json'
    target=root/f'src/main/resources/assets/gungang/models/item/{kind}.json'
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(source.read_text(encoding='utf-8'),encoding='utf-8')
for language in ['ko_kr','en_us']:
    path=root/f'src/main/resources/assets/gungang_acoustics/lang/{language}.json'
    value=json.loads(path.read_text(encoding='utf-8'))
    for kind in kinds:value[f'item.gungang.{kind}']=value.pop(f'item.gungang_acoustics.{kind}',value.get(f'item.gungang.{kind}',kind))
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for path in (root/'server_resource/config/gungang/server/sub_weapons').glob('*.json'):
    text=path.read_text(encoding='utf-8-sig')
    updated=text
    for kind in kinds:updated=updated.replace('gungang_acoustics:'+kind,'gungang:'+kind)
    if updated!=text:path.write_text(updated,encoding='utf-8')
path=root/'server_resource/config/gungang/item/landmine.json'
value=json.loads(path.read_text(encoding='utf-8-sig'));value.setdefault('showHealthBar',False)
path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
