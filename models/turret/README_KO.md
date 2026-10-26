# 터렛 모델

입력: 사용자 제공 `turret.zip` (glTF + BIN + 텍스처).

`python scripts/import_turret_model.py <zip 경로>`로 변환합니다. 추가 패키지는 필요 없습니다. 출력은 `src/main/resources/assets/gungang/`의 터렛 OBJ·MTL·텍스처이며, 원본 파일과 라이선스를 이 폴더와 `META-INF/licenses/turret.txt`에 복사합니다. 모델 JSON(렌더 타입·아이템 표시 자세)은 덮어쓰지 않습니다.

원본은 메시 하나이므로 연결된 부품 단위로 나눈 뒤, 회전 링 위에서 시작하는 요크·총몸·포신·차축은 회전부(`turret_head`), 바닥판·기둥·회전 링은 고정부(`turret_base`)로 분리합니다. 아이템 모델(`turret`)은 둘을 합친 것입니다. 삼각형은 고정부 164개, 회전부 618개, 합계 782개입니다.

포신이 엔티티 기본 방향(+Z)을 향하도록 Y축으로 90° 돌렸고, 회전축은 기둥 중심입니다. 원본 1단위를 1/800블록으로 축소해 포신 끝까지 2×2블록 안에 들어오며, 외형 높이는 약 1.48블록, 포신 중심 높이는 약 1.19블록입니다. 사격 시작점(`TurretEntity.muzzle`)도 이 높이에 맞춥니다. 기본 색상 텍스처만 사용하며 normal·metallic/roughness·emissive 맵은 원본 보관만 하고 적용하지 않습니다.

원저작자: FeelsBadMan. 출처와 CC BY 4.0 라이선스는 [license.txt](license.txt)에 있으며 배포 JAR에도 포함합니다. 형식 변환, 축 회전, 스케일 정규화, 부품 분리는 이 모드에서 수행한 변경입니다.

사용법과 설정은 [터렛 안내](../../docs/TURRET.md)를 참고하세요.
