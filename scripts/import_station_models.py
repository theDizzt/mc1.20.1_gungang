"""Import supplied Forge slices unchanged except namespace; generate blockstates, shapes and item miniatures."""
import copy
import json
import shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/gungang'

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def generate():
    for kind in ('war_table','armory'):
        source=ROOT/'models'/kind/'forge_resources/assets/combattable'
        if kind=='war_table':
            parts=[(f'combat_table_{z}_{x}',(xi-1,0,zi-1)) for zi,z in enumerate(('north','center','south')) for xi,x in enumerate(('west','center','east'))]
            atlas='combat_table_atlas'
        else:
            parts=[(f'modern_armory_y{y}_z{z}',(0,y,z)) for y in range(3) for z in range(2)]
            atlas='modern_armory_atlas'
        (ASSETS/'textures/block').mkdir(parents=True,exist_ok=True)
        shutil.copyfile(source/f'textures/block/{atlas}.png',ASSETS/f'textures/block/{atlas}.png')
        variants={}
        combined=[]
        shapes=[]
        for part,(name,offset) in enumerate(parts):
            model=json.loads((source/f'models/block/{name}.json').read_text(encoding='utf-8'))
            model['textures']={key:value.replace('combattable:','gungang:') for key,value in model['textures'].items()}
            model['render_type']='minecraft:cutout'
            write(ASSETS/f'models/block/{name}.json',model)
            for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
                variants[f'facing={facing},part={part}']={'model':f'gungang:block/{name}','y':(angle+(270 if kind=='armory' else 0))%360}
            # Smooth structural collision, avoiding dozens of tiny decorative obstacles on the tabletop.
            if kind=='war_table':
                core=[e for e in model['elements'] if e['name'].startswith(('tabletop','wide_plank','carved_corner_leg','leg_lower_foot','support_leg','lower_stretcher','apron'))]
                shapes.append([e['from']+e['to'] for e in core])
            else:
                shapes.append([[min(e['from'][i] for e in model['elements']) for i in range(3)]+[max(e['to'][i] for e in model['elements']) for i in range(3)]])
            for element in model['elements']:
                e=copy.deepcopy(element)
                assert 'rotation' not in e, 'Rotated elements require shape/miniature conversion'
                for endpoint in ('from','to'):
                    center=(8,8,8) if kind=='war_table' else (8,24,16)
                    e[endpoint]=[round((e[endpoint][i]+offset[i]*16-center[i])/3+8,6) for i in range(3)]
                for face in e['faces'].values():face.pop('cullface',None)
                combined.append(e)
        if kind=='armory':
            for part in range(6,9):
                for facing in ('north','east','south','west'):variants[f'facing={facing},part={part}']=variants[f'facing={facing},part=5']
        write(ASSETS/f'blockstates/{kind}.json',{'variants':variants})
        write(ROOT/f'src/main/resources/data/gungang/stations/{kind}.json',shapes)
        miniature={'parent':'minecraft:block/block','textures':{'particle':f'gungang:block/{atlas}','all':f'gungang:block/{atlas}'},
                   'render_type':'minecraft:cutout','elements':combined,'display':{
                       'gui':{'rotation':[25,135 if kind=='war_table' else 225,0],'translation':[0,0,0],'scale':[.65 if kind=='war_table' else .8]*3},
                       'ground':{'translation':[0,3,0],'scale':[.5,.5,.5]},
                       'fixed':{'rotation':[0,90 if kind=='armory' else 0,0],'scale':[1,1,1]}}}
        write(ASSETS/f'models/item/{kind}.json',miniature)
        # Keep the former single-block resource as a valid alias for resource packs.
        write(ASSETS/f'models/block/{kind}.json',{'parent':f'gungang:block/{parts[4 if kind=="war_table" else 0][0]}'})

if __name__=='__main__':generate()
