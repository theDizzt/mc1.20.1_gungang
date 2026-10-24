# GunGangRealistic — Minecraft 1.20.1

총게임용 음향·HUD와 팀 경기, 커스텀 아이템·블록을 제공하는 Forge 모드입니다.

## 문서 안내

사용 문서를 아래 7개 카테고리로 통합했습니다. 커스텀 아이템, 커스텀 블록, 명령어는 각각 독립 문서입니다.

| 문서 | 찾아볼 내용 |
| --- | --- |
| [서버·클라이언트 사용법](docs/SERVER.md) | 설치, 로비, [AI 봇](docs/BOTS.md), 장비 화면, 음향·HUD·폰트·키 설정 |
| [게임 모드와 경기 규칙](docs/GAMEPLAY.md) | 쟁탈전·데스매치·노르망디·폭탄전·호위·[숨바꼭질](docs/PROP-HUNT.md), 사망·부활, 전적·XP |
| [데이터 등록과 파일 형식](docs/DATA.md) | 폴더별 등록 안내, 맵·무기 JSON, PNG·TTF, 데이터팩 연결, 자동 저장 데이터 |
| [커스텀 아이템](docs/ITEMS.md) | 전체 아이템 ID, 사용법, 설치 조작, 효과와 기본값 |
| [커스텀 블록](docs/BLOCKS.md) | 기능 블록, 팀 출입구, 전장 기믹, 호위 지정 블록 |
| [명령어 모음](docs/COMMANDS.md) | 권한·콘솔 지원, HUD, 영역 편집·undo, modify, 전적 관리 |
| [개발·검증·변경 기록](docs/DEVELOPMENT.md) | 빌드, 호환성, 렌더링 메모, 날짜별 이력 |

## 빠른 시작

1. Java 17 / Minecraft 1.20.1 / Forge 환경에 설치합니다. 설치용 파일은 `dist/gungang-realistic_1.20.1_u<번호>_<날짜>.jar`입니다.
2. 음향·HUD만 사용하면 클라이언트 설치로 충분합니다. 경기·아이템·블록은 서버와 클라이언트에 같은 버전을 설치합니다.
3. 서버를 한 번 실행하고 [데이터 등록 순서](docs/DATA.md#workflow)에 따라 맵·무기를 등록합니다. 필요한 [JSON 예제](docs/server-examples/config/gungang/)만 복사해 실제 월드에 맞게 수정하세요.
4. `/gungang area list`로 맵 ID를 확인하고 `/gungang lobby open`으로 시작합니다. 관리자 편집 명령은 OP 2가 필요합니다.

Windows 빌드는 `export-jar.bat` 또는 `.\gradlew.bat exportJar --no-daemon`으로 실행합니다. 자세한 내용은 [빌드 안내](docs/DEVELOPMENT.md#build)를 참고하세요.

모델 원본과 제작 도구의 README는 해당 `models/` 폴더에 유지하며, 게임 운영·데이터 등록 안내는 위 카테고리 문서를 기준으로 관리합니다.
