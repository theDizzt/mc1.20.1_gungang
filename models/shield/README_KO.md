# 경찰 진압 방패 / Forge 1.20.1 / 손에 들었을 때 투명 관측창

## 이전 모델과 다른 점
1. 원래는 불투명한 큰 판 위에 유리 텍스처를 덧씌워 실제로는 뒤가 가려질 수 있었습니다.
2. 이번에는 **상단 관측창 영역의 불투명 본체를 실제로 제거**한 후, 얇은 반투명 유리 큐브를 넣었습니다.
3. 상단 창의 PNG 알파값은 65/255이며, Forge 모델은 `"render_type": "minecraft:translucent"` 입니다.
4. 투명 유리 너머로 월드/엔티티를 보도록 설계했습니다. 단, 그래픽 옵션/셰이더/모드 조합에 따라 렌더링 순서가 달라질 수 있어 **인게임 실행 확인은 필요**합니다.
5. 1인칭 양손과 3인칭, 방어 자세의 `display` 값을 각각 넣었습니다. **첫 테스트 후 플레이어 모델에 맞게 Blockbench Display 탭에서 조정**하세요.

## 파일
- `police_riot_shield.bbmodel` : Blockbench Generic Model용 편집 원본 (512×256 텍스처 내장)
- `police_riot_shield.glb` : 범용 3D 파일, 블렌딩되는 유리 재질 포함
- `src/main/resources/assets/combattable/models/item/police_riot_shield.json` : Forge 모델 (평시 + blocking predicate)
- `src/main/resources/assets/combattable/models/item/police_riot_shield_blocking.json` : 방어 자세 모델
- `src/main/resources/assets/combattable/textures/item/police_riot_shield.png` : RGBA 아틀라스
- `FORGE_BLOCKING_EXAMPLE.java.txt` : 커스텀 `ShieldItem` 속성 등록 예제
- `validation.json` : 생성 검증
- `police_riot_shield_preview.png` : 배경이 유리로 보이는 모델 설명 이미지 (실제 인게임 스크린샷 아님)

## 적용
`src/main/resources` 아래를 자신의 Forge 모드 프로젝트 동일 경로에 합칩니다. MOD ID가 `combattable`이 아니라면 폴더 네임스페이스 및 모든 JSON 참조를 교체하세요. 아이템 Registry 이름은 `police_riot_shield`입니다.

가장 중요한 점: 기본 바닐라 방패의 `parent: builtin/entity`를 사용하지 않습니다. Forge JSON에서 큐브를 직접 렌더링합니다. `ShieldItem` 등록 후 `combattable:blocking` 속성을 클라이언트에 등록하면 방어 시 별도 자세 모델을 선택합니다. 다른 방패 모드와는 통합 확인이 필요합니다.

## 한계
실제 Minecraft 1.20.1 Forge 클라이언트를 이 환경에서 구동할 수 없어 투명 렌더링과 방어 자세는 **게임 내 실기 검증 전**입니다. JSON/UV/알파 및 GLB 출력은 정적 검사만 수행했습니다. 일시적인 반투명 정렬 아티팩트가 발생하면 단일 렌더 타입/아틀라스 전략을 개선하거나 별도 렌더 패스가 필요할 수 있습니다.
