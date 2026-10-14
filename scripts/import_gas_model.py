"""Import the supplied 1.5-block gas drum, keeping the upper occupancy block invisible."""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/'models/gas/Forge_1.20.1/src/main/resources/assets/yourmod'
TARGET = ROOT/'src/main/resources/assets/gungang'

def import_model():
    data = json.loads((SOURCE/'models/block/red_gas_drum.json').read_text(encoding='utf-8-sig').replace('yourmod:', 'gungang:'))
    def write(relative, value):
        path = TARGET/relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    write('models/block/gas_barrel.json', data)
    write('models/block/gas_barrel_top.json', {'parent':'minecraft:block/block', 'textures':{'particle':'gungang:block/red_gas_drum'}, 'elements':[]})
    write('models/item/gas_barrel.json', {'parent':'gungang:block/gas_barrel'})
    shutil.copyfile(SOURCE/'textures/block/red_gas_drum.png', TARGET/'textures/block/red_gas_drum.png')
    print('Imported original gas drum geometry and texture:', len(data['elements']), 'elements')

if __name__ == '__main__':
    import_model()
