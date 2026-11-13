package dev.gungang.acoustics.server;

import java.util.List;

final class OrdnanceLoadouts {
    /** 73 경계 감시 포격, written from the item defaults (ExtraGadgetConfig.BARRAGE_DEFAULTS). */
    static final String BARRAGE_DESCRIPTION="지정한 위치에 빨간 연막탄이 떨어져 반경 32블록의 포격 범위를 표시합니다.\n"
            +"3초 뒤부터 8초 동안 1초마다 미사일 3발, 총 24발이 범위 안에서 사람이 가장 많이 몰린 세 지점으로 떨어집니다.\n"
            +"미사일은 천장을 뚫고 떨어지며 1발당 반경 4블록에 피해 32를 줍니다. 궤도폭격과 달리 엄폐물 뒤에 숨으면 폭발 피해를 받지 않습니다.\n"
            +"사용자 본인과 아군·적군 모두 피해를 받습니다. 최초 장착 시에도 210초 대기합니다 (랜덤 무기 모드 제외).";
    static final String BARRAGE_USAGE="사용 키로 지점 선택 시작. 우클릭 확정 / 좌클릭 취소. 확정한 포격은 취소할 수 없습니다.";
    static final List<Loadouts.Stat> BARRAGE_STATS=List.of(new Loadouts.Stat("미사일 피해","32 (1발)"),new Loadouts.Stat("미사일 폭발 반경","4블록"),
            new Loadouts.Stat("포격 범위","반경 32블록"),new Loadouts.Stat("포격","3초 후 8초간 · 초당 3발 · 총 24발"),new Loadouts.Stat("지정 사거리","64블록"));
    private static final String ARROW_USAGE="왼손 아이템 우클릭 후 복귀 매크로 키(기본 H)로 투척합니다. 주손에 든 경우 우클릭으로 던질 수 있습니다.";
    static List<Loadouts.Weapon> defaults() {
        return List.of(
                new Loadouts.Weapon("76","{id:'gungang:void_anchor',Count:1b}","보이드 앵커",
                        "포물선으로 날아가며 수평 사거리 40블록에 도달하면 전진·상승을 멈추고 지상으로 낙하하는 투창. 꽂힌 창은 높이 2블록, 체력 60, 지속 6초. 10블록 내 가장 가까운 적 1명을 4틱당 1블록의 일정한 속도로 끌어당깁니다. 주변 10블록 내 끌어당길 적이 없으면 즉시 2×2×2 범위에 피해 40을 주고 소멸합니다. 접촉 시 5×5×5 범위에 피해 40, 시간 초과 시 2×2×2 범위에 피해 40을 주고 소멸합니다. 창 파괴 시 끌어당기기 해제. 사용자 사망 후에도 유지됩니다.",null,
                        "우클릭으로 투창합니다. 장애물에 막힌 적은 벽을 통과하지 않습니다. 쿨타임 30초.","투척형",100d,100d,30d,"",false,"2026-11-21"),
                new Loadouts.Weapon("77","{id:'gungang:air_assault',Count:1b}","공중강습",
                        "5블록 위로 상승하고 1초 뒤 바라보는 최대 15블록 이내 지점으로 약 0.3초 동안 급강하합니다. 중심 2×2 피해 60, 외곽 4×4 피해 45, 충격파 12×12 피해 10. 범위별 피해는 중첩되지 않으며 반블록 위를 포함한 범위 안 적을 3블록 띄웁니다. 공중에서 받는 피해 15% 감소. 적에게 피해를 주면 이동속도가 1.25초간 15% 증가합니다. 착지 전 사망하면 취소됩니다.",null,
                        "우클릭으로 사용합니다. 공중 대기 중 시점을 돌려 급강하 방향을 정합니다. 쿨타임 40초.","지원·방어",100d,100d,40d,"",false,"2026-11-21"),
                new Loadouts.Weapon("81","{id:'gungang:molotov',Count:1b}","몰로토프 칵테일",
                        "착탄 즉시 반지름 5블록의 불장판이 8초간 유지됩니다. 범위 안의 대상에게 화상 3초. 자신과 아군도 피해를 받습니다.",null,
                        "보조무기 사용 키로 투척합니다. 블록에 닿으면 즉시 터집니다.","투척형",100d,100d,36d),
                new Loadouts.Weapon("73","{id:'gungang:perimeter_barrage',Count:1b}","경계 감시 포격",
                        BARRAGE_DESCRIPTION,null,BARRAGE_USAGE,"설치형",100d,100d,210d,"",false,"2026-12-19",BARRAGE_STATS),
                new Loadouts.Weapon("87","{id:'gungang:orbital_strike',Count:1b}","궤도폭격",
                        "지점에 자홍색 레이저와 범위 표시 후 5초 뒤 폭격. 반지름 24블록, 중심 256부터 가장자리 84까지 거리 비례 피해. 자신과 아군도 피해를 받습니다. 최초 장착 시에도 240초 대기합니다 (랜덤 무기 모드 제외).",null,
                        "사용 키로 지점 선택 시작, 우클릭으로 확정 / 좌클릭으로 취소. 확정한 폭격은 취소할 수 없습니다.","설치형",100d,100d,240d),
                new Loadouts.Weapon("92","{id:'gungang:life_support',Count:1b}","생명 유지 장치",
                        "치명상 시 자동 발동: 노란 추가 체력 30이 0.1초마다 1씩 감소합니다. 2.9초를 버티면 체력 1로 생존하며, 그 전에 회복하면 회복한 체력을 유지합니다. 활성화 중 몸이 팀 색상으로 빛납니다.",null,
                        "보조 손에 장착하면 자동 발동합니다. 쿨타임 120초.","지원·방어",100d,100d,120d),
                new Loadouts.Weapon("93","{id:'gungang:jetpack',Count:1b}","제트팩",
                        "3초간 중력 없이 바라보는 수평 방향으로 비행. 잔류 불꽃은 다른 플레이어에게 화상 3초를 주고 비행 종료 1초 후 사라집니다. 아군·적 충돌 시 피해 25와 넉백 후 정지. 이동 중 회복·무기 사용 불가.",null,
                        "보조무기 사용 키로 발동합니다. 비행 종료 시 원래 중력 상태로 돌아갑니다.","이동",100d,100d,24d),
                new Loadouts.Weapon("74","{id:'gungang:hermes_boots',Count:1b}","헤르메스의 부츠",
                        "바라보는 방향으로 4블록 순간 돌진합니다. 최대 2회까지 충전되며 사용한 돌진은 6초마다 1회씩 다시 채워집니다. 벽에 닿으면 그 자리에서 멈춥니다. 돌진 잔상의 글로우 색상은 무기 선택 > 프리셋 > 이펙트 색상에서 최대 2가지를 골라 섞을 수 있습니다.",null,
                        "보조무기 사용 키로 돌진합니다. 남은 충전 횟수는 체력바 위 보조 게이지에 칸으로 표시됩니다. 보조무기로 고르면 양발에 신고 다닙니다.","지원",100d,100d,6d,"",false,"",
                        List.of(new Loadouts.Stat("돌진 거리","4블록"),new Loadouts.Stat("최대 충전","2회"),new Loadouts.Stat("충전 시간","6초마다 1회"))),
                new Loadouts.Weapon("89","{id:'gungang:bear_trap',Count:1b}","베어트랩",
                        "밟은 적을 3초간 그 자리에 고정하고, 풀려난 뒤 2초간 구속 효과를 겁니다. 최대 1개 설치. 아군은 걸리지 않습니다.",null,
                        "사용 후 우클릭 설치 / 좌클릭 취소. 밟히지 않은 베어트랩은 보조 손 사용 키를 3초간 눌러 회수합니다.","설치형",100d,100d,36d,"",false,"2026-10-21",
                        List.of(new Loadouts.Stat("고정 시간","3초"),new Loadouts.Stat("구속 효과","2초"),new Loadouts.Stat("회수 시간","3초"),new Loadouts.Stat("최대 설치","1개"))),
                new Loadouts.Weapon("90","{id:'gungang:turret',Count:1b}","터렛",
                        "반경 16블록 안의 적을 명중 확률 100%로 자동 사격합니다. 피해 6, 4틱마다 1발, 넉백 없음. 탄약 120발, 체력 150. 빈 2×2×2 공간에 최대 1개 설치. 쿨타임 120초.",null,
                        "사용 후 우클릭 설치 / 좌클릭 취소. 설치자가 외곽 3블록 이내에서 왼손 사용 후 복귀 키(기본 H)를 길게 눌러 초당 20발 재장전, 왼손 공격 후 복귀 키(기본 G)를 길게 눌러 1틱당 체력 1 수리. 작업 중 사격 중지.","설치형",100d,100d,120d,"",false,"2026-11-01",
                        List.of(new Loadouts.Stat("피해","1"),new Loadouts.Stat("명중 확률","100%"),new Loadouts.Stat("탄약","250발"),new Loadouts.Stat("체력","150"),new Loadouts.Stat("재장전","20발/초"))),
                new Loadouts.Weapon("79","{id:'gungang:arrow_grenade',Count:1b}","화살분열 수류탄",
                        "착탄 지점에서 12초 동안 1초마다 전방향으로 화살 34발을 동시에 발사합니다. 화살 피해 16, 최대 이동 거리 6블록. 벽에 막히며 쿨타임은 투척부터 30초입니다.",null,
                        ARROW_USAGE,"투척형",100d,100d,30d,"",false,"2026-10-27",
                        List.of(new Loadouts.Stat("투사체 피해","16"),new Loadouts.Stat("투사체 사거리","6블록"),new Loadouts.Stat("지속 시간","12초"),new Loadouts.Stat("발사 간격","1초마다 전방향 34발"))),
                new Loadouts.Weapon("80","{id:'gungang:hand_axe',Count:1b}","투척용 손도끼",
                        "우클릭을 길게 눌렀다 떼면 조준 방향으로 곧게 던져 일반 명중 66 피해, 헤드샷 99 피해. 중력을 받아 멀리 갈수록 조금씩 떨어집니다. 투척 거리는 누른 시간에 따라 5블록에서 최대 24블록까지 늘어납니다. 던진 뒤 16초 동안 다시 사용할 수 없습니다. 내구도 없음.",null,
                        "왼손 아이템 우클릭 후 복귀 매크로 키를 홀드하여 충전하고 놓으면 투척 후 복귀합니다. 주손에서는 우클릭을 길게 눌렀다 떼서 투척합니다.","투척형",100d,100d,16d,"",false,"2026-10-22",
                        List.of(new Loadouts.Stat("헤드샷 피해","99"),new Loadouts.Stat("투척 피해","66"),new Loadouts.Stat("투척 거리","5~24블록"),new Loadouts.Stat("투척 쿨타임","16초"))));
    }
    static void migrateBalanceDescriptions(java.nio.file.Path directory)throws java.io.IOException {
        var marker=directory.resolve(".ordnance-balance-v4");if(java.nio.file.Files.exists(marker))return;
        var defaults=new java.util.HashMap<String,Loadouts.Weapon>();for(var weapon:defaults())if(java.util.Set.of("76","77","79").contains(weapon.id()))defaults.put(weapon.id(),weapon);
        try(var files=java.nio.file.Files.list(directory)){
            for(var path:files.filter(p->p.toString().endsWith(".json")).toList()){
                var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
                var weapon=json.has("id")?defaults.get(json.get("id").getAsString()):null;if(weapon==null)continue;
                json.addProperty("description",weapon.description());json.add("stats",ServerFiles.GSON.toJsonTree(weapon.stats()));
                java.nio.file.Files.writeString(path,ServerFiles.GSON.toJson(json));
            }
        }
        java.nio.file.Files.writeString(marker,"Ordnance balance descriptions updated.\n");
    }
    static Loadouts.Weapon upgradePlaceholder(Loadouts.Weapon weapon) {
        if(weapon.itemNbt().contains("gungang:arrow_grenade")&&"왼손 아이템 좌클릭 후 복귀 매크로 키(기본 G)로 투척합니다. 주손에 든 경우 우클릭으로 던질 수 있습니다.".equals(weapon.usage()))
            return new Loadouts.Weapon(weapon.id(),weapon.itemNbt(),weapon.name(),weapon.description(),weapon.specs(),ARROW_USAGE,weapon.category(),weapon.maxHealth(),weapon.movementSpeed(),weapon.cooldownSeconds(),weapon.command(),weapon.jetpackVerticalFlight(),weapon.releaseDate(),weapon.stats());
        if(weapon.description()!=null&&weapon.description().contains("개발중")&&(weapon.itemNbt().contains("zeta:p92")||weapon.itemNbt().contains("bf1:sw_model3")))
            for(var replacement:defaults())if(replacement.id().equals(weapon.id()))return replacement;
        return weapon;
    }
    private OrdnanceLoadouts() { }
}
