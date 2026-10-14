# Red Gas Drum | Blockbench + Forge 1.20.1

원통형 붉은 가스 드럼통 (16 × 24 × 16 모델 단위 = 1 × 1.5 × 1블록)

## 포함된 파일

- `Blockbench/red_gas_drum.bbmodel`: Blockbench에서 **File → Open Model**로 여는 편집 가능한 프로젝트. 텍스처 내장.
- `Blockbench/red_gas_drum_texture.png`: 원본 픽셀 텍스처 (128×128 RGBA).
- `Forge_1.20.1/src/main/resources/assets/yourmod/models/block/red_gas_drum.json`: 게임용 블록 모델.
- `Forge_1.20.1/src/main/resources/assets/yourmod/models/item/red_gas_drum.json`: 아이템 표현 모델.
- `Forge_1.20.1/src/main/resources/assets/yourmod/blockstates/red_gas_drum.json`: 기본 블록 상태 모델.
- `Forge_1.20.1/src/main/resources/assets/yourmod/textures/block/red_gas_drum.png`: 게임용 텍스처.

## 사용하는 방법

1. Blockbench에서 `.bbmodel`을 열어 크기와 색상, 밴드, 뚜껑을 확인하고 수정하세요.
2. Forge 1.20.1 프로젝트의 `src/main/resources/`에 `assets/yourmod/` 폴더 안의 내용을 복사하세요.
3. 폴더명 `yourmod`와 세 JSON 안의 `yourmod:`를 실제 모드 ID로 변경하세요.
4. 모드 Java 코드에서 `red_gas_drum` 블록과 BlockItem을 등록하세요. 이 패키지는 **블록 등록 Java 코드를 포함하지 않습니다**.
5. 위쪽 1/2블록도 물리적으로 충돌시키려면 블록 클래스에서 커스텀 VoxelShape(예: `Block.box(0,0,0,16,24,16)`)를 정의해야 합니다. JSON 모델만으로 충돌 판정은 변경되지 않습니다.

### 주의

- 모델/텍스처만으로는 위쪽 1/2칸에 다른 블록의 설치를 막지 않습니다. 상부 공간도 점유하는 2블록 구조가 필요하다면 별도 배치 로직 또는 상부 보조 블록을 구현해야 합니다.
- 가스 드럼통 모형은 장식용이며 폭발, 가스, 점화 효과는 포함되지 않습니다.
- 원통형 외형은 Java Block Model에서 지원하는 직육면체 조각으로 계단식 근사했습니다. 렌더링용 메시나 별도 라이브러리는 필요하지 않습니다.
