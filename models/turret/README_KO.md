# IRON-NEST 터렛 모델

입력: 사용자 제공 `iron-nest_ver_2.0.zip` (glTF + BIN + 텍스처).

`scripts/import_turret_model.py <zip 경로>`로 변환합니다. Python과 numpy가 필요합니다. 출력은 `src/main/resources/assets/gungang/`의 터렛 OBJ·MTL·모델 JSON·텍스처입니다.

본체와 다리는 고정하고 포탑과 포신만 회전합니다. 전체 회전 범위가 2×2블록 안에 들어오며 바닥은 Y=0입니다. 원본 비율을 유지하므로 실제 외형 높이는 약 0.8블록이며, 공간·충돌 판정은 요청된 2×2×2블록입니다. 케이블 장식 메시는 원본의 불일치하는 바인드 좌표 때문에 분리되어 보여 제외했습니다. 원본 보행·포신 애니메이션은 적용하지 않습니다.

원저작자: Maxy. 출처와 CC BY 4.0 라이선스는 [license.txt](license.txt)에 있으며 배포 JAR에도 포함합니다. 변환·스케일 정규화·부품 분리·케이블 제외는 이 모드에서 수행한 변경입니다.

사용법과 설정은 [터렛 안내](../../docs/TURRET.md)를 참고하세요.
