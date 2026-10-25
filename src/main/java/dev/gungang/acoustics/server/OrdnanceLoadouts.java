package dev.gungang.acoustics.server;

import java.util.List;

final class OrdnanceLoadouts {
    static List<Loadouts.Weapon> defaults() {
        return List.of(
                new Loadouts.Weapon("81","{id:'gungang:molotov',Count:1b}","몰로토프 칵테일",
                        "착탄 즉시 반지름 5블록의 불장판이 8초간 유지됩니다. 범위 안의 대상에게 화상 3초. 자신과 아군도 피해를 받습니다.",null,
                        "보조무기 사용 키로 투척합니다. 블록에 닿으면 즉시 터집니다.","투척형",100d,100d,36d),
                new Loadouts.Weapon("87","{id:'gungang:orbital_strike',Count:1b}","궤도폭격",
                        "지점에 자홍색 레이저와 범위 표시 후 5초 뒤 폭격. 반지름 24블록, 중심 256부터 가장자리 84까지 거리 비례 피해. 자신과 아군도 피해를 받습니다. 최초 장착 시에도 240초 대기합니다.",null,
                        "사용 키로 지점 선택 시작, 우클릭으로 확정 / 좌클릭으로 취소. 확정한 폭격은 취소할 수 없습니다.","설치형",100d,100d,240d),
                new Loadouts.Weapon("92","{id:'gungang:life_support',Count:1b}","생명 유지 장치",
                        "치명상 시 자동 발동: 노란 추가 체력 30이 0.1초마다 1씩 감소합니다. 2.9초를 버티면 체력 1로 생존하며, 그 전에 회복하면 회복한 체력을 유지합니다. 활성화 중 몸이 팀 색상으로 빛납니다.",null,
                        "보조 손에 장착하면 자동 발동합니다. 쿨타임 120초.","지원·방어",100d,100d,120d),
                new Loadouts.Weapon("93","{id:'gungang:jetpack',Count:1b}","제트팩",
                        "3초간 중력 없이 바라보는 수평 방향으로 비행. 잔류 불꽃은 다른 플레이어에게 화상 3초를 주고 비행 종료 1초 후 사라집니다. 아군·적 충돌 시 피해 25와 넉백 후 정지. 이동 중 회복·무기 사용 불가.",null,
                        "보조무기 사용 키로 발동합니다. 비행 종료 시 원래 중력 상태로 돌아갑니다.","이동",100d,100d,24d),
                new Loadouts.Weapon("89","{id:'gungang:bear_trap',Count:1b}","베어트랩",
                        "밟은 적을 3초간 그 자리에 고정하고, 풀려난 뒤 2초간 구속 효과를 겁니다. 최대 1개 설치. 아군은 걸리지 않습니다.",null,
                        "사용 후 우클릭 설치 / 좌클릭 취소. 밟히지 않은 베어트랩은 보조 손 사용 키를 3초간 눌러 회수합니다.","설치형",100d,100d,36d,"",false,"2026-10-21",
                        List.of(new Loadouts.Stat("고정 시간","3초"),new Loadouts.Stat("구속 효과","2초"),new Loadouts.Stat("회수 시간","3초"),new Loadouts.Stat("최대 설치","1개"))),
                new Loadouts.Weapon("90","{id:'gungang:turret',Count:1b}","터렛",
                        "반경 16블록 안의 적을 명중 확률 84%로 자동 사격합니다. 피해 1, 매 틱 1발, 넉백 없음. 탄약 250발, 체력 150. 빈 2×2×2 공간에 최대 1개 설치. 쿨타임 210초.",null,
                        "사용 후 우클릭 설치 / 좌클릭 취소. 설치자가 외곽 3블록 이내에서 왼손 사용 후 복귀 키(기본 H)를 길게 눌러 초당 20발 재장전, 왼손 공격 후 복귀 키(기본 G)를 길게 눌러 2틱당 체력 1 수리. 작업 중 사격 중지.","설치형",100d,100d,210d,"",false,"2026-11-01",
                        List.of(new Loadouts.Stat("피해","1"),new Loadouts.Stat("명중 확률","84%"),new Loadouts.Stat("탄약","250발"),new Loadouts.Stat("체력","150"),new Loadouts.Stat("재장전","20발/초"))),
                new Loadouts.Weapon("80","{id:'gungang:hand_axe',Count:1b}","손도끼",
                        "좌클릭 근접 공격 35 피해. 우클릭을 길게 눌렀다 떼면 조준 방향으로 곧게 던져 66 피해. 중력을 받아 멀리 갈수록 조금씩 떨어집니다. 투척 거리는 누른 시간에 따라 5블록에서 최대 24블록까지 늘어납니다. 던진 뒤 16초 동안 다시 사용할 수 없습니다. 내구도 없음.",null,
                        "키보드 F 로 보조무기 전환 후 좌클릭 근접 공격, 우클릭을 길게 눌렀다 떼서 투척. 보조 손 공격/사용 키로도 사용할 수 있습니다.","근접·투척형",100d,100d,16d,"",false,"2026-10-22",
                        List.of(new Loadouts.Stat("근접 피해","35"),new Loadouts.Stat("투척 피해","66"),new Loadouts.Stat("투척 거리","5~24블록"),new Loadouts.Stat("투척 쿨타임","16초"))));
    }
    static Loadouts.Weapon upgradePlaceholder(Loadouts.Weapon weapon) {
        if(weapon.description()!=null&&weapon.description().contains("개발중")&&(weapon.itemNbt().contains("zeta:p92")||weapon.itemNbt().contains("bf1:sw_model3")))
            for(var replacement:defaults())if(replacement.id().equals(weapon.id()))return replacement;
        return weapon;
    }
    private OrdnanceLoadouts() { }
}
