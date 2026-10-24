# 명령어 모음

[문서 안내](../README.md) · [데이터 등록](DATA.md) · [아이템](ITEMS.md) · [블록](BLOCKS.md) · [명령어](#)

일반 플레이어·관리자·콘솔 명령, 공간마법사와 설정 편집까지 이 문서에서 확인합니다.

- [전체 명령어와 권한](#reference)
- [AI 봇 설정과 켜기·끄기](BOTS.md)
- [전적·XP·레벨 조회와 수정](#progression)
- [공간마법사: 영역 선택·등록·되돌리기](#area)
- [modify: 맵·무기·아이템·블록 설정 편집](#modify)

<a id="reference"></a>

## 전체 명령어와 권한

`/gungang cleardeployables`(OP 2)는 전체 지뢰·방벽과 컴파운드 보우 화살을 제거합니다. 미로드 청크의 화살도 다음 로드 시 제거됩니다. 경기 중 팀 참가자는 무기고 3블록 이내에서도 무기 선택을 사용할 수 있습니다. 일반 맵의 준비 시간은 30초이고, [노르망디](GAMEPLAY.md#normandy)는 45초이며 ALPHA는 자유 이동합니다.

현재 코드에 등록된 서버·클라이언트 명령어 목록입니다. 설정 파일의 전체 형식은 [데이터 등록 설명서](DATA.md)를 참고하세요.

팀데스매치 봇은 `/gungang bot on`, `/gungang bot off`, `/gungang bot status`, `/gungang bot preset low`로 관리합니다(OP 2, 콘솔 가능). 기본 OFF이며 최대 4명입니다. `on`은 사용을 허용하며 실제 참가는 로비의 `봇 추가` 버튼으로 한 명씩 지정합니다. 봇별 이름·스킨은 `/gungang bot name <번호> <이름>`, `/gungang bot skin <번호> <텍스처>`로 바꾸고, 실력은 `/gungang bot difficulty easy|normal|hard|expert`로 조절합니다. `off`는 봇과 참가 예약을 제거하고 로비 추가 버튼을 잠급니다. [처음 실행 순서](BOTS.md#quick-start), [전체 명령어](BOTS.md#commands), [봇 닉네임·스킨·전적](BOTS.md#profile), [문제 해결](BOTS.md#troubleshooting)을 참고하세요.

### 사용 조건

- `/gungang ...`은 서버에 모드가 설치되어 있어야 합니다. 로비·무기 선택 화면은 클라이언트에도 모드가 있어야 보입니다.
- **OP 2**는 Minecraft 권한 레벨 2 이상입니다. 일반 명령어는 OP 권한이 필요 없습니다.
- 아래 표의 **콘솔 가능** 명령어는 서버 콘솔에서도 실행할 수 있습니다. 콘솔에서는 앞의 `/`를 생략합니다. 플레이어 전용 명령어는 게임 안에서 입력합니다.
- `<id>`는 실제 값으로 바꿔 입력하며 `<`, `>`는 입력하지 않습니다. `/gungang`만 입력하는 도움말 명령은 현재 없습니다. 하위 명령은 TAB 자동 완성으로 확인할 수 있습니다.

### HUD와 공간마법사

| 명령어 | 권한 | 기능 |
| --- | --- | --- |
| `/gungang hud on` / `/gungang hud off` | 일반, 플레이어 전용 | 본인의 총게임 HUD 표시/숨김. 접속·사망 후에도 유지됩니다. |
| `/gungang hud` | 일반, 플레이어 전용 | 현재 HUD 설정 확인 |
| `/gungang area wand` | OP 2 | 공간마법사 지급. 좌클릭/우클릭으로 두 모서리 선택 |
| `/gungang area list [페이지]` | OP 2, 콘솔 가능 | 비활성 맵 포함 ID·이름·활성 상태·파일명 조회. 페이지당 10개 |
| `/gungang area pos1 [x y z]` / `pos2 [x y z]` | OP 2 | 현재 위치 또는 좌표로 선택 |
| `/gungang area save <맵ID> <종류> [번호]` | OP 2 | 선택 영역을 맵 JSON에 저장·즉시 반영 |
| `/gungang area undo` | OP 2 | 본인의 마지막 저장 취소(최대 20회, 재시작 후 유지) |
| `/gungang area info` / `clear` | OP 2 | 선택 확인 / 초기화 |
| `/gungang area enable <맵ID> true/false` | OP 2 | 편집한 맵 활성화/비활성화 |

HUD off는 체력·탄약·팀·나침반·거점·호위·경기 시간·점수판·부활 안내 등 GunGang 화면 HUD를 숨깁니다. 기본 Minecraft HUD가 다시 표시됩니다. 설정의 `shooterHud`가 꺼져 있으면 on으로 해도 해당 설정은 유지됩니다. 메뉴·결과 화면·월드의 거점 신호기·장비 설치 미리보기는 유지됩니다. 서버와 클라이언트 모두 새 버전을 설치해야 합니다.

영역 종류와 사용 예시는 [공간마법사 설명서](#area)를 참고하세요. area 편집 명령은 플레이어 전용이며 준비/경기 중에는 저장할 수 없습니다. list는 콘솔과 경기 중에도 사용할 수 있습니다.

### 맵·무기·아이템·블록 설정 수정

`/gungang modify <map|weapon|secondary|item|block> <ID> <항목> <값>` (OP 2, 콘솔 가능)

종류까지만 입력하면 ID 목록, ID까지 입력하면 항목 목록, 항목까지 입력하면 현재값을 보여줍니다. TAB 자동완성을 지원합니다. 수정은 로비에서 가능하며 저장·설정 다시 읽기를 자동 수행합니다. 예: `/gungang modify map arena01 name 황금 광산`, `/gungang modify item molotov cooldownSeconds 40`.

표시용 무기 스탯과 실제 아이템 효과의 차이, 전체 예시와 적용 시점은 [설정 편집 설명서](#modify)를 참고하세요.

### 로비와 경기 관리 명령 목록

| 명령어 | 권한 | 실행 위치 | 기능·조건 |
| --- | --- | --- | --- |
| `/gungang escorttrack` | OP 2 | 콘솔 가능 | 로비에서 선택한 호위 맵의 경로를 검증하고 선로를 다시 생성합니다. |
| `/gungang cleardeployables` | OP 2 | 콘솔 가능 | 전체 지뢰·방벽·컴파운드 보우 화살을 제거합니다. 전장 기믹 복구는 reset 또는 경기 수명주기를 따릅니다. |
| `/gungang lobby open` | OP 2 | 콘솔 가능 | 서버에 하나의 로비를 열고 접속자에게 UI를 표시합니다. 이미 열려 있으면 기존 로비를 다시 표시하고 팀·준비 상태를 유지합니다. 준비·경기 중에는 새로 열 수 없습니다. |
| `/gungang lobby close` | OP 2 | 콘솔 가능 | 로비를 닫습니다. 진행 중 경기는 중단 전적으로 저장하고 원래 위치·게임 모드를 복원하며 팀은 해제합니다. |
| `/gungang lobby` | 일반 | 플레이어 전용 | 이미 열린 로비의 UI를 본인에게 다시 표시합니다. 준비·경기 진행 중에는 로비 UI가 열리지 않습니다. |
| `/gungang map <id>` | OP 2 | 콘솔 가능 | 열린 로비에서 활성 맵을 선택합니다. 준비·경기 중에는 변경할 수 없습니다. 맵 변경 시 전원의 준비 상태가 해제됩니다. |
| `/gungang random` | OP 2 | 콘솔 가능 | 열린 로비에서 미배정자와 팀 참가자를 ALPHA·BRAVO에 균등하게 무작위 배치합니다. 명시적으로 선택한 관전자는 제외합니다. 준비 상태가 해제됩니다. |
| `/gungang forcestart` | OP 2 | 콘솔 가능 | 열린 로비에서 준비 상태와 관계없이 현재 팀 배정대로 경기를 시작합니다. 맵이 선택돼 있고 ALPHA·BRAVO 참가자가 1명 이상 있어야 합니다. 미배정자는 관전자로 시작합니다. |
| `/gungang cooldown reset [대상]` | OP 2 | 콘솔 가능(대상 필수) | 보조무기·장비(스캐너 포함)의 쿨타임과 아이템 쿨타임을 초기화합니다. 대상을 생략하면 본인, `@a`·플레이어 이름 등 선택자로 여러 명을 지정할 수 있습니다. 경기 준비·진행 중에는 사용할 수 없습니다. |
| `/gungang stop` | OP 2 | 콘솔 가능 | 현재 준비·경기를 승리 팀 없이 중단하고 전적을 저장합니다. 로비는 유지되며 준비 상태가 해제됩니다. 실행 중인 경기가 없으면 아무 작업도 하지 않습니다. |
| `/gungang reload` | OP 2 | 콘솔 가능 | 서버 음향·맵·무기 설정을 다시 읽고 서버 음향 설정을 접속자에게 전송합니다. 준비·경기 중에는 거부합니다. 설정 오류가 있으면 마지막 정상 설정을 유지합니다. 성공 시 준비 상태가 해제됩니다. 저장된 맵 지도(`server/maps/data`)는 유지합니다. |
| `/gungang reload map_data [id]` | OP 2 | 콘솔 가능 | 저장된 좌표 기반 지도(`config/gungang/server/maps/data/<id>.png`)를 삭제해 다음 미리보기 때 다시 생성합니다. ID를 생략하면 전체를 삭제합니다. 경기 중에도 사용할 수 있습니다. |

맵 ID는 `config/gungang/server/maps/*.json` 안의 `id` 값입니다. `00`은 랜덤 맵·게임모드 선택으로 예약되어 있습니다(같은 ID의 실제 맵이 있으면 그 맵이 우선). 파일명이나 표시 이름이 아닙니다. `enabled: true`인 맵만 선택할 수 있으며 TAB 자동 완성에 표시됩니다.

서버에는 로비·경기가 하나만 존재합니다. 로비 창을 닫아도 참가 상태는 유지되며 **L키**, `/gungang lobby`, 전투 테이블로 다시 열 수 있습니다. 다시 열 때 팀·준비·맵 설정을 초기화하지 않습니다. 최초 로비 생성 시 서버 config를 읽고 이후 변경은 `/gungang reload`로 반영합니다. 미배정 플레이어도 대기 중 이동하고 블록을 사용할 수 있습니다. 경기 종료 시에는 팀이 해제됩니다. 무기 선택창을 열거나 무기를 선택할 때는 서버 무기 파일을 다시 읽으며 클라이언트 config는 사용하지 않습니다.

### 플레이어 팀·준비·무기 선택

| 명령어 | 권한 | 기능·조건 |
| --- | --- | --- |
| `/gungang team ALPHA` | 일반 | 로비에서 본인을 **Team ALPHA**에 배정합니다. 기본 빨강이며 option.json에서 변경합니다. |
| `/gungang team BRAVO` | 일반 | 로비에서 본인을 **Team Bravo**에 배정합니다. 기본 파랑이며 option.json에서 변경합니다. |
| `/gungang team SPECTATOR` | 일반 | 로비에서 본인을 관전자로 전환합니다. 관전자는 준비 인원과 랜덤 팀 배치에서 제외됩니다. |
| `/gungang ready` | 일반 | 본인의 준비 상태를 켜거나 끕니다. ALPHA·BRAVO 참가자만 준비할 수 있습니다. |
| `/gungang loadout` | 일반 | 무기 선택 UI를 엽니다. 경기 중에는 자기 팀 스폰 또는 리스폰 대기 중 사용할 수 있습니다. 경기 밖에서는 로비 개방·팀·위치 제한 없이 사용 가능합니다. 기본 E키와 같습니다. [프리셋](SERVER.md#presets) 탭에서 무기 조합 저장·장착·기본값 지정을 할 수 있습니다. |
| `/gungang join [ALPHA\|BRAVO]` | 일반 | 준비·진행 중 경기에 참가합니다. 팀 생략 시 적은 인원 쪽에 배정합니다. 이미 참가한 플레이어의 재참가·팀 변경은 거부합니다. |
| `/gungang join <ALPHA\|BRAVO> <플레이어 또는 선택자>` | OP 2 | 지정한 플레이어를 진행 중인 경기에 참가시킵니다. 예: `/gungang join ALPHA @a`. |
| `/gungang reset` 또는 `/reset` | OP 2 | 경기를 종료하고 접속 중인 전원을 오버월드 `(39, 5, -179)`로 이동합니다. 리스폰 위치, 최대·현재 체력 100, 모험 모드, 빈 인벤토리로 초기화합니다. |

이 명령어들은 플레이어 전용입니다. 팀 인자는 소문자로 입력해도 됩니다. 팀 변경은 전원의 준비 상태를 해제합니다. 다른 플레이어를 지정하는 팀 명령 인자는 없으며, 관리자는 로비 UI에서 대상 행을 선택해 팀을 변경할 수 있습니다.

팀 참가자가 1명 이상 있고 관전자를 제외한 전원이 준비하면 즉시 팀 스폰으로 이동합니다. 혼자 테스트할 때는 `/gungang lobby open` → `/gungang team ALPHA` → `/gungang ready` 순서로 실행합니다. 활성 맵이 있어야 하며 미배정자가 있으면 시작하지 않습니다. **30초 준비 시간** 동안 스폰 공간에 머물며 무기를 선택하고, 이후 전투와 경기 타이머가 자동으로 시작됩니다. 중단은 OP 권한으로 `/gungang stop`을 사용합니다.

### 아이템 NBT 내보내기

#### 백틱 키로 클립보드에 복사

주 손에 아이템을 들고 **백틱(`)** 키를 누르면 전체 ItemStack SNBT 문자열을 클립보드에 복사합니다. 설정 → 조작 → 키 설정의 **주 손 아이템 NBT 복사**에서 키를 바꿀 수 있습니다. OP 권한이나 서버 모드 설치 없이 동작합니다. 채팅·인벤토리 등 화면이 열린 동안에는 작동하지 않으며, 빈손이면 안내만 표시하고 기존 클립보드를 유지합니다.

복사 내용은 `{id,Count,tag,...}` 형태입니다. TaCZ 총기·부착물 ID, 탄약 수, 약실 상태, 발사 모드와 알 수 없는 애드온 태그도 선택적으로 제거하지 않습니다. 실제 서버 자료의 커스텀 총기 3종과 HAMR 부착물 NBT는 SNBT 문자열로 변환한 뒤 다시 읽어 값·자료형이 보존되는지 테스트합니다.

클라이언트가 받은 아이템 데이터 기준이므로 서버 전용 비동기화 정보나 외부 총기 팩 파일까지 복사하는 기능은 아닙니다. 재현할 환경에도 해당 TaCZ 버전·총기·부착물 팩이 필요합니다. JSON 설정의 `itemNbt`에 넣을 때에는 문자열 안의 따옴표와 역슬래시를 JSON 형식으로 이스케이프해야 합니다. 바로 사용할 JSON 파일이 필요하면 아래 서버 내보내기 명령을 사용하세요.

#### 서버 아이템을 JSON 파일로 내보내기

```text
/gungang exportitem my_rifle
```

OP 2 이상의 플레이어가 아이템을 **주 손**에 들고 실행합니다. 전체 ItemStack NBT를 `config/gungang/exports/my_rifle.json`에 무기 객체 형태로 저장합니다. TaCZ 총기와 부착물 NBT도 포함됩니다.

- ID는 영문 소문자·숫자·`_`·`-`로 이루어진 1~48자입니다.
- 손이 비어 있으면 실패합니다. 같은 이름의 파일이 있으면 덮어쓰지 않으므로 다른 ID를 사용하세요.
- 주무기는 생성된 파일을 `config/gungang/server/weapons/`에, 보조무기는 `config/gungang/server/sub_weapons/`에 복사합니다.
- JSON의 `name`, `description`, `usage`와 주무기의 `specs`를 작성하고 `/gungang reload`를 실행합니다. `name`은 지급 아이템의 표시 이름으로 사용됩니다.
- 이 명령은 파일만 내보냅니다. 무기 목록에 자동 등록하거나 아이템을 지급하지 않습니다.

### 설정 변경과 저장 위치

| 항목 | 경로 | 적용 방법 |
| --- | --- | --- |
| 맵별 설정 | `config/gungang/server/maps/*.json` | `/gungang reload` |
| 주무기별 설정 | `config/gungang/server/weapons/*.json` | `/gungang reload` |
| 보조무기별 설정 | `config/gungang/server/sub_weapons/*.json` | `/gungang reload` |
| 핫바 9번 기본 아이템 | `config/gungang/server/slot9.json` | `/gungang reload` |
| 팀 색상·리스폰 시간 | `config/gungang/server/option.json` | 로비에서 `/gungang reload` |
| 서버 음향 | `config/gungang/acoustics.json` | `/gungang reload` |
| 클라이언트 외부 폰트 | `config/gungang/fonts/*.ttf` | 폰트 설정에서 파일 지정 후 F3+T |
| 경기 전적 | `config/gungang/result/*.txt` | 경기 종료 시 자동 저장 |

`/gungang reload`와 바닐라 `/reload`는 다릅니다. `/reload`는 데이터팩을 다시 읽으며 모드 설정 변경에는 `/gungang reload`를 사용합니다.

제한시간은 맵의 `timeLimitSeconds`에 초 단위로 지정합니다. **0은 카운트업**, 양수는 카운트다운입니다. 서버 경기 타이머를 수동으로 시작·정지·초기화하는 별도 명령은 없습니다.

### 클라이언트 구형 타이머 명령

아래 명령은 클라이언트 내부의 별도 카운터를 조작합니다. OP 권한은 필요 없으며 서버 콘솔에서는 사용할 수 없습니다. **현재 HUD에는 이 카운터를 표시하지 않으며, 커스텀 거점 HUD의 서버 경기 타이머에도 영향을 주지 않습니다.**

| 명령어 | 내부 동작 |
| --- | --- |
| `/gungangtimer start` | 기존 값부터 내부 카운터를 시작·재개합니다. |
| `/gungangtimer stop` | 값을 유지한 채 내부 카운터를 멈춥니다. |
| `/gungangtimer reset` | 내부 카운터를 0으로 초기화하고 멈춥니다. |

서버에서 나가면 내부 카운터도 초기화됩니다.

### 경기 시작 예시

활성 맵 JSON에 `"id": "arena"`가 설정되어 있다고 가정합니다. 관리자가 순서대로 실행합니다.

```text
/gungang reload
/gungang lobby open
/gungang map arena
/gungang random
```

그다음 각 팀 참가자가 `/gungang ready`를 입력하거나 로비 UI에서 준비 버튼을 누릅니다. 경기 중 TAB으로 전적을 확인하고, 자기 팀 스폰에서 E키 또는 `/gungang loadout`으로 무기를 변경할 수 있습니다.

경기만 중단하려면 `/gungang stop`, 로비까지 종료하려면 `/gungang lobby close`를 사용합니다.

<a id="debug"></a>

### 디버그: HUD·점수판 미리보기

경기 없이 가짜 경기 데이터를 **명령을 입력한 플레이어에게만** 보내 HUD를 확인합니다. 모두 OP 2, 플레이어 전용이며 경기 준비·진행 중에는 사용할 수 없습니다.

| 명령어 | 기능 |
| --- | --- |
| `/gungang debug hud <control\|deathmatch\|bomb\|escort\|normandy\|ffa\|prophunt\|prophunt_hider> [초] [인원]` | 모드별 상단 HUD 미리보기. 기본 90초·8명. 타이머가 실제로 줄어 10초 카운트다운도 확인할 수 있고, 점수·거점 게이지·호위 진행도·개인전 순위가 시간에 따라 변합니다. 숨바꼭질은 아래를 참고하세요. |
| `/gungang debug scoreboard [인원]` | TAB 점수판 확인용(기본 16명, 최대 32명). TAB을 누르고 있는 동안 표시합니다. |
| `/gungang debug result <alpha\|bravo\|draw>` | 승리 타이틀 후 3초 뒤 경기 결과 화면을 엽니다. |
| `/gungang debug killfeed [개수]` | 가짜 킬로그 1~8줄(기본 5줄). |
| `/gungang debug prop <번호\|random\|tagger\|off>` | 경기 없이 숨바꼭질 변신 테스트. 블록 번호(`01`~`99`) 또는 `random`이면 그 블록으로 변신해 실제 숨는 팀과 같은 조작(T 고정·R 회전·E 상호작용·G 경적·점프 충전·Shift 정렬)과 체력 50으로 움직입니다. 다시 입력하면 다른 블록으로 바뀝니다. `tagger`는 술래(체력 100·산탄총)로 바꾸며, 테스트 중인 다른 플레이어의 변신 블록을 실제로 쏴서 맞힐 수 있습니다. 체력이 0이 되면 탈락 대신 체력을 회복하고 계속합니다. `off`는 변신을 풀고 시작 전 인벤토리·게임 모드·최대 체력을 되돌립니다. 접속 종료나 실제 경기 시작 때도 자동으로 되돌립니다. 경기 중에는 사용할 수 없습니다. |
| `/gungang debug off` | 미리보기 종료 (변신 테스트도 함께 종료) |

점수판 첫 줄은 본인이며 나머지는 `Alpha_N`·`Bravo_N`(개인전은 `Player_N`) 가짜 플레이어입니다. 맵 날씨·시간은 바꾸지 않습니다. HUD 미리보기 중에는 실제 경기처럼 핫바 1번이 고정되며 `debug off`로 해제됩니다. 미리보기 중 실제 경기가 시작되면 자동으로 종료되고 실제 HUD로 바뀝니다. 킬로그 HUD가 꺼져 있으면 가짜 킬로그는 본인 채팅에 사망 메시지로 표시됩니다.

숨바꼭질 미리보기(`prophunt`, `prophunt_hider`)는 인원의 약 1/3을 술래(`Seeker_N`), 나머지를 숨는 팀(`Hider_N`)으로 만듭니다. `[초]`는 찾는 시간입니다.

- 처음 10초는 숨는 시간입니다. 이후 찾는 시간 동안 8초마다 숨는 플레이어 1명이 발견되고, 20초마다 변신 블록이 바뀝니다. 찾는 시간이 끝나면 30초 위치 확인 단계로 넘어갑니다.
- `prophunt`: 본인이 술래입니다. 발견되지 않은 블록은 `?`로 가려집니다.
- `prophunt_hider`: 본인이 숨는 플레이어이며 발견되지 않습니다. 모든 블록이 보이고, 실제 경기처럼 3인칭 시점과 E/T/R/점프 조작·변신 안내 문구가 적용됩니다. 조작 요청은 실제 경기가 없으므로 서버가 무시합니다.

<a id="progression"></a>

## 전적·XP·레벨 조회와 수정

| 명령어 | 기능 |
|---|---|
| `/gungang stats [플레이어]` | 전적·경험치·레벨 조회 |
| `/gungang xp [플레이어]` | 현재 레벨의 경험치/다음 레벨까지 필요한 경험치 조회 |
| `/gungang level [플레이어]` | 레벨·경험치 조회 |
| `/gungang stats set <플레이어> <필드> <값>` | 관리자: 전적 또는 XP 수정 |
| `/gungang xp set <플레이어> <값>` | 관리자: 누적 경험치 지정 |
| `/gungang xp add <플레이어> <값>` | 관리자: 경험치 가감(최종값은 0 이상) |

조회 시 이름 생략은 본인입니다. DB에 있는 오프라인 플레이어는 이름 또는 UUID로 지정할 수 있습니다. 수정 필드는 `xp`, `matches`, `wins`, `kills`, `deaths`, `damage`, `playSeconds`입니다. 예: `/gungang stats set dizzt3942 kills 100`. 전적 변경이 경험치를 자동 재계산하지는 않습니다. XP 변경은 접속자의 HUD에 즉시 반영됩니다. 수정 명령은 OP 권한 레벨 2 이상이 필요합니다.

조회 결과는 `sendCommandFeedback`가 꺼져 있어도 명령 실행자의 채팅에 표시됩니다. `stats`, `xp`, `level`의 경험치는 현재 레벨 구간 기준으로 표시합니다. 예를 들어 누적 XP 141인 2레벨은 `경험치 60/120`이며, 최고 레벨은 `경험치 MAX`입니다.

<a id="area"></a>

## 공간마법사: 영역 선택·등록·되돌리기

관리자(OP 2)가 게임 안에서 `/gungang area wand`로 **공간마법사**를 받습니다. 아이템 ID는 `gungang:spatial_wand`이며 GunGang 아이템 탭에서도 꺼낼 수 있습니다.

1. 지팡이를 주 손에 들고 블록을 **좌클릭**하면 첫 점, **우클릭**하면 둘째 점을 선택합니다. 블록은 부서지거나 사용되지 않습니다.
2. 두 점은 상자의 대각선 모서리입니다. 클릭한 블록도 포함하며, 좌표와 **가로(X) × 세로(Z) × 높이(Y)**를 알려줍니다. 지팡이를 들고 있으면 본인에게만 하늘색 윤곽 입자가 보입니다.
3. `/gungang area save <맵ID> <종류> [번호]`로 저장합니다. 선택만으로 파일이 변경되지는 않습니다.
4. 잘못 저장했다면 `/gungang area undo`를 실행합니다.

공중 위치는 `/gungang area pos1`과 `pos2`로 현재 발 위치를 선택하거나, `/gungang area pos1 10 65 20`처럼 좌표를 입력합니다. 상대 좌표 `~`도 사용할 수 있습니다. `/gungang area info`는 선택 내용, `/gungang area clear`는 선택 초기화입니다. 차원이 바뀌면 이전 차원 선택은 사용할 수 없습니다.

### 저장 종류

맵 ID를 모르면 `/gungang area list`를 입력하세요. 비활성 맵도 포함해 **ID · 이름 · 활성/비활성 · 파일명**을 10개씩 표시합니다. 다음 페이지는 `/gungang area list 2`로 확인합니다. OP 2가 필요하며 콘솔·경기 중에도 조회할 수 있습니다. 설정이 잘못된 파일은 오류로 표시하고 나머지 목록은 계속 표시합니다.

| 종류 | 저장 결과 | 번호 |
| --- | --- | --- |
| `map` | 맵 전체 `previewArea`와 중앙 상공의 `spectatorSpawn` 자동 등록 | 없음 |
| `alpha`, `bravo` | 팀 영역과 영역 안의 스폰 자동 등록. 폭탄전에서는 공격/방어 스폰에 대응 | 없음 |
| `deathzone` | `deathZones` 영역 추가 | 생략=추가, 기존 번호=교체, 다음 번호=추가 |
| `capture` | 일반 점령 거점 영역. 점수·속도 등 기존 설정 유지 | 없음 |
| `spectator` | 선택 상자 중심으로 관전자 위치 설정 | 없음 |
| `attack`, `defense` | 폭탄전 공격/방어 영역과 스폰 | 없음 |
| `bomb_a`, `bomb_b` | 폭탄전 A/B 설치 가능 **바닥 블록** 범위 | 없음 |
| `escort` | 호위 지정 블록 검색 범위 `escort.scanArea` | 없음 |
| `escort_alpha`, `escort_bravo` | 호위 체크포인트 통과 후 팀 부활 위치 | **1~3 필수** |
| `normandy` | 노르망디 거점 중심과 반경 | **1~4 필수** |
| `normandy_spawn` | 노르망디 거점 확보 후 공격팀 스폰 | **1~4 필수** |
| `attack_launch` | 노르망디 공격팀 출발 위치 | 없음 |

스폰 영역은 **바닥 위 발 위치부터 최소 2블록 높이의 빈 공간**을 포함해 선택하세요. 중앙에 가까운 자리 중 단단한 바닥, 빈 몸 공간, 물·용암이 없는 위치를 찾습니다. 찾지 못하면 저장하지 않습니다. 방향은 저장할 때 바라보는 수평 방향입니다. 스폰 검색은 각 변 512블록 이하·총 200만 블록 이하입니다.

`map`은 각 변 최대 512블록이며, 관전자 위치는 상자 중앙에서 위쪽 3블록입니다(월드 높이 이내). `escort`는 각 변 X/Z 1024·Y 256블록 이하, 총 200만 블록 이하여야 합니다. 노르망디는 원형 거점이므로 선택 영역의 X/Z 중 짧은 변의 절반을 반경으로 사용합니다(1~32블록).

### 예시

각 명령을 실행하기 전에 그 용도에 맞는 상자를 새로 선택합니다.

```text
/gungang area wand
/gungang area save arena01 map
/gungang area save arena01 alpha
/gungang area save arena01 bravo
/gungang area save arena01 capture
/gungang area save arena01 deathzone
/gungang area enable arena01 true
/gungang area undo
```

저장 위치는 `config/gungang/server/maps/*.json`입니다. 기존 맵은 파일명이 아닌 JSON의 `id`로 찾고, 다른 설정은 보존합니다. 없는 ID는 새 맵을 **비활성 상태**로 만듭니다. 생성 시 양 팀 스폰의 임시 기본값은 관리자 위치이므로 양 팀 영역을 지정한 뒤 활성화하세요. 폭탄전·노르망디 전용 설정은 해당 모드의 기존 맵/예제를 편집합니다. 호위는 새 ID에 `escort`를 처음 저장하면 기본 제한시간 240초를 적용합니다. 이미 다른 모드 설정이 있는 맵에 호위 설정을 덧붙이면 검증에서 거부됩니다.

저장 즉시 로비 맵 목록에 반영되고 준비 상태가 해제됩니다. 별도 reload는 필요 없습니다. 준비/경기 중 저장·활성화·undo는 거부됩니다. 잘못된 좌표·설정은 검증 후 거부하며 원래 파일을 유지합니다.

되돌리기는 **관리자별 최근 20회**이며 서버 재시작 후에도 `config/gungang/editor-undo/<UUID>.json`에 보관됩니다. 마지막 저장 이전의 파일 내용으로 복원하고, 새로 만든 파일이면 삭제합니다. 다른 관리자나 외부 편집기가 저장 이후 파일을 바꾸었다면 덮어쓰지 않고 거부합니다. 선택 점을 취소하려면 undo 대신 clear를 사용하세요.

<a id="modify"></a>

## modify: 맵·무기·아이템·블록 설정 편집

`/gungang modify`로 기존 맵·무기·아이템·블록 JSON 설정을 편집합니다. **OP 2**가 필요하며 서버 콘솔에서도 사용할 수 있습니다.

```text
/gungang modify <종류> <ID> <항목> <값>
```

| 종류 | 대상 | 저장 위치 (`config/gungang/` 기준) |
| --- | --- | --- |
| `map` | 활성·비활성 맵의 이름, 설명, 시간, 모드별 설정, 기존 좌표 | `server/maps/*.json` |
| `weapon` | 주무기 이름·설명·사용법·분류·표시 스탯·최대 체력·이동 속도 | `server/weapons/*.json` |
| `secondary` | 보조무기 정보·쿨타임·아이템 NBT, 폭탄전 설치/해체 시간 | `server/sub_weapons/*.json` |
| `item` | 지뢰·회복약·전투자극제·방벽·화염병·궤도폭격·생명유지장치·제트팩·스캐너·방패의 실제 효과 설정 | `item/<ID>.json` |
| `block` | 회복 팩·불발탄·가스통·웅덩이·산성 용액·노즐·파괴 가능한 벽 | `item/<ID>.json` |

맵·무기 ID는 파일명이 아닌 JSON 안의 `id`입니다. 아이템·블록 ID는 `molotov`, `acid_nozzle`처럼 파일명의 `.json`을 뺀 이름입니다. `block`은 별도 폴더를 만들지 않고 기존 아이템 설정 폴더를 사용합니다. 설정 파일이 없는 일반 Minecraft 블록이나 외부 모드 전체를 편집하는 명령은 아닙니다.

### 목록과 조회

```text
/gungang modify map
/gungang modify weapon
/gungang modify item
/gungang modify block
/gungang modify item molotov
/gungang modify item molotov cooldownSeconds
```

종류까지만 입력하면 ID 목록, ID까지 입력하면 편집 가능한 항목 목록, 항목까지 입력하면 현재값을 표시합니다. ID·항목·현재값은 **TAB 자동완성**으로 확인할 수 있습니다. 목록과 조회는 경기 중에도 가능합니다.

### 수정 예시

아래 `arena01`, `amk`, `grenade`는 자신의 설정 ID로 바꿔 입력하세요.

```text
/gungang modify map arena01 name 황금 광산
/gungang modify map arena01 description 공격팀이 금광 수레를 호위하는 맵입니다.
/gungang modify map arena01 timeLimitSeconds 240
/gungang modify map arena01 escort.speed 1.5
/gungang modify map arena01 escort.checkpointBonusSeconds 180
/gungang modify weapon amk name 돌격소총
/gungang modify weapon amk description 중거리 전투용 무기
/gungang modify weapon amk specs.damage 32
/gungang modify weapon amk maxHealth 150
/gungang modify weapon amk movementSpeed 105
/gungang modify secondary grenade cooldownSeconds 30
/gungang modify secondary 98 plantSeconds 4
/gungang modify secondary 98 fuseSeconds 45
/gungang modify secondary 99 defuseSeconds 7
/gungang modify item landmine damage 120
/gungang modify item molotov cooldownSeconds 40
/gungang modify item jetpack verticalFlight true
/gungang modify block small_health_pack cooldownSeconds 12
/gungang modify block cracked_oak_planks health 250
/gungang modify block acid_nozzle damage 3
/gungang modify block acid_nozzle damageIntervalTicks 10
```

문자열은 공백을 포함해 그대로 입력합니다. 빈 문자열은 `""`, 따옴표나 줄바꿈을 이스케이프하려면 JSON 문자열 형식을 사용합니다. 숫자에는 숫자만, 참/거짓에는 `true` 또는 `false`를 입력합니다. 틱·개수처럼 정수인 항목에는 소수를 사용할 수 없습니다. 단위는 기존 JSON과 동일합니다(`Seconds`는 초, `Ticks`는 틱, 20틱=1초).

중첩 항목은 `escort.speed`처럼 점으로 구분합니다. 기존 배열 항목은 `normandy.points.0.name`처럼 **0부터 시작하는 번호**를 사용합니다. 선택한 맵에 없는 모드 설정이나 새 배열 항목은 이 명령으로 만들지 않습니다. 새 영역·거점·스폰 등록은 [공간마법사](#area)를 사용하세요. 폭탄전 스폰은 `bomb.attackSpawn.*`/`bomb.defenseSpawn.*`, 설치 영역이 있으면 `bomb.areaA.*`/`bomb.areaB.*`를 편집합니다.

### 적용과 검증

`specs.damage`, `specs.accuracy`, `specs.stability`, `specs.range`, `specs.fireRate`는 **무기 선택 화면의 표시용 정보**입니다. TaCZ 총기의 실제 피해·반동·연사력은 해당 총기팩 설정을 따릅니다. `maxHealth`, `movementSpeed`는 실제 장비 보너스 설정입니다. 내장 아이템의 효과와 전용 쿨타임은 `modify item`에서 변경하세요. 전용 쿨타임이 있는 아이템은 보조무기 파일의 `cooldownSeconds`보다 아이템 설정을 우선합니다.

저장 전 형식·허용 범위·기존 모드 규칙을 검증하고, 성공하면 JSON에 저장한 뒤 설정을 다시 읽습니다. 별도 `/gungang reload`는 필요 없습니다. 로비 준비 상태는 해제됩니다. 이미 지급된 장비의 체력/이동속도 보너스·NBT·쿨타임은 다음 지급/재선택부터 적용되며, 이미 진행 중인 효과나 대기시간을 초기화하지 않습니다. 기존 설치물의 생성 시 체력 등도 새로 설치할 때 반영될 수 있습니다.

경기 준비/진행 중에는 수정할 수 없습니다. 알 수 없는 항목·잘못된 값은 거부하며, 다시 읽기에 실패하면 편집한 파일을 이전 내용으로 복원합니다. 기존의 다른 JSON 필드는 유지합니다. ID와 명령어 실행 훅은 편집 대상에서 제외합니다.

`/gungang area undo`는 공간마법사의 저장 기록만 되돌립니다. modify로 같은 맵을 수정한 경우 area undo는 후속 수정으로 감지하여 덮어쓰지 않습니다. modify 값을 되돌리려면 이전 값으로 다시 설정하세요.
