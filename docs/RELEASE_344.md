# 344: 송풍기 / 미니건 설정

- 송풍기 지급 탄약 100발, TaCZ 탄창 용량 100발. 과열 데이터와 예열 발사 스크립트 제거, 달리기 후 지연과 예열 애니메이션 시간 0.
- 송풍기 탄환 넉백 0. 별도 `windStrength` 기본 1, 0~100 범위에서 0.01 단위로 편집. 적중 방향으로 `windStrength × 0.1` 블록/틱의 속도를 더함. 대상당 틱마다 한 번 적용하고 바닐라 넉백의 자동 상승 효과는 사용하지 않음.
- 송풍기 탄흔 파티클 생성 차단.
- 미니건 `script_param.shoot_delay` 기본 0.25초. 연사 시작 시 준비 시간을 적용하며 연속 발사 중 RPM 유지. 발사 간격의 2배(최소 150ms) 이상 쉬면 다시 준비.
- `/gungang modify ui 35`: 바람 세기, TaCZ 넉백 강도 및 탄창 용량.
- `/gungang modify ui 18`: 발사 전 지연 및 과열 한도/발열량/냉각 배수/냉각 지연/과열 잠금.

## 적용

`dist/gungang-realistic_1.20.1_u344_261110.jar`를 서버와 클라이언트에 설치합니다.
`dist/gungang-u344-resources.zip`의 `config`는 서버 루트, `tacz`는 서버와 클라이언트 게임 루트에 같은 경로로 덮어쓰고 재시작합니다.
작업 폴더의 `server_resource`에는 이미 리소스 변경이 반영되어 있습니다. 총기팩 파일들은 Git에서 제외되므로 별도 ZIP에 포함했습니다.

빌드: `gradlew.bat exportJar -PreleaseUpdate=344 -x test --console=plain` 성공. 요청에 따라 자동 테스트 및 게임 실행 테스트는 수행하지 않았습니다.
