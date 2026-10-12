"""Import the supplied Forge riot-shield assets without changing their geometry or alpha."""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'models/shield/src/main/resources/assets/combattable'
TARGET = ROOT / 'src/main/resources/assets/gungang'

def import_model():
    for suffix in ('', '_blocking'):
        source = SOURCE / f'models/item/police_riot_shield{suffix}.json'
        data = json.loads(source.read_text(encoding='utf-8-sig'))
        text = json.dumps(data, ensure_ascii=False, indent=2)
        text = text.replace('combattable:', 'gungang:').replace('gungang:item/police_riot_shield_blocking', 'gungang:item/shield_blocking')
        data = json.loads(text)
        # ItemInHandLayer rotates the hand -90 degrees around X. Counter it
        # before drawing this upright JSON model (the original was lying flat).
        for hand in ('righthand', 'lefthand'):
            data['display']['thirdperson_' + hand] = {
                'rotation': [90, 180, 0], 'translation': [-4, 2, -2], 'scale': [.92, .92, .92]}
            data['display']['firstperson_' + hand] = {
                'rotation': [0, 180, 0],
                'translation': [-5 if hand == 'righthand' else 5, -5, -7],
                'scale': [.82, .82, .82]}
        text = json.dumps(data, ensure_ascii=False, indent=2)
        assert data['render_type'] == 'minecraft:translucent'
        assert len(data['elements']) == 49
        for element in data['elements']:
            assert all(-16 <= v <= 32 for key in ('from', 'to') for v in element[key])
        path = TARGET / f'models/item/shield{suffix}.json'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text + '\n', encoding='utf-8')
    texture = TARGET / 'textures/item/police_riot_shield.png'
    texture.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SOURCE / 'textures/item/police_riot_shield.png', texture)
    print('Imported shield: 49 cubes, normal/blocking poses, original translucent texture.')

if __name__ == '__main__':
    import_model()
