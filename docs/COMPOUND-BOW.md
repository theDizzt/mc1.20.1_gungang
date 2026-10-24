# 컴파운드 보우 (주무기 26)

`server_resource/config/gungang/server/weapons/26_compound-bow.json`에 등록합니다. 실제 설치 파일인 Tactical Military Bow 1.0.4, TaCZ 1.1.8-hotfix2, LR Tactical 0.4.3, Curios 5.14.1, GeckoLib 4.8.4를 기준으로 호환 검사를 수행했습니다. 활 모드가 없는 환경에서는 전용 믹스를 적용하지 않습니다. 26번 설정 파일을 사용하는 서버에는 해당 모드와 의존 모드를 설치해야 합니다.

지급 NBT는 다음과 같습니다. 서버가 추가하는 장비 식별 태그와 표시 이름은 기존 장비와 동일하게 적용됩니다.

```snbt
{Count:1b,id:"tacticalmilitarybow:tactical_military_bow",tag:{AmmoCount:1,ArrowType:"piercing",DrawnTick:10,IsAiming:0b,IsDrawing:0b,IsRunning:0b}}
```

- 주손에 활을 들고 있는 동안 핫바 **8번 칸**(내부 인덱스 7)에 `tacticalmilitarybow:normal_arrow` 1개를 지급합니다. 기존 칸의 다른 아이템은 인벤토리로 보존합니다.
- 지급된 화살은 장전 시 소비되지 않습니다. 활 자체의 충전·장전·발사 간격은 유지됩니다.
- 다른 무기를 들거나 활을 왼손으로 옮기면 지급된 화살을 회수합니다. 개인이 가지고 있던 일반 화살은 삭제하지 않습니다.
- 조준 여부는 발사 시점의 서버 상태로 판정합니다. 완전 충전 시 비조준 몸통 **32**, 헤드샷 **48** / 조준 몸통 **42**, 헤드샷 **63**입니다. 덜 당긴 발사는 충전 비율에 따라 낮아집니다. 방어·보호 구역·기존 피해 증폭 등 공통 전투 규칙은 별도로 적용됩니다.
- 활을 주손에 든 동안 1인칭 왼손 아이템 렌더링을 숨깁니다. 왼손의 실제 아이템은 유지됩니다.
- 지뢰·방벽은 실제 투사체 충돌로 피해를 받습니다. 파괴 가능한 GunGang 기믹 블록은 활의 기본 피해를 한 번 받습니다. 스폰 게이트와 보호 구역 규칙도 유지합니다.
- `/gungang cleardeployables`, `/gungang reset`, 경기 준비·시작·종료 때 활의 투사체를 제거합니다. 박힌 화살뿐 아니라 진행 중인 투사체도 정리하며, 미로드 청크의 옛 화살은 다음 로드 시 제거됩니다.

설정 파일 변경은 경기 밖에서 `/gungang reload`로 반영합니다. 자바 코드 변경은 서버·클라이언트의 GunGang 모드 JAR를 교체하고 재시작해야 합니다.

## 개발 검증

`gradlew runServer -PgungangBowSmoke`는 `server_resource/mods`의 모드 파일을 참조하고 별도 `build/bow-smoke-run`에서 검사합니다. 해당 폴더에 테스트 전용 `eula.txt`와 충돌하지 않는 포트의 `server.properties`를 준비해야 합니다. 정상 완료 표식은 `GUNGANG_COMPOUND_BOW_SMOKE_PASS`입니다. 테스트 소스는 배포 JAR에서 제외됩니다.

실제 서버 검사에는 26번 장비 지급, 8번 칸 보존·회수, 장전 소비 차단, 실제 몸통·헤드샷 피해, 지뢰·방벽·블록에 대한 비행 충돌, 명령·경기 수명주기 정리, 미로드 화살 무효화가 포함됩니다. 1인칭 표시 수정은 렌더 이벤트에서 처리하며 실제 클라이언트 화면 확인은 별도로 필요합니다.
