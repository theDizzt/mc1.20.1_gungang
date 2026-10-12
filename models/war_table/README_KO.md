# Tactical Combat Table | 마인크래프트 Forge 1.20.1

3×1×3 (48×16×48 모델 좌표)의 중세 전술 작전 테이블 모델입니다. 실제 파일을 제공하는 제작 초안이며, 콘셉트 이미지와 똑같은 메시를 복원한 것은 아닙니다.

## 가장 먼저 열어볼 파일

- `combat_table_3x1x3.bbmodel`: **Blockbench에서 File > Open Model로 열기**. 텍스처가 내장된 편집 가능한 전체 복셀 모델입니다.
- `combat_table_preview.png`: 실제로 만든 큐브의 아이소메트릭 미리보기.
- `combat_table_3d.glb`: 메시가 포함된 범용 3D 미리보기 파일(Blender 등에서 열 수 있음).
- `forge_resources/assets/combattable/models/block/*.json`: 3×3 위치별 분할된 Minecraft Java 블록 모델 9개.
- `forge_resources/assets/combattable/textures/block/combat_table_atlas.png`: 공용 512×512 텍스처 아틀라스.
- `FORGE_MODEL_NOTES.md`: Forge 프로젝트에 적용할 때의 분할 설명.

## 중요

이 파일들은 **커스텀 블록의 외형 에셋**입니다. Forge Java 코드, 블록 등록, 배치/철거 처리, 블록스테이트 설정, 충돌판정은 포함하지 않았습니다.

`Blockbench` 프로젝트는 `Generic Model` 형식이라서 전체 디자인을 자유롭게 수정할 수 있습니다. 게임에 적용하려면 9개 분할 JSON을 3x3 파트 블록의 모델로 참조하도록 등록해야 합니다.

Blockbench에 모델을 열 때 프로젝트 좌표의 `-24`부터 `+24`까지가 3×3 폭이고, 최대 높이는 16입니다. 파일을 수정한 뒤에는 Forge용 9조각 JSON을 다시 내보내거나 동일한 기준으로 분할해야 합니다. `build_model.py`는 모델 재생성 스크립트로 제공됩니다.

Designed elements: oak/dark oak surface, metal corner clasps, legs, decorative red pennants, illustrated terrain map, tactical team flags (red/blue), forts, mountains, lantern, scrolls, books, compass, ruler, quill, inkwell, pointer dagger, drawer.
