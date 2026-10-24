# 설정 데이터 등록과 파일 형식

[문서 안내](../README.md) · [데이터 등록](#) · [아이템](ITEMS.md) · [블록](BLOCKS.md) · [명령어](COMMANDS.md)

맵·무기 JSON, 이미지·폰트, 데이터팩 연결과 자동 저장 경로를 한곳에 정리했습니다. 아이템·블록별 기본값은 각각의 목록 문서에서 확인합니다.

- [어느 폴더에 무엇을 넣는가](#folders)
- [등록 순서](#workflow)
- [팀 색상과 부활 옵션](#options)
- [맵 JSON 등록](#maps)
- [주무기·보조무기 JSON과 NBT](#weapons)
- [지도와 PNG 미리보기](#preview)
- [맵 날씨와 시간](#environment)
- [비무장 지대 JSON](#safe-zones)
- [예제 서버 자료 연결](#examples)

<a id="folders"></a>

## 어느 폴더에 무엇을 넣는가

모든 경로는 **실행 중인 서버의 작업 폴더** 기준입니다. 클라이언트 전용 폰트·개인 TOML만 각 클라이언트에 둡니다. JSON은 UTF-8로 저장하고 수정 후 로비에서 `/gungang reload`를 실행합니다. `/gungang area`와 `/gungang modify`로 저장한 변경은 자동 반영됩니다.

| 등록할 자료 | 넣을 위치 | 등록·반영 방법 |
| --- | --- | --- |
| 맵 JSON | `config/gungang/server/maps/<파일명>.json` | 고유 `id`, 실제 스폰·영역 지정 후 `enabled: true`. `/gungang area list`로 확인 |
| 주무기 JSON | `config/gungang/server/weapons/*.json` | 고유 `id`, 전체 ItemStack SNBT와 정보·스탯 등록 |
| 보조무기 JSON | `config/gungang/server/sub_weapons/*.json` | 지급 아이템·설명·사용법·쿨타임 등록. 폭탄/해체기 98·99는 전용 항목 |
| 기본 지급 아이템 | `config/gungang/server/slot9.json` | 무기 JSON과 같은 형식. `null`이면 핫바 9번 지급 안 함 |
| 커스텀 아이템·블록 효과 | `config/gungang/item/<ID>.json` | 서버가 생성한 지원 항목의 값을 편집. 임의 JSON 추가로 새 아이템이 등록되지는 않음 |
| 맵 미리보기 PNG | `config/gungang/server/maps/preview/<파일명>.png` | 맵 `previewImage`에 파일명 등록. 최대 1,000,000바이트·2048×2048 |
| 좌표 지도 | 맵 JSON의 `previewArea` | 범위 등록 시 자동 생성. 캐시는 `server/maps/data/<id>.png` |
| 외부 TTF | 클라이언트 `config/gungang/fonts/*.ttf` | Config의 `fontFile` 선택, F3+T로 반영. 서버에서 자동 배포하지 않음 |
| 팀·부활·XP 보상 | `config/gungang/server/option.json` | 옵션 값 편집 후 reload |
| 팀데스매치 AI 봇 | `config/gungang/server/bot.json` | `/gungang bot` 명령으로 저장·반영. 직접 편집은 서버 재시작 필요. [봇 설정](BOTS.md) |
| 비무장 지대 | `config/gungang/server/safe_zones.json` | `zones` 배열에 영역 추가 후 reload |
| 레벨별 누적 XP 표 | `config/gungang/xp.json` | `cumulativeXp` 91개, 첫 값 0·엄격한 증가 |
| 서버 음향 | `config/gungang/acoustics.json` | 서버가 접속·reload 때 클라이언트에 전송 |
| Minecraft 데이터팩 | `<월드>/datapacks/<팩>/data/<namespace>/functions/*.mcfunction` | 맵의 `onPrepare`·`onStart`·`onEnd`에 `namespace:function` 등록. Minecraft `/reload` 후 `/gungang reload` |

`player-db.json`(전적·XP·무기 프리셋), `result/`, `editor-undo/`, `server/maps/data/`는 자동 저장 데이터입니다. 새 설정을 등록하는 곳이 아닙니다. `exports/<id>.json`은 `/gungang exportitem` 출력이므로 필요한 무기 폴더로 옮기고 정보를 채워야 선택 목록에 등록됩니다.

폴더별 등록 안내를 이 문서로 통합했습니다. 예제 생성 스크립트가 `preview/README.txt`에 쓰던 PNG 파일명·용량·해상도·reload 안내도 아래 미리보기 절에 포함합니다. 프로젝트의 `font/`와 실제 클라이언트의 `config/gungang/fonts/`를 혼동하지 마세요.

<a id="workflow"></a>

## 등록 순서

1. [예제 폴더](server-examples/config/gungang)에서 필요한 JSON만 서버에 복사하거나 `/gungang area wand`로 맵을 만듭니다.
2. 맵 ID·차원·팀 스폰과 영역을 실제 월드에 맞춥니다. 호위/폭탄전/노르망디는 [해당 모드 규칙](GAMEPLAY.md)을 확인합니다.
3. 주 손 아이템의 전체 SNBT를 백틱 키로 복사하거나 `/gungang exportitem <id>`로 내보낸 뒤 주무기/보조무기에 등록합니다.
4. PNG·TTF는 위 표의 폴더에 넣고 연결 필드를 지정합니다. 자동 좌표 지도는 PNG 업로드가 필요 없습니다.
5. `/gungang reload` 후 `/gungang area list`와 장비 선택 화면에서 확인합니다. 오류가 나면 서버 로그의 실제 경로와 검증 메시지를 확인합니다.

영역 등록은 [공간마법사 명령](COMMANDS.md#area), 기존 값 수정은 [modify 명령](COMMANDS.md#modify)을 사용하세요. 파일명과 JSON의 `id`는 서로 다를 수 있습니다.

<a id="options"></a>

## 팀 색상과 부활 옵션

`config/gungang/server/option.json`의 기본 내용입니다. 기존 파일은 덮어쓰지 않으며, 빠진 항목은 기본값을 사용합니다.

```json
{
  "alphaColor": "#FF5555",
  "bravoColor": "#5555FF",
  "respawnSeconds": 5
}
```

색상은 `#RRGGBB`, 리스폰 시간은 1~300초입니다. 로비에서 `/gungang reload`로 반영합니다. 커스텀 HUD·로비·전적·초상화는 지정한 RGB를 사용하며, 바닐라 이름표는 Minecraft의 16가지 팀 색상 중 가장 가까운 색을 사용합니다. `Red` / `Blue` 팀 ID는 유지됩니다.

거점 HUD는 상단의 작은 양 팀 점수 패널과 중앙 원형 점령률을 표시합니다. 점수는 맵의 `winningScore`에 대한 진행 막대로도 표시합니다. 상태 문구·거점 이름 대신 바로 아래에 타이머를 배치하며, 경합은 노란 표시로 구분합니다. 거점 보스바는 생성하지 않으며 일반 보스바는 유지합니다. 경기 타이머는 `advanced_pixel_lcd-7.ttf`에서 만든 전용 텍스처로 숫자와 콜론을 고정폭 칸의 중앙에 정렬합니다. 서버와 클라이언트 모두 새 JAR로 교체하세요.

<a id="maps"></a>

## 맵 JSON 등록

멀티플레이 맵 목록은 **접속한 서버 프로세스의 config 폴더**에서 읽어 클라이언트로 전송합니다. 클라이언트의 같은 경로에 파일을 넣어도 서버 로비에는 반영되지 않습니다. 목록이 비어 있으면 서버의 `config/gungang/server/maps/*.json`에서 `enabled: true`인지 확인하고 서버 명령 `/gungang reload`를 실행하세요. 기본 예제는 비활성 상태입니다.

서버에는 로비·경기가 하나만 존재합니다. 최초 로비 생성 시 서버 설정을 읽으며, 이미 열린 로비를 L키·`/gungang lobby`·전투 테이블로 다시 열면 팀·준비·맵 설정을 그대로 유지합니다. `/gungang lobby open`을 반복해도 새 로비를 만들거나 기존 참가자를 초기화하지 않습니다. 준비·경기 중에는 추가 로비 생성이 거부됩니다. 설정 변경은 `/gungang reload`로 반영합니다. 경기 종료 시에는 팀을 해제합니다. 무기 선택창을 열거나 무기를 선택할 때에는 서버의 `server/weapons`, `server/sub_weapons`, `server/slot9.json`을 다시 읽습니다. 클라이언트는 서버가 전송한 목록과 미리보기 아이템을 사용합니다.

전투 진행 중에는 자기 팀 스폰 구역 또는 리스폰 대기 중 무기를 교체할 수 있습니다. 스폰에서는 액션바에 `E를 눌러 무기 교체`를, 리스폰 대기 중에는 화면 중앙 아래에 남은 시간과 무기 변경 키를 표시합니다. 키를 변경하면 안내에도 실제 키가 표시됩니다. 준비 단계에서는 팀 참가자가 무기를 선택할 수 있으며 기존 스폰 이동 제한은 유지합니다. 경기 밖에서는 로비가 닫혀 있어도 팀·위치 제한 없이 E키나 `/gungang loadout`으로 교체할 수 있습니다. 경기 밖에서 고른 무기는 실제 지급되며 다음 경기의 기본 선택으로 사용합니다. 단, [기본 프리셋](SERVER.md#presets)을 지정한 플레이어는 경기 시작 시 그 프리셋을 지급합니다. 기존 슬롯의 일반 아이템은 인벤토리로 옮기고 공간이 없으면 발밑에 떨어뜨립니다.

맵마다 `config/gungang/server/maps/<파일명>.json`을 만듭니다. `id`는 중복되지 않는 영문 소문자·숫자·`_`·`-`입니다. `gameMode`는 Minecraft 게임 모드이며 `survival`, `adventure`, `creative` 중 하나입니다. 거점과 승리 판정은 모드가 처리합니다.

```json
{
  "id": "arena",
  "name": "테스트 경기장",
  "description": "좌우 대칭 경기장",
  "enabled": true,
  "timeLimitSeconds": 600,
  "gameMode": "adventure",
  "dimension": "minecraft:overworld",
  "alphaSpawn": {"x": 0.5, "y": 65, "z": 0.5, "yaw": 0, "pitch": 0},
  "bravoSpawn": {"x": 40.5, "y": 65, "z": 0.5, "yaw": 180, "pitch": 0},
  "spectatorSpawn": {"x": 20.5, "y": 80, "z": 0.5, "yaw": 0, "pitch": 30},
  "alphaArea": {"minX": -5, "minY": 64, "minZ": -5, "maxX": 5, "maxY": 72, "maxZ": 5},
  "bravoArea": {"minX": 35, "minY": 64, "minZ": -5, "maxX": 45, "maxY": 72, "maxZ": 5},
  "capture": {
    "name": "중앙 거점",
    "area": {"minX": 17, "minY": 64, "minZ": -3, "maxX": 24, "maxY": 70, "maxZ": 4},
    "winningScore": 100,
    "captureStepTicks": 2,
    "scoreIntervalTicks": 30
  },
  "onPrepare": [],
  "onStart": [],
  "onEnd": []
}
```

`alphaArea` / `bravoArea`는 3차원 직육면체이며 플레이어 몸 전체가 들어갈 공간을 지정합니다. 스폰 좌표는 공간 안에 있어야 하고 충분한 바닥과 머리 위 여유는 맵에서 직접 확보해야 합니다. 공간을 지정해도 블록이나 맵이 자동 생성되지는 않습니다. `onPrepare`, `onStart`, `onEnd`에는 서버에 존재하는 데이터팩 함수 ID를 넣습니다. 준비 시작·전투 시작·경기 종료 시 각각 한 번 실행하며 OP 2 권한으로 실행합니다.

선택 필드 `releaseDate`에 맵 출시일을 `yyyy-MM-dd` 형식으로 넣으면(예: `"releaseDate": "2024-02-23"`) 로비 맵 선택 화면의 설명 줄 오른쪽에 `출시 2024.02.23`으로 표시합니다. 생략하거나 빈 문자열이면 표시하지 않으며, 형식이 틀리거나 없는 날짜이면 해당 맵을 불러오지 않습니다. 게임 모드 출시일은 [게임 모드 문서](GAMEPLAY.md)에 정리했습니다.

<a id="weapons"></a>

## 주무기·보조무기 JSON과 NBT

주무기 `maxHealth` / `movementSpeed`, 보조무기 `cooldownSeconds`, 새 지뢰·회복약·자극제·방벽과 `config/gungang/item/*.json`은 [아이템 설정 안내](ITEMS.md)를 참고하세요. 무기 선택 목록은 휠/스크롤바로 탐색하고, 팀 선택 및 전적 화면은 ALPHA 왼쪽·BRAVO 오른쪽으로 표시합니다.

설명·사용방법의 줄바꿈은 JSON 문자열 안에 `\n`으로 입력합니다. `\n\n`은 빈 줄 하나를 넣습니다. 예: `"description": "첫 번째 줄\n두 번째 줄\n\n추가 설명"`. Windows 형식 `\r\n`과 두 번 이스케이프한 `\\n`도 표시할 때 처리합니다. JSON 문자열 내부에 Enter로 실제 개행을 넣으면 JSON 문법 오류가 나므로 이스케이프 표현을 사용하세요. 이 처리는 화면 설명에만 적용하고 `itemNbt`는 변경하지 않습니다.

게임에서 주 손 아이템을 들고 **백틱(`)** 키를 누르면 클라이언트의 전체 아이템 NBT를 클립보드에 복사합니다. 키 설정에서 변경할 수 있습니다. 서버 원본을 JSON 파일로 내보내는 방법은 `/gungang exportitem <id>`이며, [복사와 내보내기 안내](COMMANDS.md)를 참고하세요.

주무기는 `config/gungang/server/weapons/<파일명>.json`, 보조무기는 `config/gungang/server/sub_weapons/<파일명>.json`에 **무기 객체 하나씩** 저장합니다. 배열이나 `primary` / `secondary` 감싸기는 사용하지 않습니다. 각 분류는 1~64개이며 같은 분류의 `id`는 중복될 수 없습니다. 파일명 오름차순으로 UI에 표시하고 첫 무기를 준비 시작 시 기본 지급합니다. `01_`, `02_` 같은 접두사로 순서를 지정할 수 있습니다.

주무기 파일 예시 (`server/weapons/01_mk14_hamr.json`):

`category`는 주무기·보조무기 공통 문자열(최대 32자)입니다. `돌격소총`, `지정사수소총`, `권총`, `근접무기`처럼 자유롭게 지정하며 목록과 상세 설명에 표시합니다. 생략하거나 빈 문자열이면 `미분류`로 처리하므로 기존 JSON도 그대로 사용할 수 있습니다. 카테고리는 표시용이며 무기 NBT·슬롯·성능을 바꾸지 않습니다. `/gungang exportitem` 결과에도 기본 `category: "미분류"`가 포함됩니다.

주무기·보조무기 공통 선택 필드 `releaseDate`(`yyyy-MM-dd`)는 무기 화면 설명 위에 `출시 2014.08.16`처럼 표시합니다. 맵과 같이 생략하면 표시하지 않고, 형식이 틀리면 해당 무기를 불러오지 않습니다.

```json
{
      "id": "mk14_hamr",
      "category": "지정사수소총",
      "itemNbt": "{id:\"tacz:modern_kinetic_gun\",Count:1b,tag:{GunId:\"tacz:mk14\",GunFireMode:\"SEMI\",HasBulletInBarrel:1b,GunCurrentAmmoCount:20,AttachmentSCOPE:{id:\"tacz:attachment\",Count:1b,tag:{AttachmentId:\"tacz:scope_hamr\"}}}}",
      "name": "MK14 / HAMR",
      "description": "HAMR 조준경을 장착한 지정사수 소총",
      "specs": {"damage":"서버 설정값", "accuracy":"서버 설정값", "stability":"서버 설정값", "range":"서버 설정값", "fireRate":"서버 설정값"},
      "usage": "조준 후 발사. 실제 키는 TaCZ 조작 설정을 따릅니다."
}
```

보조무기 파일 예시 (`server/sub_weapons/01_sidearm.json`):

```json
{
      "id": "sidearm",
      "category": "근접무기",
      "itemNbt": "{id:\"minecraft:iron_sword\",Count:1b}",
      "name": "보조 검",
      "description": "보조무기 예시",
      "usage": "왼손 공격 키 사용"
}
```

핫바 9번 파일 예시 (`server/slot9.json`):

```json
{
    "id": "food",
    "itemNbt": "{id:\"minecraft:cooked_beef\",Count:16b}",
    "name": "전투 식량",
    "description": "핫바 9번 기본 지급 아이템",
    "usage": "우클릭"
}
```

`itemNbt`는 `tag` 부분만이 아니라 `{id,Count,tag}`를 포함한 **전체 ItemStack SNBT 문자열**입니다. `name`은 지급된 아이템의 표시 이름을 대체하며 나머지 NBT는 유지합니다. 주무기는 핫바 1번, 보조무기는 왼손으로 지급합니다. 경기 중 클라이언트는 선택 슬롯을 1번으로 고정하므로 2~9번 아이템은 직접 선택해 사용할 수 없습니다(F키 양손 교체·왼손 매크로는 사용 가능). `server/slot9.json`은 무기 객체 하나를 넣거나 파일 내용 전체를 `null`로 지정하며, 파일이 없어도 9번 아이템을 지급하지 않습니다. 주무기 스펙은 UI용 문자열이며 실제 TaCZ 피해량 등을 변경하지 않습니다. 보조무기는 `specs`가 필요 없습니다.

`python scripts/fill_weapon_specs.py`는 `server_resource/tacz`의 TaCZ·LR Tactical 팩 데이터를 읽어 `server_resource/config/gungang/server/weapons`·`sub_weapons`의 비어 있는 설명(`id: 01` 형태)과 스펙(`설정 필요`·`서버 설정값`)을 채웁니다. 이미 작성한 값은 유지하며 `--force`로 덮어쓰고 `--dry-run`으로 미리 확인합니다. 다른 폴더는 경로를 인자로 넘깁니다. 스펙 기준은 다음과 같습니다.

| 스펙 | 계산 |
| --- | --- |
| 피해량 | 근거리 1발 피해. 산탄은 펠릿 합계, 폭발탄은 폭발 피해 포함 |
| 명중률 | `100 / (1 + 0.25×지향사격 분산 + 0.75×조준 분산)` |
| 안정성 | `100 / (1 + 최대 상하 반동 + 최대 좌우 반동)` |
| 사거리 | 근거리 피해의 75% 이상을 유지하는 거리(블록), 탄속×수명과 200블록 이내 |
| 연사력 | RPM. 볼트액션 시간과 1발 장전 무기의 재장전 시간 포함 |

근접 무기는 좌클릭 피해·최대 사거리·좌클릭 쿨다운 기준이며 명중률·안정성은 `근접`으로 표시합니다. 보조무기는 설명만 채웁니다. 팩에 데이터가 없는 무기(예: LR Tactical 내장 카람빗)는 건너뜁니다.

총과 부착물을 실제로 조립한 뒤 `/gungang exportitem my_rifle`을 실행하고, 생성된 JSON 파일을 `server/weapons` 또는 `server/sub_weapons`로 복사하면 됩니다. 내보내기는 기존 파일을 덮어쓰지 않습니다. 무기 UI는 선택 ID만 서버에 보내며, 클라이언트가 임의 NBT를 지급 요청할 수 없습니다. 지급 무기를 다른 컨테이너에 보관했다면 다시 인벤토리로 가져와야 교체할 수 있습니다.

맵은 **`config/gungang/server/maps`만 읽습니다.** 구형 `config/gungang/maps`는 읽거나 자동 복사하지 않습니다. 구형 맵을 사용하려면 필요한 파일만 직접 새 폴더로 옮기세요. 이전 버전이 이미 복사한 파일은 자동 삭제하지 않으므로 새 폴더에서 불필요한 맵을 제거하거나 `enabled: false`로 지정하세요. 서버 로그에 실제 읽은 절대 경로와 활성 맵 수를 표시합니다.

기존 `config/gungang/loadouts.json`은 최초 실행 시 새 구조로 분리하며 원본은 보존합니다. 이미 JSON이 있는 무기 분류에는 기존 무기를 섞어 넣지 않습니다. `.weapons-migrated` 표식이 있으면 다시 가져오지 않습니다. 수정 후 `/gungang reload`로 적용합니다.

### 주무기 JSON

기존 `config/gungang/server/weapons/*.json`의 최상위에 다음 필드를 추가합니다.

```json
{
  "maxHealth": 100,
  "movementSpeed": 100
}
```

생략하면 최대 체력 100, 기본 이동 속도의 100%입니다. 최대 체력은 Minecraft 체력 수치(하트 수가 아님), 이동 속도는 퍼센트입니다. 무기 선택·경기 지급·리스폰 시 적용합니다. 변경 시 현재 체력 비율을 유지하며, 경기 참여 해제 시 모드가 추가한 능력치만 제거합니다. 자극제의 이동 속도 증가는 주무기 이동 속도에 추가로 곱해집니다.

### 보조무기 JSON과 수량

`config/gungang/server/sub_weapons/*.json`의 최상위에 초 단위 `cooldownSeconds`를 추가합니다.

```json
{
  "id": "landmine",
  "itemNbt": "{id:'gungang:landmine',Count:1b}",
  "name": "지뢰",
  "category": "설치형",
  "description": "최대 3개 설치",
  "usage": "보조무기 사용 후 우클릭 설치 / 좌클릭 취소",
  "cooldownSeconds": 10
}
```

`cooldownSeconds`가 없거나 0이면 쿨타임 없이 사용합니다. 모드가 지급한 보조무기는 손을 바꿔도 사용 중 수량이 감소하지 않습니다. 일반 아이템, 인벤토리 분할·이동·버리기는 이 수량 유지 처리 대상이 아닙니다. 총기 내부 탄약 NBT와 내구도를 무한으로 바꾸지는 않습니다.

쿨타임은 실제 소비·설치 성공 시 시작하고, 설치 미리보기·취소·설치 실패에는 시작하지 않습니다. 같은 보조무기를 다시 선택해도 진행 중 쿨타임은 유지합니다. 바닐라 아이템 쿨타임과 LR Tactical 투척물의 기본 쿨타임 대신 이 값을 사용합니다. HUD 아이템 슬롯에는 남은 초와 음영을 표시합니다. 총기 자체 연사 간격과 장전은 총기 모드의 별도 동작입니다.

새 4종의 선택용 JSON은 최초 업데이트 때 `sub_weapons/gungang_*.json`으로 생성하며, 같은 ID 또는 같은 커스텀 아이템을 사용하는 기존 파일이 있으면 추가하지 않습니다. 기존 파일을 덮어쓰지 않습니다. 제공 서버 예제의 지뢰·회복약·자극제·방벽은 기존 선택 항목에서 새 아이템을 사용하도록 변경했습니다.

<a id="preview"></a>

## 지도와 PNG 미리보기

기본 맵 ID는 일리오스 `01`, 리장타워 `02`, 노르망디 `03`, 부산 `04`, 브라질-다운런 `05`입니다. JSON의 `id`는 앞자리 0을 보존하는 문자열이며 맵 목록은 ID 순서로 정렬됩니다. 목록의 맵 이름 앞에 노란색으로 표시됩니다. 파일명은 기존 이름을 유지합니다.

맵 JSON 최상위에 `previewArea`를 지정하면 서버 월드의 블록 지도색으로 북쪽이 위인 지도를 자동 생성합니다. 스크린샷·쉐이더 렌더링이 아니라 마인크래프트 지도처럼 지형을 색으로 나타낸 평면도입니다. 기존 `previewImage` PNG보다 우선하며, `previewArea`가 없거나 `null`이면 기존 PNG를 사용합니다.

```json
"previewArea": {
  "minX": 230, "minY": 0, "minZ": 350,
  "maxX": 415, "maxY": 40, "maxZ": 420
}
```

위 값은 형식 예시입니다. 실제 전장 전체가 들어가는 양 끝 블록 좌표로 바꾸세요. 각 축의 min ≤ max이며 한 축당 최대 512블록입니다. X/Z가 지도 범위이고, 각 기둥에서 minY~maxY 사이 가장 위의 보이는 블록 색을 사용합니다. 실내 맵은 maxY를 천장 아래로 낮추면 내부 바닥·벽을 볼 수 있습니다. 범위 밖 표시는 생략됩니다.

- 원 A / 원 B: ALPHA / BRAVO 스폰. 반투명 사각형은 각 스폰 영역입니다. 색상은 `server/option.json`의 `alphaColor`·`bravoColor`를 따릅니다.
- 폭탄전 맵은 ALPHA 색 A가 공격 스폰, BRAVO 색 B가 방어 스폰입니다. 실제 팀 배정은 라운드마다 달라집니다.
- 주황색 마름모 C: 쟁탈 거점. 주황색 사각형은 거점 범위입니다.
- 금색 마름모 A/B: 폭탄 설치 지점. 주황색 마름모 1~4: 노르망디 순차 거점.
- 북쪽(-Z)이 위, 동쪽(+X)이 오른쪽입니다.

지도는 로비에서 해당 맵 미리보기를 처음 열 때 생성합니다. 서버 틱당 기존 청크 하나를 읽으며, 생성되지 않은 청크를 새로 만들지 않습니다. 없는 청크는 사선 패턴과 `Unexplored` 표시로 구분됩니다. 넓은 범위는 생성에 수십 초 걸릴 수 있습니다. 최대 8개 지도를 메모리에 캐시합니다.

완성된 지도는 서버의 `config/gungang/server/maps/data/<맵 id>.png`에 저장되어, 서버를 재시작하거나 `/gungang reload`·`/reset` 후에도 다시 생성하지 않고 바로 표시합니다. 맵 JSON 내용(`previewArea`, 스폰·거점 좌표 등)이나 팀 색상이 바뀌면 저장된 파일을 무시하고 자동으로 다시 생성합니다. `Unexplored` 청크가 포함된 지도는 나중에 청크가 생길 수 있으므로 파일로 저장하지 않습니다.

맵을 새로 건축해 지형만 바뀐 경우에는 자동으로 감지되지 않으므로 다음 명령으로 저장된 지도를 지우고 로비 화면을 다시 엽니다.

- `/gungang reload map_data`: 저장된 모든 지도 삭제
- `/gungang reload map_data <id>`: 해당 맵 지도만 삭제

일리오스·리장·노르망디·부산·브라질·이탈리아 JSON에는 제공된 미리보기 영역 좌표가 입력되어 있습니다. 이탈리아 ID는 `06`이며 폭탄전과 팀 데스매치를 지원합니다. 폭탄전 영역은 지도에서 금색 사각형과 A/B 기호로 표시됩니다.

### 시간대·날씨 강제 적용

경기 준비부터 경기 종료까지 맵 JSON의 `weather`와 `timeOfDay`를 서버·클라이언트에서 유지합니다. `clear`/`fog`는 비·천둥을 끄고, `rain`/`snow`는 강수량을 최대로 유지합니다. 시간대는 `day`=정오(6000), `dusk`=12000, `night`=18000, `dawn`=23000입니다. 일반 경기 종료는 원래 환경으로 복구합니다.

`/reset` 및 `/gungang reset`은 경기를 종료한 뒤 **맑음·정오(6000틱)**로 설정합니다. 리셋 후에는 서버의 기존 시간·날씨 진행 게임 규칙을 따르며, 다음 경기부터 맵 설정을 다시 강제합니다.

### PNG 직접 등록

1. PNG 이미지를 서버의 `config/gungang/server/maps/preview/`에 넣습니다.
2. 해당 맵 JSON의 최상위에 `"previewImage": "iljang.png"`를 지정합니다.
3. 로비에서 `/gungang reload` 후 맵 선택 화면을 다시 엽니다.

이미지는 최대 1 MB(1,000,000바이트), 가로·세로 각각 2048픽셀까지 지원합니다. 하위 경로 없이 파일명만 지정합니다. 빈 문자열이면 미리보기를 생략합니다. 선택한 이미지가 클라이언트로 전송되므로 클라이언트에 별도로 복사할 필요는 없습니다. 원본 비율을 유지하며 관리자만 실제 경기 맵을 변경할 수 있고, 다른 플레이어도 목록을 눌러 미리볼 수 있습니다.

<a id="environment"></a>

## 맵 날씨와 시간

각 `config/gungang/server/maps/*.json` 최상위에 다음 두 값을 설정합니다. 생략 시 맑음·낮입니다.

```json
"weather": "clear",
"timeOfDay": "day"
```

| 설정 | 허용값 |
| --- | --- |
| `weather` | `clear` 맑음, `fog` 안개, `rain` 비, `snow` 눈 |
| `timeOfDay` | `day` 낮, `dusk` 황혼, `night` 밤, `dawn` 여명 |

경기 준비부터 종료까지 선택 시간대를 고정합니다. 눈·비의 표시를 해당 맵 차원에서 지정하고, 안개는 시야 32블록으로 설정합니다. 종료하면 경기 전 시간·날씨를 복원합니다. 비와 눈은 강수를 지원하는 차원에서 사용하세요. `/gungang reload` 후 다음 경기부터 바뀐 맵 설정을 사용합니다.

<a id="safe-zones"></a>

## 비무장 지대 JSON

`config/gungang/server/safe_zones.json`의 `zones` 배열에 안전지대를 추가합니다. 최대 128곳을 지정할 수 있습니다. 서버 설정이 클라이언트에 자동 전달됩니다.

```json
{
  "zones": [
    {
      "name": "대기실",
      "dimension": "minecraft:overworld",
      "enabled": true,
      "from": { "x": -1, "y": 84, "z": -11 },
      "to": { "x": 24, "y": 116, "z": -31 }
    }
  ]
}
```

두 점은 정수 블록 좌표이며 순서는 상관없습니다. 양 끝의 블록 전체가 포함됩니다. 위 예시는 X -1~24, Y 84~116, Z -31~-11 블록의 범위입니다. 위치 판정 기준은 엔티티의 발 위치입니다. `enabled: false`이면 해당 구역만 비활성화합니다.

안전지대 안에서는 근접 공격, TaCZ 발사·총검 공격, 활·석궁·삼지창·공격용 투척물과 지뢰 설치를 차단합니다. 구역 바깥에서 구역 안으로 가하는 공격 피해도 차단합니다. 공격자가 안전지대로 들어오면 해당 공격자의 남아 있는 투사체 피해도 차단합니다. 낙하·익사 같은 환경 피해는 그대로 적용합니다. 로비·무기고·팀 블록, 음식·회복약 등 일반 상호작용은 유지됩니다.

안에 있는 동안 액션바에 **비무장 지대**를 표시하고 벗어나면 지웁니다. 스폰의 무기 선택 안내보다 우선합니다.

서버 재시작 또는 로비에서 `/gungang reload`로 반영합니다. 파일이 없으면 안전지대가 없는 빈 배열로 생성합니다. 제공된 `server_resource`에는 대기실, 연회장(훈련장 8호실), 비밀연구소(훈련장 6호실)의 세 구역을 등록했습니다.

<a id="examples"></a>

## 예제 서버 자료 연결

[server-examples](server-examples)에 실제 스폰·거점 좌표를 옮긴 3개 맵(된장타워·일히오스·사찰), 플레이어 개인 정보 없이 추출한 총기 NBT 3개, MK14/HAMR 주무기와 S&W Model 3 보조무기의 개별 JSON을 제공합니다. 무기 스펙은 추정하지 않고 편집할 자리로 남겼습니다. S&W 예제는 서버 자료의 `bf1` 총기 팩이 필요합니다.

1. `server-examples/config/gungang`의 필요한 설정을 서버의 `config/gungang`으로 복사합니다. 예제 맵은 **비활성 상태**입니다. 실제 스폰 방의 경계를 확인해 `alphaArea` / `bravoArea`를 고친 뒤 `enabled: true`로 바꾸세요. 예제 공간은 좌표 주변의 임시 범위이며 실제 벽을 분석한 결과가 아닙니다.
2. 기존 `Gungame`의 거점·타이머 실행을 비활성화하세요. 모드와 기존 게임 로직을 동시에 실행하면 보스바·시간·승리 처리가 중복됩니다. 새 연결 데이터팩은 필요 없습니다.
3. `/gungang reload`로 설정을 읽습니다. 예제의 함수 연결은 모두 빈 배열이며 모드만으로 진행됩니다.
4. 기존 커맨드블록의 시작 버튼 대신 `/gungang lobby open`을 사용합니다.

원본 `server_resource` 파일은 수정하지 않았습니다. 맵·총기 팩·커맨드블록·Mohist 플러그인까지 포함한 실제 멀티플레이 경기와 UI 배치는 최종 서버에서 확인해야 합니다.
