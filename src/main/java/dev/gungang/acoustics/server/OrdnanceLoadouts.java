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
                        "치명상 시 자동 발동: 노란 추가 체력 30이 0.1초마다 1씩 감소합니다. 3초 안에 일반 체력을 회복하면 생존합니다. 활성화 중 몸이 팀 색상으로 빛납니다.",null,
                        "보조 손에 장착하면 자동 발동합니다. 쿨타임 120초.","지원·방어",100d,100d,120d),
                new Loadouts.Weapon("93","{id:'gungang:jetpack',Count:1b}","제트팩",
                        "3초간 중력 없이 바라보는 수평 방향으로 비행. 잔류 불꽃은 다른 플레이어에게 화상 3초를 주고 비행 종료 1초 후 사라집니다. 아군·적 충돌 시 피해 25와 넉백 후 정지. 이동 중 회복·무기 사용 불가.",null,
                        "보조무기 사용 키로 발동합니다. 비행 종료 시 원래 중력 상태로 돌아갑니다.","이동",100d,100d,24d));
    }
    static Loadouts.Weapon upgradePlaceholder(Loadouts.Weapon weapon) {
        if(weapon.description()!=null&&weapon.description().contains("개발중")&&(weapon.itemNbt().contains("zeta:p92")||weapon.itemNbt().contains("bf1:sw_model3")))
            for(var replacement:defaults())if(replacement.id().equals(weapon.id()))return replacement;
        return weapon;
    }
    private OrdnanceLoadouts() { }
}
