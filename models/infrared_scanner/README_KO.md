# 96번 적외선 스캐너 모델

사용자 제공 `low-poly_anpeq_15.zip`의 glTF 모델을 `gungang:infrared_scanner`에 적용한다. 아이템 등록, 적 위치 공유, 지속 시간, 쿨타임 코드는 변경하지 않는다.

## 출처

This work is based on "low-poly AN/PEQ 15" (https://sketchfab.com/3d-models/low-poly-anpeq-15-522f6a04c34946dbb168022187645c1a) by D_U (https://sketchfab.com/DU1701) licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/).

원본 `license.txt`, `scene.gltf`, `scene.bin`을 이 폴더에 보관한다. 제작자 표기와 변경 사항은 배포 리소스의 `META-INF/licenses/infrared_scanner.txt`에도 포함된다.

## 변환

`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/import_infrared_scanner.ps1`

새 ZIP을 가져올 때는 `-Archive <ZIP 경로>`를 추가한다. 기존 전투 장비 리소스 생성 스크립트도 이 변환기를 호출한다.

- 원본 2개 메시, 8,629개 정점, 3,552개 삼각형을 보존한다. 원본의 양면 재질을 위해 역방향 면을 추가해 OBJ에는 총 7,104개 면이 있다.
- 서로 상쇄되는 원본 루트 변환을 검증한 후, 좌표축과 크기를 손에 드는 장비에 맞게 변환한다. 긴 축은 약 0.8블록이며 손 표시에서는 0.65배 배율을 사용한다.
- 원본에는 이미지 텍스처가 없다. 두 재질의 선형 기본 색상을 sRGB 단색 텍스처로 변환한다. Minecraft 기본 아이템 렌더러에서 지원하지 않는 PBR 금속성/거칠기는 적용하지 않는다.
- `src/main/resources/assets/gungang/models/item/infrared_scanner.json`의 표시 설정으로 인벤토리, 양손, 바닥 표시를 조정한다.

## 렌더링 검증

`gradlew.bat -PgungangAxeSmoke -PgungangScannerPreview runClient`

개발용 클라이언트에서 실제 Forge OBJ 베이킹 면 수와 텍스처 경로를 검사하고 `build/ui-smoke/infrared-scanner.png`에 표시 컨텍스트별 화면을 저장한 뒤 종료한다. 검증 코드는 배포 JAR에 포함되지 않는다.
