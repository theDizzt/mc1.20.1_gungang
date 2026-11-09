package dev.gungang.acoustics.server;

import java.util.*;

/**
 * Hover help of the weapon patch screen ({@code /gungang modify ui}): what each row's value is for, how it acts in
 * combat and, where it feeds damage, the formula. Text is Korean, lines split by {@code \n}. Formulas marked 추정 are
 * read from how TaCZ / GunGang use the value, not from a measured test.
 * <p>{@link #estimate} turns a TaCZ gun's typed numbers into damage estimates (DPS, shots to kill ...), shown under the
 * damage-related rows while editing.
 */
public final class PatchHelp {
    private static final Map<String, String> TACZ = new HashMap<>(), MELEE = new HashMap<>(), GEAR = new HashMap<>(), ITEM = new HashMap<>(), ITEM_OF = new HashMap<>();
    /** TaCZ rows whose help ends with the live damage estimate. */
    public static final Set<String> ESTIMATED = Set.of("tacz:bullet.damage", "tacz:rpm", "tacz:ammo_amount", "tacz:bullet.bullet_amount",
            "tacz:bullet.extra_damage.head_shot_multiplier", "tacz:reload.cooldown.empty", "tacz:reload.cooldown.tactical", "tacz:bullet.pierce");
    /** Reference health for "shots to kill" (a player's default max health). */
    public static final double REFERENCE_HEALTH = 100;

    private static void tacz(String path, String text) { TACZ.put(path, text); }
    private static void gear(String field, String text) { GEAR.put(field, text); }
    private static void item(String field, String text) { ITEM.put(field, text); }
    /** Help of one item setting's field, when the generic one would mislead. */
    private static void item(String setting, String field, String text) { ITEM_OF.put(setting + "." + field, text); }

    static {
        gear("windStrength", "송풍기 바람 세기. 기본 1, 0~100 사이를 0.01 단위로 입력합니다.\n적중 방향으로 속도를 세기 × 0.1 블록/틱만큼 더하며, 대상마다 한 틱에 한 번 적용됩니다.\n위로 튕기는 넉백과 별개이며, 0이면 바람으로 밀지 않습니다.");
        tacz("script_param.shoot_delay", "연속 발사 시작 전 준비 시간(초). 미니건 기본 0.25, 0이면 즉시 발사합니다.\n연속 발사 중에는 RPM 간격을 유지하며, 사격이 0.15초 이상 끊기면 다시 준비합니다.");
        // ---- TaCZ gun data -------------------------------------------------------------------------------------
        tacz("bullet.damage", "용도: 탄환 1개가 맞았을 때의 기본 피해 (거리 감소 적용 전).\n"
                + "작동: 맞은 거리에 해당하는 '거리별 피해' 구간 값이 있으면 그 값이 이 값을 대신합니다. 산탄이면 탄환마다 따로 들어갑니다.\n"
                + "산출식(추정): 피해 = 구간피해(거리) × (헤드샷이면 헤드샷 배수) × 전투자극제 배수(1 + 피해증가%/100)\n"
                + "방어구가 있으면 (1 − 방어관통률) 만큼은 방어구 감소를 받고, 나머지는 방어구를 무시합니다.");
        tacz("rpm", "용도: 분당 발사 수. 발사 간격 = 60 / RPM 초.\n"
                + "작동: 자동은 누르고 있는 동안 이 간격으로, 반자동·볼트는 클릭이 이 간격보다 빠를 수 없습니다. 경기 중에도 즉시 적용됩니다.\n"
                + "산출식: 초당 발사 = RPM / 60, 이론 DPS = 피해 × 탄환 수 × RPM / 60");
        tacz("ammo_amount", "용도: 탄창 1개에 들어가는 탄 수.\n"
                + "산출식: 탄창 소진 시간 = 탄창 × 60 / RPM 초, 탄창당 최대 피해 = 피해 × 탄환 수 × 탄창");
        tacz("bullet.bullet_amount", "용도: 한 번 쏠 때 나가는 탄환 수 (산탄총의 갈래 수). 탄 1발만 소모합니다.\n"
                + "작동: 탄환마다 탄퍼짐으로 따로 퍼지고, 맞은 탄환마다 '기본 피해'가 따로 들어갑니다.\n"
                + "산출식: 1발 최대 피해 = 피해 × 탄환 수 (전부 명중 시). 실제 명중 수는 거리·탄퍼짐에 따라 줄어듭니다.");
        tacz("bullet.pierce", "용도: 탄환 1개가 맞힐 수 있는 최대 대상 수 (1 = 관통 없음).\n"
                + "작동: 일렬로 선 적을 이 수만큼 차례로 맞히며, 대상마다 같은 피해가 들어갑니다(추정: 관통으로 피해가 줄지 않음).");
        tacz("bullet.knockback", "용도: 탄환에 맞은 대상을 쏜 사람 반대쪽으로 밀어내는 세기 (0.01 단위, 0 = 넉백 없음).\n"
                + "작동: 피해가 들어갈 때 함께 밀립니다. 넉백 저항이 있으면 그만큼 줄어듭니다.\n"
                + "송풍기·넉백 샷건처럼 아군도 미는 총은 GunGang이 직접 밀어냅니다. 송풍기는 대상마다 틱(0.05초)당 한 번만 밀려 탄환 수와 무관합니다.\n"
                + "산출식(추정): 순간 속도 ≈ 세기 블록/틱 (0.4 = 맨손 타격 정도). 계속 맞으면 속도 = 이전/2 + 세기 로 쌓여 최대 약 2배까지.");
        tacz("bullet.speed", "용도: 탄속 (초당 블록, 추정).\n"
                + "작동: 높을수록 먼 표적에 빨리 닿아 리드(예측 사격)가 덜 필요합니다.\n"
                + "산출식(추정): 최대 사거리 ≈ 탄속 × 탄 수명 (공기저항·중력이 있으면 더 짧음)");
        tacz("bullet.life", "용도: 탄환이 사라지기까지의 시간(초).\n"
                + "산출식(추정): 최대 사거리 ≈ 탄속 × 탄 수명. 근접 무기형 총(전자충격기 등)은 이 값으로 사거리를 짧게 만듭니다.");
        tacz("bullet.extra_damage.head_shot_multiplier", "용도: 머리에 맞았을 때 피해 배수.\n"
                + "산출식: 헤드샷 피해 = 구간피해(거리) × 헤드샷 배수 (1 = 차이 없음)");
        tacz("bullet.extra_damage.armor_ignore", "용도: 피해 중 방어구를 무시하는 비율 (0~1).\n"
                + "산출식(추정): 피해 × 방어관통률 은 방어구 무시, 피해 × (1 − 방어관통률) 은 방어구 감소 적용.\n"
                + "GunGang 경기에서 방어구를 입지 않으면 결과 차이가 없습니다.");
        tacz("bullet.explosion.damage", "용도: 탄이 터질 때의 폭발 피해 (로켓·유탄 등).\n"
                + "작동: 폭발 중심에서 멀수록 줄어듭니다. 플레이어·봇에게는 전역 '폭발 피해 배수'와 이 무기의 폭발 배수가 곱해집니다.\n"
                + "산출식(추정): 피해 ≈ 폭발 피해 × (1 − 거리/반경) × 전역 배수 × 무기 배수");
        tacz("bullet.explosion.radius", "용도: 폭발 피해가 닿는 반경(블록).\n"
                + "작동: 반경 밖은 피해 없음, 안쪽은 중심에 가까울수록 피해가 큽니다(추정: 거리에 비례해 감소). 벽 뒤는 덜 맞습니다.");
        tacz("weight", "용도: 들고 있을 때 이동속도를 줄이는 무게(kg).\n"
                + "산출식: 최종 이동속도 ≈ GunGang 이동속도% × (1 − 무게 × 0.015). 음수면 오히려 빨라집니다. 맨 위 줄에 결과가 표시됩니다.");
        tacz("movement_speed.base", "용도: 들고 있을 때 항상 적용되는 이동속도 보정 비율.\n산출식(추정): 이동속도 × (1 + 보정). 예: -0.1 = 약 10% 느림");
        tacz("movement_speed.aim", "용도: 조준(우클릭) 중 추가 이동속도 보정 비율.\n산출식(추정): 이동속도 × (1 + 기본 보정 + 조준 보정). 예: -0.2 = 조준 중 약 20% 느림");
        tacz("movement_speed.reload", "용도: 재장전 중 추가 이동속도 보정 비율.\n산출식(추정): 이동속도 × (1 + 기본 보정 + 재장전 보정)");
        tacz("aim_time", "용도: 조준을 시작해 조준경이 완전히 올라오기까지의 시간(초).\n작동: 이 동안은 조준 탄퍼짐으로 서서히 바뀝니다(추정). 짧을수록 반응 사격이 빠릅니다.");
        tacz("draw_time", "용도: 무기로 바꾼 뒤 쏠 수 있을 때까지의 시간(초).\n작동: 주무기·보조무기 전환 직후의 공백 시간입니다.");
        tacz("put_away_time", "용도: 다른 무기로 바꿀 때 이 무기를 집어넣는 시간(초).\n산출식(추정): 무기 전환 시간 ≈ 이 무기 넣기 + 다음 무기 꺼내기");
        tacz("sprint_time", "용도: 달리기를 멈춘 뒤 사격·조준이 가능해지기까지의 지연(초).\n작동: 길면 달리다가 마주친 적에게 늦게 반응합니다.");
        tacz("reload.feed.empty", "용도: 탄이 하나도 없을 때 재장전을 시작해 탄이 실제로 채워지는 시점(초).\n"
                + "작동: 이 시점 전에 재장전을 끊으면 탄이 채워지지 않습니다. 끝나는 시간은 '재장전 종료'입니다.");
        tacz("reload.feed.tactical", "용도: 탄이 남아 있을 때(전술 재장전) 탄이 실제로 채워지는 시점(초).\n작동: 보통 '탄 없음'보다 짧습니다(약실에 1발이 남아 있음).");
        tacz("reload.cooldown.empty", "용도: 탄이 없을 때 재장전 동작이 모두 끝나 다시 쏠 수 있는 시점(초). 실제 재장전 총 시간입니다.\n"
                + "산출식(추정): 지속 DPS = 탄창당 피해 / (탄창 소진 시간 + 이 값)");
        tacz("reload.cooldown.tactical", "용도: 탄이 남아 있을 때 재장전 동작이 끝나는 시점(초). 전술 재장전 총 시간입니다.");
        for (String stance : List.of("stand", "move", "sneak", "lie", "aim"))
            tacz("inaccuracy." + stance, "용도: " + Map.of("stand", "서서", "move", "이동하며", "sneak", "웅크리고", "lie", "엎드려서", "aim", "조준하고").get(stance)
                    + " 쏠 때의 탄퍼짐(클수록 넓게 퍼짐, 대략 각도 단위).\n"
                    + "산출식(추정): 거리 d에서 퍼지는 반지름 ≈ d × tan(탄퍼짐°). 산탄총은 이 값이 커야 갈래가 퍼지고, 작으면 원거리 명중률이 올라갑니다.");

        // ---- lrtactical melee pack -----------------------------------------------------------------------------
        MELEE.put("data.attack.attack_left.cooldown", "용도: 좌클릭 공격 후 다음 좌클릭까지의 간격(틱, 20틱 = 1초).\n산출식: 초당 공격 = 20 / 간격, 근접 DPS ≈ 좌클릭 피해 × 20 / 간격");
        MELEE.put("data.attack.attack_left.delay", "용도: 좌클릭을 누른 뒤 실제로 피해가 들어가기까지의 지연(틱).\n작동: 휘두르는 동작 중 이 시점에 판정합니다. 길면 맞히기 어렵고, 0이면 즉시 판정합니다.");
        MELEE.put("data.attack.attack_right.cooldown", "용도: 우클릭(강공격) 후 다음 우클릭까지의 간격(틱, 20틱 = 1초).\n산출식: 우클릭 DPS ≈ 우클릭 피해 × 20 / 간격");
        MELEE.put("data.attack.attack_right.delay", "용도: 우클릭을 누른 뒤 피해가 들어가기까지의 지연(틱).\n작동: 강공격은 보통 길게 두어 예측 가능하게 만듭니다.");

        // ---- GunGang weapon / secondary file -------------------------------------------------------------------
        gear("name", "용도: 무기 선택 창·킬로그·HUD에 보이는 이름. 성능과 무관합니다.");
        gear("category", "용도: 무기 선택 창의 분류(돌격소총, 산탄총 ...). 같은 분류끼리 모여 표시됩니다. 성능과 무관합니다.");
        gear("maxHealth", "용도: 이 무기를 고른 플레이어의 최대 체력.\n"
                + "작동: 장착 중인 플레이어에게 즉시 적용됩니다. 무거운 무기에 높게 주어 균형을 맞추는 값입니다.\n"
                + "산출식: 처치에 필요한 피해 = 최대 체력. 체력 재생(전역)도 최대 체력의 %로 회복합니다.");
        gear("movementSpeed", "용도: 기본 이동속도 대비 %(100 = 기본).\n"
                + "산출식: 최종 이동속도 ≈ 이 값% × (1 − TaCZ 무게 × 0.015). 맨 위 줄에 결과가 표시됩니다.");
        gear("description", "용도: 무기 선택 창의 설명 문장. 표시 전용이며 성능에 영향이 없습니다. 줄바꿈은 \\n.");
        gear("usage", "용도: 무기 선택 창의 사용방법 문장. 표시 전용입니다. 줄바꿈은 \\n.");
        gear("releaseDate", "용도: 무기가 추가된 날짜(yyyy-MM-dd). 무기 선택 창의 정렬·신규 표시에 쓰입니다.");
        gear("specs.damage", "용도: 무기 선택 창 막대의 '피해' 표시 값. 표시 전용 — 실제 피해는 TaCZ '기본 피해'입니다.");
        gear("specs.accuracy", "용도: 무기 선택 창의 '정확도' 표시 값. 표시 전용 — 실제는 TaCZ 탄퍼짐 값입니다.");
        gear("specs.stability", "용도: 무기 선택 창의 '안정성' 표시 값. 표시 전용 — 실제 반동은 총기팩 반동 데이터입니다.");
        gear("specs.range", "용도: 무기 선택 창의 '사거리' 표시 문구. 표시 전용 — 실제는 거리별 피해 구간·탄 수명입니다.");
        gear("specs.fireRate", "용도: 무기 선택 창의 '연사력' 표시 문구. 표시 전용 — 실제는 TaCZ RPM입니다.");
        gear("stats", "용도: 무기 선택 창 오른쪽의 세부 수치 줄(이름 · 값). 표시 전용이며 성능에 영향이 없습니다.\n작동: 이름과 값을 둘 다 비우면 그 줄이 삭제되고, 빈 '새 표시 항목'을 채우면 줄이 늘어납니다(최대 12줄).");
        gear("cooldownSeconds", "용도: 보조무기를 쓴 뒤 다시 쓸 수 있을 때까지의 시간(초).\n작동: '아이템 효과'에 쿨타임이 따로 있으면 그 값이 우선합니다. 0이면 쿨타임 없음(총·근접형).");
        gear("plantSeconds", "용도: 폭탄전에서 E를 누르고 있어야 하는 설치 시간(초).");
        gear("fuseSeconds", "용도: 폭탄을 설치한 뒤 터질 때까지의 시간(초). 방어팀은 이 안에 해체해야 합니다.");
        gear("defuseSeconds", "용도: 방어팀이 설치된 폭탄을 해체하는 데 걸리는 시간(초).");
        gear("elements.burnDamage", "용도: 이 무기에 맞아 불이 붙은 동안 화상 1회(약 1초마다)의 피해.\n산출식(추정): 화상 총 피해 ≈ 이 값 × 불붙은 초");
        gear("elements.witherDamage", "용도: 이 무기가 건 위더 효과의 1회당 피해(위더 I은 약 2초마다).\n산출식(추정): 총 피해 ≈ 이 값 × 지속 초 / 2");
        gear("elements.poisonDamage", "용도: 이 무기가 건 독 효과의 1회당 피해(독 I은 약 1.25초마다).\n산출식(추정): 총 피해 ≈ 이 값 × 지속 초 / 1.25");
        gear("elements.explosionMultiplier", "용도: 이 무기 폭발이 플레이어·봇에게 주는 피해 배수.\n산출식: 폭발 피해 × 전역 '폭발 피해 배수' × 이 값");

        // ---- GunGang item effect settings (generic by field name) -----------------------------------------------
        item("damage", "용도: 이 아이템이 한 번 맞힐 때의 피해.\n산출식(추정): 피해 × 전투자극제 배수(사용자가 효과 중일 때). 범위형이면 범위 안 대상마다 들어갑니다.");
        item("radius", "용도: 효과가 닿는 반경(블록). 반경 안 대상 모두에게 적용됩니다.");
        item("range", "용도: 효과·설치·공격이 닿는 거리(블록).");
        item("durationSeconds", "용도: 효과가 유지되는 시간(초).");
        item("cooldownSeconds", "용도: 다시 사용할 수 있을 때까지의 시간(초). 무기 파일의 쿨타임보다 우선합니다.");
        item("health", "용도: 설치물의 체력. 이만큼 피해를 받으면 부서집니다.");
        item("heal", "용도: 사용하면 회복하는 체력. 회복될 양은 체력바에 초록색으로 미리 보입니다.");
        item("speed", "용도: 이동·비행 속도(틱당 블록에 가까운 값, 추정). 초당 블록 ≈ 값 × 20.");
        item("capacity", "용도: 용량(탄약 수, 방어량 등). 다 쓰면 재장전·수리가 필요합니다.");
        item("projectileRange", "용도: 투사체가 날아가는 거리(블록).");
        item("projectileSpeed", "용도: 투사체 속도(틱당 블록, 추정).");
        item("throwSpeed", "용도: 던지는 속도(틱당 블록, 추정). 클수록 멀리 날아갑니다.");
        item("throwDamage", "용도: 던져서 맞혔을 때의 피해.");
        item("emissionIntervalTicks", "용도: 파편·투사체를 내보내는 간격(틱, 20틱 = 1초).\n산출식(추정): 발사 수 ≈ 지속 초 × 20 / 간격");
        item("burnSeconds", "용도: 맞은 대상이 불타는 시간(초).\n산출식(추정): 화상 총 피해 ≈ 화상 피해 × 화상 시간");
        item("burnDamage", "용도: 화상 1회(약 1초마다)의 피해. 이 아이템이 붙인 불에만 적용됩니다.");
        item("knockback", "용도: 밀어내는 세기. 클수록 멀리 밀립니다(넉백 저항이 있으면 줄어듦).");
        item("detectionRange", "용도: 적을 감지해 반응하는 거리(블록).");
        item("fireIntervalTicks", "용도: 사격 간격(틱).\n산출식: DPS ≈ 피해 × 20 / 간격");
        item("reloadPerSecond", "용도: 초당 다시 채워지는 탄약.");
        item("absorption", "용도: 추가로 주어지는 체력(노란 체력). 실제 체력보다 먼저 깎입니다.");
        item("triggerRadius", "용도: 적이 이 거리(블록) 안에 들어오면 작동합니다.");
        item("delaySeconds", "용도: 사용 후 효과가 실제로 발동하기까지의 지연(초).");
        item("centerDamage", "용도: 중심에서의 최대 피해.\n산출식(추정): 피해 = 중심 피해 − (중심 피해 − 가장자리 피해) × 거리/반경");
        item("edgeDamage", "용도: 반경 끝에서의 최소 피해. 중심과 가장자리 사이는 거리에 비례합니다.");
        item("collisionDamage", "용도: 몸으로 부딪힌 대상에게 주는 피해. 아군에게도 들어갑니다.");
        item("maxPlaced", "용도: 한 사람이 동시에 설치해 둘 수 있는 최대 개수. 넘으면 가장 오래된 것이 사라집니다(추정).");
        item("dashDistance", "용도: 대시 한 번에 이동하는 거리(블록).");
        item("energyCost", "용도: 대시 한 번에 쓰는 에너지(최대 100, 추정).\n산출식(추정): 연속 대시 수 = 100 / 소모");
        item("rightClickDamage", "용도: 우클릭(강공격) 피해.");
        item("bleedDamagePerSecond", "용도: 출혈 상태의 초당 피해.\n산출식: 출혈 총 피해 = 초당 피해 × 출혈 시간");
        item("bleedSeconds", "용도: 출혈이 지속되는 시간(초).");
        item("rootSeconds", "용도: 걸린 대상이 움직이지 못하는 시간(초).");
        item("slowSeconds", "용도: 속박이 풀린 뒤 이어지는 둔화 시간(초).");
        item("startDamage", "용도: 첫 1회 피해. 이후 콤보마다 '콤보마다 증가'만큼 커집니다.\n산출식: n번째 피해 = min(최대 피해, 첫 피해 + 증가 × (n − 1))");
        item("damageStep", "용도: 연속으로 맞을 때마다 늘어나는 피해.\n산출식: n번째 피해 = min(최대 피해, 첫 피해 + 증가 × (n − 1))");
        item("maxDamage", "용도: 늘어나는 피해의 상한.");
        item("intervalSeconds", "용도: 반복 효과의 간격(초).\n산출식: 총 횟수 ≈ 지속 시간 / 간격");
        for (int i = 1; i <= 6; i++) item("motion" + i + "Damage", "용도: 연속 공격 " + i + "번째 모션의 피해. 모션은 1→6 순서로 이어집니다.\n산출식(추정): 콤보 1회전 피해 = 모션 1~6 피해 합");
        item("slashAngle", "용도: 베기 판정 부채꼴의 각도(°). 정면 기준 이 각도 안의 대상이 맞습니다.");
        item("thrustAngle", "용도: 찌르기(모션 6) 판정 각도(°). 좁을수록 정확히 겨눠야 맞습니다.");
        item("guardMovementSpeed", "용도: 방어 중 이동속도(%, 100 = 평소와 같음).");
        item("attackIntervalSeconds", "용도: 공격 사이의 최소 간격(초). 경기 중에도 즉시 적용됩니다.\n산출식: DPS ≈ 피해 / 간격");
        item("arcDamage", "용도: 직격 대상 주변으로 튀는 전격의 피해(대상마다).");
        item("arcRadius", "용도: 전격이 튀는 반경(블록).");
        item("glowSeconds", "용도: 맞은 대상이 발광(벽 너머로 보임)하는 시간(초).");
        for (int i = 1; i <= 5; i++) item("tier" + i + "Damage", "용도: 콤보 " + i + "단의 피해. 연속으로 맞힐수록 다음 단계 피해가 들어갑니다" + (i == 5 ? " (5단에서 유지)." : "."));
        item("guardReductionPercent", "용도: 방어 중 정면 피해 경감률(%).\n산출식: 받는 피해 = 피해 × (1 − 경감% / 100)");
        item("guardAngle", "용도: 방어가 통하는 정면 각도(°). 이 밖(옆·뒤)에서 온 공격은 막지 못합니다.");
        item("explosionPlayerMultiplier", "용도: 모든 폭발이 플레이어·봇에게 주는 피해 배수(전역).\n산출식: 최종 폭발 피해 = 폭발 피해 × 이 값 × 무기별 폭발 배수");
        item("percentPerSecond", "용도: 체력 재생 속도(초당 최대 체력의 %).\n산출식: 1회 회복 = 최대 체력 × % / 100 × 간격, 가득 차는 시간 ≈ 100 / % 초");
        item("healthCostPercent", "용도: 사용할 때 소모하는 체력(최대 체력의 %). 남은 체력이 이 이하면 쓸 수 없습니다.");
        item("movementBonusPercent", "용도: 효과 중 이동속도 증가(%).");
        item("damageBonusPercent", "용도: 효과 중 주는 피해 증가(%).\n산출식: 피해 × (1 + 증가% / 100). 총·근접·아이템 피해 모두에 곱해집니다(방어 계산 뒤).");
        item("attackBonusPercent", "용도: 효과 중 공격 속도 증가(%, 추정).");
        item("poisonSeconds", "용도: 걸리는 독의 지속 시간(초).");
        item("amplifier", "용도: 효과 단계(0 = I단계). 높을수록 독 피해 간격이 짧아집니다.");
        item("damageIntervalTicks", "용도: 피해 간격(틱).\n산출식: DPS = 피해 × 20 / 간격");
        item("respawnSeconds", "용도: 터진 뒤 다시 생기기까지의 시간(초).");
        item("repairSeconds", "용도: 파손된 뒤 수리되기까지의 시간(초).");
        item("movementPenaltyPercent", "용도: 사용(방어) 중 이동속도 감소(%).");
        item("regenDelaySeconds", "용도: 마지막 전투 후 회복이 시작되기까지의 시간(초).");
        item("regenPerSecond", "용도: 회복이 시작된 뒤 초당 회복량.");
        item("meleeReductionPercent", "용도: 들고 있는 동안 받는 근접 피해 감소(%).");
        item("disarmSeconds", "용도: 철거(해제)에 걸리는 시간(초).");
        item("trailSeconds", "용도: 지나간 자리의 불꽃이 남는 시간(초).");
        item("drainAmount", "용도: 한 번에 줄어드는 추가 체력.\n산출식: 버티는 시간 = 추가 체력 / 감소량 × 감소 간격");
        item("drainIntervalSeconds", "용도: 추가 체력이 줄어드는 간격(초).");
        item("chargeSeconds", "용도: 최대 거리로 던지기까지 모으는 시간(초).");
        item("minThrowDistance", "용도: 모으지 않고 던질 때의 거리(블록).");
        item("maxThrowDistance", "용도: 끝까지 모아 던질 때의 거리(블록). 사이는 모은 시간에 비례합니다(추정).");
        item("recallSeconds", "용도: 회수에 걸리는 시간(초).");
        item("placementRange", "용도: 설치할 수 있는 거리(블록).");
        item("reloadRange", "용도: 가까이 있으면 탄약을 보충해 주는 거리(블록).");

        // ---- Fields whose meaning depends on the item -----------------------------------------------------------
        item("health_regeneration", "delaySeconds", "용도: 마지막으로 피해를 받은 뒤 자연 재생이 시작되기까지의 시간(초).");
        item("health_regeneration", "intervalSeconds", "용도: 자연 재생 1회의 간격(초).\n산출식: 1회 회복 = 최대 체력 × 초당% / 100 × 간격");
        item("orbital_strike", "range", "용도: 폭격 위치를 지정할 수 있는 최대 거리(블록).");
        item("orbital_strike", "delaySeconds", "용도: 위치를 확정한 뒤 폭격이 떨어지기까지의 시간(초).");
        item("jetpack", "speed", "용도: 비행 속도(틱당 블록). 초당 블록 ≈ 값 × 20.\n산출식: 비행 거리 ≈ 속도 × 20 × 지속 시간");
        item("jetpack", "burnDamage", "용도: 불꽃에 닿아 붙은 화상의 초당 피해.\n산출식: 화상 총 피해 = 화상 피해 × 화상 시간");
        item("fallout", "radius", "용도: 낙진 구름의 반경(블록). 아군에게도 피해가 들어갑니다.");
        item("fallout", "durationSeconds", "용도: 낙진 구름이 남아 있는 시간(초).\n산출식: 최대 횟수 = 지속 시간 / 간격");
        item("turret", "damage", "용도: 포탑 탄 1발의 피해.\n산출식: 포탑 DPS = 피해 × 20 / 사격 간격(틱)");
        item("turret", "capacity", "용도: 포탑 탄약. 바닥나면 재장전 범위 안에서 채워야 합니다.\n산출식: 연속 사격 시간 = 탄약 × 사격 간격 / 20 초");
        item("acid_nozzle", "damage", "용도: 산성 분수 안에 있을 때 1회 피해.\n산출식: DPS = 피해 × 20 / 피해 간격(틱)");
        item("arc_thrower", "damage", "용도: 직격 대상의 피해. 주변 대상은 전격 피해를 따로 받습니다.");
        item("katana", "rightClickDamage", "용도: 우클릭 강공격 피해. 맞으면 출혈이 이어집니다.\n산출식: 총 피해 = 우클릭 피해 + 출혈 초당 피해 × 출혈 시간");
        // ---- GunGang blocks (전역 tab, /gungang modify block) -------------------------------------------------------
        item("soundVolume", "용도: 소리 크기 (1 = 기본).");
        item("soundIntervalTicks", "용도: 위를 걸을 때 소리가 나는 최소 간격(틱, 20틱 = 1초).");
        item("height", "용도: 분수 물줄기의 높이(블록). 이 높이 안에 있는 대상이 효과를 받습니다.");
        item("particlesPerSecond", "용도: 초당 입자 수. 보이기만 하며 효과에는 영향이 없습니다(많으면 클라이언트 부하 증가).");
        for (String pack : List.of("small_health_pack", "large_health_pack")) {
            item(pack, "heal", "용도: 힐팩 블록을 밟거나 사용했을 때 회복하는 체력. 회복될 양은 체력바에 초록색으로 미리 보입니다.\n산출식: 회복 후 체력 = min(최대 체력, 현재 체력 + 회복량)");
            item(pack, "cooldownSeconds", "용도: 한 번 쓰인 힐팩이 다시 채워지기까지의 시간(초).");
        }
        for (String blast : List.of("uxo", "gas_barrel")) {
            String what = blast.equals("uxo") ? "불발탄(밟거나 맞히면 터짐)" : "가스통(맞히거나 폭발에 휘말리면 터짐)";
            item(blast, "damage", "용도: " + what + "이 터질 때 반경 안 모두에게 주는 피해. 아군·적을 가리지 않습니다.\n"
                    + "작동: 반경 안이면 거리와 상관없이 같은 피해이고, 이어서 전역 '폭발 피해 배수'가 곱해집니다. 주변 불발탄·가스통·금 간 벽도 연쇄로 맞습니다.\n"
                    + "산출식: 받는 피해 = 피해 × 전역 폭발 배수");
            item(blast, "radius", "용도: 폭발 피해가 닿는 반경(블록). 반경 안이면 모두 같은 피해를 받습니다.");
            item(blast, "respawnSeconds", "용도: 터진 뒤 다시 생기기까지의 시간(초).");
        }
        item("puddle", "soundVolume", "용도: 물웅덩이를 밟을 때 나는 소리 크기 (1 = 기본). 클수록 멀리서도 발소리가 들립니다.");
        item("acid", "poisonSeconds", "용도: 산성 용액에 닿았을 때 걸리는 독의 지속 시간(초). 닿아 있는 동안 계속 갱신됩니다.");
        item("acid", "amplifier", "용도: 걸리는 독의 단계(0 = I). 높을수록 독 피해가 자주 들어갑니다.\n산출식(추정): 독 피해 간격 ≈ 25 / 2^단계 틱");
        item("acid_nozzle", "damage", "용도: 산성 분수 물줄기 안에 있을 때 한 번에 받는 피해. 아군·적을 가리지 않습니다.\n산출식: DPS = 피해 × 20 / 피해 간격(틱)");
        item("acid_nozzle", "damageIntervalTicks", "용도: 산성 분수의 피해 간격(틱, 20틱 = 1초).\n산출식: DPS = 피해 × 20 / 피해 간격");
        item("hand_axe", "arcDegrees","용도: 던질 때 위로 꺾는 각도(°). 0이면 바라보는 방향 그대로 날아갑니다.");
    }

    /** Hover help of a patch row, or "" when there is none. {@code setting} is the item effect file behind the weapon (or null). */
    public static String of(String key, String setting) {
        if (key == null) return "";
        if (key.startsWith("tacz:")) return TACZ.getOrDefault(key.substring(5), "용도: TaCZ 총기 데이터 파일의 " + key.substring(5) + " 값.");
        if (key.startsWith("melee:")) return MELEE.getOrDefault(key.substring(6), "용도: 근접 무기 팩 파일의 " + key.substring(6) + " 값.");
        if (key.startsWith("gg:")) {
            String field = key.substring(3);
            if (field.matches("stats\\.\\d+\\.(name|value)")) field = "stats";
            return GEAR.getOrDefault(field, "용도: GunGang 무기 파일의 " + field + " 값.");
        }
        if (key.startsWith("item:")) {
            String field = key.substring(5);
            String text = setting == null ? null : ITEM_OF.get(setting + "." + field);
            if (text == null && setting != null && setting.startsWith("cracked_") && field.equals("health"))
                text = "용도: 금 간 블록의 체력. 총알·폭발 피해가 쌓여 0이 되면 부서지고, 경기(맵)가 다시 시작되면 복구됩니다.\n산출식: 부수는 데 필요한 탄 수 ≈ 체력 / 1발 피해";
            if (text == null) text = ITEM.get(field);
            String file = setting == null ? "" : "\n설정 파일: item/" + setting + ".json · " + field;
            return (text == null ? "용도: 아이템 효과 설정 " + field + " 값." : text) + file;
        }
        return "";
    }

    /**
     * Damage estimate of a TaCZ gun from its numbers (missing ones count as unknown). Body hits at a distance that
     * keeps the base damage; every pellet hitting. Lines split by {@code \n}; "" without damage and fire rate.
     */
    public static String estimate(Map<String, Double> v, double health) {
        Double damage = v.get("tacz:bullet.damage"), rpm = v.get("tacz:rpm");
        if (damage == null || rpm == null || damage <= 0 || rpm <= 0) return "";
        double pellets = Math.max(1, v.getOrDefault("tacz:bullet.bullet_amount", 1.0));
        double head = v.getOrDefault("tacz:bullet.extra_damage.head_shot_multiplier", 1.0);
        double shot = damage * pellets, perSecond = rpm / 60, dps = shot * perSecond;
        var out = new StringBuilder("── 현재 값으로 추정 (기본 피해 구간 · 전부 명중) ──");
        out.append("\n1발 피해: ").append(n(damage)).append(pellets > 1 ? " × " + n(pellets) + "갈래 = " + n(shot) : "")
                .append(head != 1 ? " · 헤드샷 " + n(shot * head) : "");
        out.append("\n이론 DPS: ").append(n(shot)).append(" × ").append(n(rpm)).append(" / 60 = ").append(n(dps)).append(head != 1 ? " (헤드샷 " + n(dps * head) + ")" : "");
        int body = (int) Math.ceil(health / shot - 1e-9), heads = (int) Math.ceil(health / (shot * head) - 1e-9);
        double ttk = (body - 1) / perSecond;
        out.append("\n체력 ").append(n(health)).append(" 처치: 몸샷 ").append(body).append("발").append(head != 1 ? " · 헤드샷 " + heads + "발" : "")
                .append(" (최소 ").append(n(ttk)).append("초)");
        Double mag = v.get("tacz:ammo_amount");
        if (mag != null && mag > 0 && mag < 100000) {
            double empty = mag / perSecond, total = shot * mag;
            out.append("\n탄창 ").append(n(mag)).append("발: 소진 ").append(n(empty)).append("초 · 탄창당 ").append(n(total));
            Double reload = v.get("tacz:reload.cooldown.empty");
            if (reload != null && reload > 0) out.append("\n지속 DPS (재장전 ").append(n(reload)).append("초 포함): ").append(n(total / (empty + reload)));
        }
        Double pierce = v.get("tacz:bullet.pierce");
        if (pierce != null && pierce > 1) out.append("\n관통: 일렬로 선 적 최대 ").append(n(pierce)).append("명에게 같은 피해");
        out.append("\n※ 거리별 피해 구간·전투자극제·방어구는 반영하지 않은 값입니다.");
        return out.toString();
    }
    private static String n(double value) {
        double r = Math.round(value * 100) / 100.0;
        return r == Math.rint(r) ? Long.toString((long) r) : String.format(Locale.ROOT, "%.2f", r).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    private PatchHelp() { }
}
