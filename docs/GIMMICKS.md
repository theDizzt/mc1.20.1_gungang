# 전장 기믹과 맵 환경

[README](../README.md) · [서버 설정](SERVER.md) · [보조무기 설정](ITEMS.md) · [명령어](COMMANDS.md)

모든 기믹은 `gungang:` 네임스페이스를 사용합니다. 크리에이티브 기능 블록 탭 또는 `/give @s gungang:uxo`처럼 지급해 설치합니다. 설정은 서버의 `config/gungang/item/<아이템 ID>.json`이며, 없는 파일은 시작 시 생성됩니다. 수정 후 `/gungang reload`를 실행합니다. 제공 예제는 [item 폴더](server-examples/config/gungang/item/)에 있습니다.

| ID | 동작 및 기본 설정 |
| --- | --- |
| `uxo` | 누구나 밟으면 폭발. `damage: 80`, `radius: 5`, `respawnSeconds: 30` |
| `gas_barrel` | 빨간 주의 표지 드럼통, 높이 1.5블록. 위·아래 어디를 공격해도 폭발. UXO와 같은 폭발 설정 |
| `cracked_oak_planks` | 파괴 가능한 나무판자, `health: 100` |
| `cracked_glass` / `cracked_glass_pane` | 파괴 가능한 유리/유리판, `health: 60` / `40` |
| `cracked_<색상>_concrete` | 바닐라 16색 콘크리트 각각 별도 설정, `health: 200` |
| `puddle` | 흐르지 않는 1/16 높이 물. `soundVolume: 0.7`, `soundIntervalTicks: 6` |
| `acid` / `acid_bucket` | 빛나는 연두색 유체. 일반 빈 양동이로 원천을 퍼 담으면 `산성 액체가 든 양동이`가 되며, 비우면 일반 양동이로 돌아옵니다. 크리에이티브 탭에는 산성 양동이를 표시하지 않습니다. 효과는 `acid.json`에서 `poisonSeconds: 8`, `amplifier: 0` |
| `nozzle` | 물·용암·산성액체와 같은 칸에 설치 가능한 얇은 쇠 기둥. 바닐라 물 잠김처럼 해당 칸에 원천을 보존하며, 빈 양동이로 액체만 회수하거나 채울 수 있습니다. 위로 물 입자 분사. `height: 3`, `particlesPerSecond: 40` |

폭발은 팀을 구분하지 않고 반경 안 플레이어에게 동일한 기본 피해를 줍니다. 방어구 등 피해 감소는 적용됩니다. 주변 지형을 없애지 않습니다. 재생성 시간은 서버 게임 시간 기준입니다.

벽은 근접 공격·화살·TaCZ 총알로 피해를 받고, HP가 소진되면 외형과 충돌이 사라집니다. `/gungang reset`, 경기 준비·종료 시 설치 위치와 HP를 복구합니다. 청크가 내려가 있던 벽도 다시 로드될 때 복구됩니다. 크리에이티브로 직접 제거한 블록은 복구하지 않습니다. `/gungang cleardeployables`는 기믹을 복구하지 않습니다.

웅덩이 아이템으로 울타리·유리·유리판을 클릭하면 해당 블록을 유지한 채 겹쳐 설치합니다. 겹친 웅덩이는 크리에이티브 공격으로 제거할 수 있습니다. 첨벙 소리는 모드의 발소리 음량·차폐 설정을 따릅니다.

산성액체 원천은 맵 제작 시 `/setblock ~ ~ ~ gungang:acid`로 배치할 수 있습니다. 저장된 양동이의 호환성과 담긴 상태 표현을 위해 `gungang:acid_bucket` ID는 유지합니다.

Oculus/Iris에서는 활성 쉐이더팩의 물 재질 ID를 산성액체에 연결합니다. 팩이 산성액체 전용 재질을 지정했다면 그 설정을 우선합니다. 실제 반사·굴절·색상은 쉐이더팩에 따라 달라집니다. OptiFine 등 다른 쉐이더 구현은 팩의 `shaders/block.properties`에서 `minecraft:water`가 속한 `block.<번호>` 항목에 `gungang:acid`를 추가해야 합니다.

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
