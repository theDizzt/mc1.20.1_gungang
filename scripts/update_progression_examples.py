"""Add server progression/deathmatch options without overwriting existing custom values."""
from pathlib import Path
import json,re,sys
root=Path(__file__).resolve().parents[1]
config=root/'server_resource/config/gungang'
path=config/'server/option.json'
data=json.loads(path.read_text(encoding='utf-8-sig'))
data.setdefault('deathmatch',dict(timeLimitSeconds=1200,winningKills=100))
data.setdefault('experience',dict(winBase=32,winContributionMax=24,lossBase=16,lossContributionMax=16,killWeight=1.0,assistWeight=0.5,damagePerPoint=100.0))
path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
source=(root/'src/main/java/dev/gungang/acoustics/server/XpTable.java').read_text(encoding='utf-8')
xp=[int(n) for n in re.search(r'LongStream.of\((.*?)\)\.boxed',source,re.S).group(1).split(',')]
assert len(xp)==91 and xp[0]==0 and xp[-1]==267909 and all(a<b for a,b in zip(xp,xp[1:]))
for filename,defaults in [('xp.json',dict(cumulativeXp=xp)),('player-db.json',dict(version=1,players=[]))]:
    path=config/filename
    if not path.exists() or (filename=='xp.json' and '--apply-xp' in sys.argv):path.write_text(json.dumps(defaults,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
