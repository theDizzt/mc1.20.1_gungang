# 87번 궤도폭격 아이템 모델

사용자 제공 `night_vision_binocular.zip`의 야간투시 쌍안경을 `gungang:orbital_strike`에 적용한다. 폭격 위치 지정, 발동, 피해량, 쿨타임 코드는 변경하지 않는다.

## 출처

This work is based on "Night vision binocular" (https://sketchfab.com/3d-models/night-vision-binocular-439c0cb2be044d45a5a60b36b89b825c) by Halil Kantarci (https://sketchfab.com/halilkantarci) licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/).

원본 glTF, 바이너리, 텍스처, 라이선스는 이 폴더에 보관한다. 제작자 표기와 변경 내용은 배포 JAR의 `META-INF/licenses/orbital_strike.txt`에도 포함한다.

## 재변환

`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/import_orbital_strike.ps1`

새 원본을 가져오려면 `-Archive <ZIP 경로>`를 추가한다.

- 5개 메시, 8,513개 정점, 원본 8,748개 삼각형을 보존한다. 양면 재질에 대응하는 역방향 면을 포함해 OBJ에는 17,496개 면이 있다.
- 원본 루트 행렬의 회전 상쇄와 균일 배율을 검증한다. 손에 드는 장비 크기에 맞춰 긴 축 약 0.884블록으로 변환하고 손 표시에는 0.65배 배율을 적용한다.
- 원본 diffuse PNG를 그대로 사용하며 체크섬으로 동일성을 확인한다. 원본 V 좌표가 모두 반복 타일 [1,2] 안에 있어 1을 빼서 Minecraft 아틀라스 범위로 이동한다.
- 원본의 투명도는 translucent 렌더링으로 유지한다. 기본 아이템 렌더러에서 지원하지 않는 normal/specular/gloss 맵은 원본 보관만 하고 적용하지 않는다.
- 표시 자세는 `src/main/resources/assets/gungang/models/item/orbital_strike.json`의 `display`에서 조정한다.

## 실제 렌더러 검증

`gradlew.bat -PgungangAxeSmoke -PgungangOrbitalPreview runClient`

실제 Forge 모델 베이킹 면 수와 텍스처 경로를 검사하고, `build/ui-smoke/orbital-strike.png`에 앞뒤·GUI·좌우 손·3인칭 모델 표시를 저장한다. 검증 코드는 배포 JAR에서 제외된다.
