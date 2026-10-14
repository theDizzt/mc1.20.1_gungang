"""Recolor vanilla water animation frames into bright lime acid, preserving animation metadata."""
import io
from pathlib import Path
from zipfile import ZipFile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
cache = Path.home()/'.gradle/caches/forge_gradle'
candidates = list(cache.glob('minecraft_repo/versions/1.20.1/client.jar'))
candidates += list(cache.glob('minecraft_user_repo/net/minecraftforge/forge/*1.20.1*/*recomp.jar'))
for source in candidates:
    with ZipFile(source) as archive:
        if 'assets/minecraft/textures/block/water_still.png' not in archive.namelist():
            continue
        for suffix in ('still', 'flow'):
            original = f'assets/minecraft/textures/block/water_{suffix}.png'
            im = Image.open(io.BytesIO(archive.read(original))).convert('RGBA')
            # Preserve ripples while keeping even the darkest pixels vivid and green.
            im.putdata([(round(158*(.72+.28*max(r,g,b)/255)),
                         round(255*(.72+.28*max(r,g,b)/255)),
                         round(24*(.72+.28*max(r,g,b)/255)),
                         round(a*.88)) for r,g,b,a in im.getdata()])
            target = ROOT/f'src/main/resources/assets/gungang/textures/block/acid_{suffix}.png'
            target.parent.mkdir(parents=True, exist_ok=True)
            im.save(target)
            target.with_suffix('.png.mcmeta').write_bytes(archive.read(original+'.mcmeta'))
        print('Generated animated acid_still / acid_flow from', source)
        break
else:
    raise RuntimeError('Vanilla 1.20.1 assets not found; run Gradle setup first')
