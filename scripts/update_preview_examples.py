"""Add opt-in map preview filenames without changing existing map values."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
for directory in [root/'server_resource/config/gungang/server/maps',root/'docs/server-examples/maps']:
    if not directory.exists():
        continue
    for path in directory.glob('*.json'):
        value=json.loads(path.read_text(encoding='utf-8-sig'))
        if 'previewImage' not in value:
            value['previewImage']=''
            path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    preview=directory/'preview'
    preview.mkdir(exist_ok=True)
    readme=preview/'README.txt'
    if not readme.exists():
        readme.write_text('PNG 이미지를 이 폴더에 넣고 맵 JSON에 "previewImage": "iljang.png"처럼 파일명을 지정하세요.\n최대 1 MB(1,000,000바이트) / 2048 x 2048 픽셀. 로비에서 /gungang reload 후 화면을 다시 여세요.\n',encoding='utf-8')
