package dev.gungang.acoustics.server;

import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Live weapon patching for administrators ({@code /gungang modify ui}). GunGang equipment fields are saved to the
 * weapon JSON and re-applied to players using that weapon; TaCZ gun stats are written into the gun pack's data file
 * (values only, comments kept) and TaCZ re-reads its packs, which syncs the new numbers to every client.
 * Secondaries ({@code kind} "secondary") edit their weapon file and, for GunGang's built-in items, the item effect
 * settings (damage, range, duration ...); those are saved through the lobby-only settings reload.
 */
public final class WeaponPatch {
    /** Rows of the screen. {@code key} is {@code gg:<field>} or {@code tacz:<path>}. */
    public record Row(String key, String label, String group, boolean numeric, String value, String help) {
        public Row(String key, String label, String group, boolean numeric, String value) { this(key, label, group, numeric, value, null); }
        Row withHelp(String text) { return new Row(key, label, group, numeric, value, text); }
    }
    /** {@code weight} is the TaCZ gun weight in kg (null: not a TaCZ gun or its data file was not found); {@code kind}: "weapon" or "secondary". */
    public record Entry(String id, String name, String category, Double weight, double movementSpeed, String kind) { }
    /** {@code falloff}: the TaCZ gun's current damage_adjust bands {distance (-1: open-ended), damage}; null when not a TaCZ gun. */
    public record View(List<Entry> weapons, String id, String name, String gunId, String file, List<Row> rows, String message, String error, String phase, double weightMultiplier, String kind, List<double[]> falloff) {
        public View(List<Entry> weapons, String id, String name, String gunId, String file, List<Row> rows, String message, String error, String phase, double weightMultiplier, String kind) {
            this(weapons, id, name, gunId, file, rows, message, error, phase, weightMultiplier, kind, null);
        }
    }
    /** {@code kind} null means a primary weapon (older clients). */
    public record Request(String id, Map<String, String> changes, String kind) {
        public Request(String id, Map<String, String> changes) { this(id, changes, null); }
    }
    public static final String PRIMARY = "weapon", SECONDARY = "secondary", GENERAL = "general";
    /** The 전역 설정 tab's settings (item effect files). */
    private static final Map<String, String> GENERAL_SETTINGS = Map.of("health_regeneration", "체력 재생", "combat", "전투");
    private static final String GENERAL_CATEGORY = "전역";
    private static final Map<String, String> ELEMENT_LABELS = Map.of(
            "elements.burnDamage", "화상 피해 (1회당)", "elements.witherDamage", "위더 피해 (1회당)",
            "elements.poisonDamage", "독 피해 (1회당)", "elements.explosionMultiplier", "폭발 피해 배수 (이 무기 · 전역 배수에 곱함)");
    /** Secondary weapon-file fields shown, in order (stats.N.value rows follow, labelled by their stat name). */
    private static final List<String> SECONDARY_FIELDS = List.of("name", "category", "cooldownSeconds", "plantSeconds", "fuseSeconds", "defuseSeconds", "releaseDate", "description", "usage");
    /** Display stat rows of a secondary ({@code stats.N.name} / {@code stats.N.value}); blank rows after them add new ones. */
    private static final java.util.regex.Pattern STAT_KEY = java.util.regex.Pattern.compile("stats\\.(\\d+)\\.(name|value)");
    /** Empty stat rows offered after a secondary's own (to add display lines), and the most a weapon may have. */
    private static final int NEW_STATS = 2, MAX_STATS = 12;
    /** Max health and movement speed are not edited for secondaries: neither offered nor accepted. */
    private static final Set<String> SECONDARY_HIDDEN = Set.of("maxHealth", "movementSpeed");
    private static final Map<String, String> SECONDARY_LABELS = Map.of("cooldownSeconds", "쿨타임 (초)", "plantSeconds", "설치 시간 (초)", "fuseSeconds", "폭발 시간 (초)", "defuseSeconds", "해체 시간 (초)");
    /** Item effect setting labels; anything not listed shows its field name. */
    private static final Map<String, String> ITEM_LABELS = Map.ofEntries(
            Map.entry("damage", "피해"), Map.entry("radius", "반경 (블록)"), Map.entry("range", "범위 (블록)"), Map.entry("durationSeconds", "지속 시간 (초)"),
            Map.entry("cooldownSeconds", "쿨타임 (초)"), Map.entry("health", "체력"), Map.entry("heal", "회복량"), Map.entry("speed", "속도"),
            Map.entry("capacity", "용량·탄약"), Map.entry("projectileRange", "투사체 사거리 (블록)"), Map.entry("projectileSpeed", "투사체 속도"),
            Map.entry("throwSpeed", "투척 속도"), Map.entry("throwDamage", "투척 피해"), Map.entry("emissionIntervalTicks", "발사 간격 (틱)"),
            Map.entry("burnSeconds", "화상 시간 (초)"), Map.entry("burnDamage", "화상 피해"), Map.entry("knockback", "넉백"),
            Map.entry("detectionRange", "감지 반경 (블록)"), Map.entry("fireIntervalTicks", "사격 간격 (틱)"), Map.entry("reloadPerSecond", "초당 재장전"),
            Map.entry("absorption", "추가 체력"), Map.entry("triggerRadius", "작동 반경 (블록)"), Map.entry("delaySeconds", "지연 (초)"),
            Map.entry("centerDamage", "중심 피해"), Map.entry("edgeDamage", "가장자리 피해"), Map.entry("collisionDamage", "충돌 피해"),
            Map.entry("maxPlaced", "최대 설치 수"), Map.entry("dashDistance", "대시 이동 거리 (블록)"), Map.entry("energyCost", "대시 에너지 소모"),
            Map.entry("rightClickDamage", "우클릭 피해"), Map.entry("percentPerSecond", "초당 회복 (최대 체력 %)"), Map.entry("bleedDamagePerSecond", "출혈 초당 피해"), Map.entry("bleedSeconds", "출혈 시간 (초)"), Map.entry("rootSeconds", "속박 시간 (초)"), Map.entry("slowSeconds", "둔화 시간 (초)"),
            Map.entry("explosionPlayerMultiplier", "폭발 피해 배수 (플레이어·봇 대상)"), Map.entry("startDamage", "첫 피해"), Map.entry("damageStep", "콤보마다 증가"),
            Map.entry("maxDamage", "최대 피해"), Map.entry("intervalSeconds", "간격 (초)"), Map.entry("motion1Damage", "공격 모션 1 피해"),
            Map.entry("motion2Damage", "공격 모션 2 피해"), Map.entry("motion3Damage", "공격 모션 3 피해"), Map.entry("motion4Damage", "공격 모션 4 피해"),
            Map.entry("motion5Damage", "공격 모션 5 피해"), Map.entry("motion6Damage", "공격 모션 6 피해"), Map.entry("slashAngle", "베기 각도 (°)"),
            Map.entry("thrustAngle", "찌르기 각도 (모션 6, °)"), Map.entry("guardMovementSpeed", "방어 중 이동 속도 (%)"),
            Map.entry("attackIntervalSeconds", "공격 간격 (초)"), Map.entry("arcDamage", "전격 피해 (주변)"),
            Map.entry("arcRadius", "전격 반경 (블록)"), Map.entry("glowSeconds", "발광 시간 (초)"),
            Map.entry("tier1Damage", "콤보 1단 피해"), Map.entry("tier2Damage", "콤보 2단 피해"), Map.entry("tier3Damage", "콤보 3단 피해"),
            Map.entry("tier4Damage", "콤보 4단 피해"), Map.entry("tier5Damage", "콤보 5단 피해 (최대)"),
            Map.entry("guardReductionPercent", "방어 피해 경감 (%)"), Map.entry("guardAngle", "방어 각도 (정면, °)"));
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("name", "이름"), Map.entry("category", "분류"), Map.entry("maxHealth", "최대 체력"), Map.entry("movementSpeed", "이동 속도 (%)"),
            Map.entry("description", "표시 · 설명 (줄바꿈 \\n)"), Map.entry("usage", "표시 · 사용방법 (줄바꿈 \\n)"), Map.entry("releaseDate", "출시일 (yyyy-MM-dd)"),
            Map.entry("specs.damage", "표시 · 피해"), Map.entry("specs.accuracy", "표시 · 정확도"), Map.entry("specs.stability", "표시 · 안정성"),
            Map.entry("specs.range", "표시 · 사거리"), Map.entry("specs.fireRate", "표시 · 연사력"));
    /** Multi-line text fields: one-line edit boxes carry each line break as the two characters \n (a literal \n as \\n). */
    private static final Set<String> MULTILINE = Set.of("description", "usage");
    static String escapeLines(String text) { return text.replace("\\n", "\\\\n").replace("\n", "\\n"); }
    static String unescapeLines(String text) {
        var out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 2 < text.length() && text.charAt(i + 1) == '\\' && text.charAt(i + 2) == 'n') { out.append("\\n"); i += 2; }
            else if (c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == 'n') { out.append('\n'); i++; }
            else out.append(c);
        }
        return out.toString();
    }
    private static final Map<UUID, Long> LAST_APPLY = new HashMap<>();
    private static final long COOLDOWN_MS = 1500;

    public static void open(ServerPlayer player, String id) throws Exception { open(player, PRIMARY, id); }
    public static void open(ServerPlayer player, String kind, String id) throws Exception {
        requireAdmin(player);
        if (!GungangNetwork.installed(player)) throw new IllegalArgumentException("패치 화면은 GunGang 모드가 설치된 클라이언트에서만 열 수 있습니다.");
        send(player, kindOf(kind), id, "", "");
    }
    private static String kindOf(String kind) { return SECONDARY.equals(kind) ? SECONDARY : GENERAL.equals(kind) ? GENERAL : PRIMARY; }
    /** Server-thread handler for the screen's requests: {@code open} (select a weapon) or {@code apply}. */
    public static void receive(ServerPlayer player, String action, String json) {
        if (player == null) return;
        String id = "", kind = PRIMARY;
        try {
            requireAdmin(player);
            var request = ServerFiles.GSON.fromJson(json, Request.class);
            if (request == null || request.id() == null) throw new IllegalArgumentException("잘못된 요청입니다.");
            id = request.id();
            kind = kindOf(request.kind());
            switch (action) {
                case "open" -> send(player, kind, id, "", "");
                case "apply" -> {
                    long now = System.currentTimeMillis(), last = LAST_APPLY.getOrDefault(player.getUUID(), 0L);
                    if (now - last < COOLDOWN_MS) throw new IllegalStateException("잠시 후 다시 적용하세요.");
                    LAST_APPLY.put(player.getUUID(), now);
                    var changes = request.changes() == null ? Map.<String, String>of() : request.changes();
                    send(player, kind, id, kind.equals(SECONDARY) ? applySecondary(player, id, changes) : kind.equals(GENERAL) ? applyGeneral(player, id, changes) : apply(player, id, changes), "");
                }
                default -> throw new IllegalArgumentException("알 수 없는 요청입니다.");
            }
        } catch (Exception failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            try { send(player, kind, id, "", message); }
            catch (Exception again) { player.sendSystemMessage(Component.literal("무기 패치 실패: " + message)); }
        }
    }
    private static void requireAdmin(ServerPlayer player) {
        if (!player.hasPermissions(2)) throw new IllegalStateException("무기 패치는 OP 2 이상만 사용할 수 있습니다.");
    }
    private static Optional<Loadouts.Weapon> weapon(List<Loadouts.Weapon> weapons, String id) {
        return weapons.stream().filter(w -> w.id().equals(id)).findFirst();
    }
    static String gunId(Loadouts.Weapon weapon) {
        try {
            var stack = weapon.stack();
            return stack.hasTag() && stack.getTag().contains("GunId") ? stack.getTag().getString("GunId") : "";
        } catch (RuntimeException invalid) { return ""; }
    }
    /** The lrtactical melee weapon id of a weapon ("katana:katanagai"), or "". */
    static String meleeId(Loadouts.Weapon weapon) {
        try {
            var stack = weapon.stack();
            return stack.hasTag() && stack.getTag().contains("MeleeWeaponId") ? stack.getTag().getString("MeleeWeaponId") : "";
        } catch (RuntimeException invalid) { return ""; }
    }
    private static Path packs() { return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("tacz").toAbsolutePath().normalize(); }

    private static void send(ServerPlayer player, String kind, String requested, String message, String error) throws Exception {
        var loadouts = Loadouts.load();
        var entries = new ArrayList<Entry>();
        for (var w : loadouts.primary()) entries.add(new Entry(w.id(), w.name(), w.category(), weight(w), w.movementSpeed(), PRIMARY));
        for (var w : loadouts.secondary()) entries.add(new Entry(w.id(), w.name(), w.category(), weight(w), w.movementSpeed(), SECONDARY));
        for (var setting : new TreeMap<>(GENERAL_SETTINGS).entrySet()) entries.add(new Entry(setting.getKey(), setting.getValue(), GENERAL_CATEGORY, null, 100, GENERAL));
        if (kind.equals(GENERAL)) {
            String id = GENERAL_SETTINGS.containsKey(requested) ? requested : new TreeMap<>(GENERAL_SETTINGS).firstKey();
            var rows = new ArrayList<Row>();
            for (var field : ConfigModify.editable("item", id).entrySet())
                rows.add(new Row("item:" + field.getKey(), ITEM_LABELS.getOrDefault(field.getKey(), field.getKey()), "general", ConfigModify.numeric("item", id, field.getKey()), number(text(field.getValue()))));
            rows.replaceAll(row -> row.withHelp(PatchHelp.of(row.key(), id)));
            var view = new View(entries, id, GENERAL_SETTINGS.get(id), "", "", rows, message, error, LobbyManager.phase(), weightMultiplier(), kind);
            GungangNetwork.send(player, new GungangNetwork.WeaponPatchView(ServerFiles.GSON.toJson(view)));
            return;
        }
        var weapons = kind.equals(SECONDARY) ? loadouts.secondary() : loadouts.primary();
        if (weapons.isEmpty()) throw new IllegalStateException(kind.equals(SECONDARY) ? "등록된 보조무기가 없습니다." : "등록된 주무기가 없습니다.");
        String id = weapon(weapons, requested).map(Loadouts.Weapon::id).orElse(weapons.get(0).id());
        var weapon = weapon(weapons, id).orElseThrow();
        var rows = new ArrayList<Row>();
        if (kind.equals(SECONDARY)) secondaryRows(id, weapon, rows);
        else {
            var fields = ConfigModify.weaponFields(id);
            for (var field : fields.entrySet()) {
                String key = field.getKey();
                if (key.matches("stats\\.\\d+\\.name")) continue;
                String text = text(field.getValue());
                String label = key.matches("stats\\.\\d+\\.value") ? "표시 · " + text(fields.get(key.replace(".value", ".name")))
                        : LABELS.getOrDefault(key, ELEMENT_LABELS.getOrDefault(key, key));
                boolean display = key.startsWith("specs.") || key.startsWith("stats.") || MULTILINE.contains(key);
                rows.add(new Row("gg:" + key, label, display ? "display" : key.startsWith("elements.") ? "element" : "gear",
                        ConfigModify.numeric("weapon", id, key), MULTILINE.contains(key) ? escapeLines(text) : number(text)));
            }
        }
        if (kind.equals(PRIMARY)) itemRows(weapon, rows);
        String gun = gunId(weapon), file = "";
        List<double[]> falloff = null;
        if (!gun.isEmpty()) {
            try {
                var data = TaczGunFile.find(gun);
                file = packs().relativize(data.path()).toString().replace('\\', '/');
                for (var field : data.editable().values())
                    rows.add(new Row("tacz:" + field.path(), field.label(), "tacz", true, data.current(field.path())));
                if (data.current("bullet.damage") != null) falloff = data.falloff();
            } catch (IllegalArgumentException missing) { file = "! " + missing.getMessage(); }
        }
        String melee = meleeId(weapon);
        if (!melee.isEmpty()) {
            try {
                var data = TaczGunFile.findMelee(melee);
                file = packs().relativize(data.path()).toString().replace('\\', '/');
                for (var field : data.editable().values())
                    rows.add(new Row("melee:" + field.path(), field.label(), "melee", true, data.values().get(field.path()).text()));
            } catch (IllegalArgumentException missing) { file = "! " + missing.getMessage(); }
        }
        speedFirst(rows);
        String setting = itemSetting(weapon);
        rows.replaceAll(row -> row.withHelp(PatchHelp.of(row.key(), setting)));
        var view = new View(entries, id, weapon.name(), gun, file, rows, message, error, LobbyManager.phase(), weightMultiplier(), kind, falloff);
        GungangNetwork.send(player, new GungangNetwork.WeaponPatchView(ServerFiles.GSON.toJson(view)));
    }
    /** Rows that set how fast the weapon attacks: TaCZ fire rate, melee pack cooldowns, GunGang item attack intervals. */
    static boolean attackSpeed(String key) {
        return key.equals("tacz:rpm") || key.matches("melee:data\\.attack\\.attack_(left|right)\\.cooldown")
                || key.startsWith("item:") && ConfigModify.ATTACK_SPEED_ITEM_FIELDS.contains(key.substring(5));
    }
    /** Moves the attack speed rows to the top, in their own group (all of them apply live, even mid-match). */
    private static void speedFirst(List<Row> rows) {
        var speed = new ArrayList<Row>();
        for (var it = rows.iterator(); it.hasNext(); ) {
            var row = it.next();
            if (attackSpeed(row.key())) { speed.add(new Row(row.key(), row.label(), "speed", row.numeric(), row.value(), row.help())); it.remove(); }
        }
        rows.addAll(0, speed);
    }
    private static String text(com.google.gson.JsonElement value) {
        return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
    }
    /** The GunGang item effect setting behind a secondary ("turret" for gungang:turret), or null. */
    static String itemSetting(Loadouts.Weapon weapon) {
        try {
            var stack = weapon.stack();
            if (KatanaSpin.katana(stack)) return "katana";
            if (TwinBlades.is(stack)) return "twin_blades";
            if (Fallout.is(stack)) return "fallout";
            if (ArcThrower.is(stack)) return "arc_thrower";
            var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(weapon.stack().getItem());
            return key != null && key.getNamespace().equals("gungang") && ConfigModify.exists("item", key.getPath()) ? key.getPath() : null;
        } catch (Exception invalid) { return null; }
    }
    /** Weapon-file fields, then the item effect settings (when it is a GunGang item). TaCZ rows are added by the caller. */
    private static void secondaryRows(String id, Loadouts.Weapon weapon, List<Row> rows) throws Exception {
        var fields = ConfigModify.editable(SECONDARY, id);
        for (String key : SECONDARY_FIELDS) if (fields.containsKey(key)) {
            boolean multiline = MULTILINE.contains(key);
            rows.add(new Row("gg:" + key, SECONDARY_LABELS.getOrDefault(key, LABELS.getOrDefault(key, key)), multiline ? "display" : "secondary",
                    ConfigModify.numeric(SECONDARY, id, key), multiline ? escapeLines(text(fields.get(key))) : number(text(fields.get(key)))));
        }
        // Display stats on the loadout card: name and value of each, then blank rows to add more (clear both to remove one).
        var stats = weapon.stats();
        for (int i = 0; i < Math.min(MAX_STATS, stats.size() + NEW_STATS); i++) {
            boolean exists = i < stats.size();
            String label = exists ? "표시 항목 " + (i + 1) : "새 표시 항목";
            rows.add(new Row("gg:stats." + i + ".name", label + " · 이름 (이름·값 비우면 삭제)", "display", false, exists ? stats.get(i).name() : ""));
            rows.add(new Row("gg:stats." + i + ".value", label + " · 값", "display", false, exists ? stats.get(i).value() : ""));
        }
        for (String key : ConfigModify.ELEMENT_FIELDS) if (fields.containsKey(key) && !fields.get(key).isJsonNull())
            rows.add(new Row("gg:" + key, ELEMENT_LABELS.get(key), "element", true, number(text(fields.get(key)))));
        itemRows(weapon, rows);
    }
    /** Item effect settings of a GunGang item (rapier, turret ...); nothing for other items. */
    private static void itemRows(Loadouts.Weapon weapon, List<Row> rows) throws Exception {
        String item = itemSetting(weapon);
        if (item == null) return;
        for (var field : ConfigModify.editable("item", item).entrySet())
            rows.add(new Row("item:" + field.getKey(), ITEM_LABELS.getOrDefault(field.getKey(), field.getKey()), "item", ConfigModify.numeric("item", item, field.getKey()), number(text(field.getValue()))));
    }
    /** Secondary changes: weapon file and item effect settings (lobby only, like /gungang modify), then TaCZ data. */
    private static String applySecondary(ServerPlayer player, String id, Map<String, String> changes) throws Exception {
        if (changes.isEmpty()) throw new IllegalArgumentException("변경한 항목이 없습니다.");
        if (changes.size() > 128) throw new IllegalArgumentException("항목이 너무 많습니다.");
        var weapon = weapon(Loadouts.load().secondary(), id).orElseThrow(() -> new IllegalArgumentException("해당 보조무기가 없습니다: " + id));
        var gear = new LinkedHashMap<String, String>();
        var item = new LinkedHashMap<String, String>();
        var tacz = new LinkedHashMap<String, String>();
        var melee = new LinkedHashMap<String, String>();
        String falloff = null;
        for (var change : changes.entrySet()) {
            String key = change.getKey(), value = change.getValue() == null ? "" : change.getValue();
            boolean multiline = key.startsWith("gg:") && MULTILINE.contains(key.substring(3));
            if (value.length() > (key.equals(FALLOFF) || multiline ? 1000 : 200)) throw new IllegalArgumentException("값이 너무 깁니다: " + key);
            if (key.startsWith("gg:")) gear.put(key.substring(3), multiline ? unescapeLines(value) : value);
            else if (key.startsWith("item:")) item.put(key.substring(5), value);
            else if (key.equals(FALLOFF)) falloff = value;
            else if (key.startsWith("tacz:")) tacz.put(key.substring(5), value);
            else if (key.startsWith("melee:")) melee.put(key.substring(6), value);
            else throw new IllegalArgumentException("알 수 없는 항목: " + key);
        }
        // Attack speed item fields alone are live; everything else in the secondary files waits for the lobby.
        if (!gear.isEmpty() || !item.isEmpty() && !ConfigModify.attackSpeedOnly("item", item)) LobbyManager.requireMapEditing();
        var fields = ConfigModify.editable(SECONDARY, id);
        var stats = stats(weapon, gear);
        for (String field : gear.keySet()) if (!fields.containsKey(field) || SECONDARY_HIDDEN.contains(field)) throw new IllegalArgumentException("수정할 수 없는 항목: " + field);
        String setting = itemSetting(weapon);
        if (!item.isEmpty() && setting == null) throw new IllegalArgumentException("이 보조무기에는 아이템 효과 설정이 없습니다.");
        TaczGunFile data = null;
        String patched = null;
        if (!tacz.isEmpty() || falloff != null) {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("tacz")) throw new IllegalStateException("서버에 TaCZ가 설치되어 있지 않습니다.");
            data = TaczGunFile.find(gunId(weapon));
            patched = tacz.isEmpty() ? data.source() : data.patched(tacz); // throws before anything is saved
            if (falloff != null) patched = withFalloff(data, patched, falloff);
        }
        TaczGunFile meleeData = null;
        String meleePatched = null;
        if (!melee.isEmpty()) {
            meleeData = TaczGunFile.findMelee(meleeId(weapon));
            meleePatched = meleeData.patched(melee); // throws before anything is saved
        }
        var done = new ArrayList<String>();
        if (!gear.isEmpty() || stats != null) {
            ConfigModify.apply(SECONDARY, id, gear, stats == null ? Map.of() : Map.of("stats", stats));
            done.add("장비 " + gear.size() + "개" + (stats != null ? " · 표시 항목 " + stats.size() + "줄" : ""));
        }
        if (!item.isEmpty()) { ConfigModify.apply("item", setting, item); done.add("아이템 효과 " + item.size() + "개"); }
        if (data != null) { writeTacz(player, data, patched); done.add("TaCZ " + tacz.size() + "개" + (falloff != null ? " · 거리별 피해 자동 채움" : "")); }
        if (meleeData != null) { writeTacz(player, meleeData, meleePatched); done.add("근접 " + melee.size() + "개"); }
        LogUtils.getLogger().info("GunGang secondary patch by {}: {} {}", player.getGameProfile().getName(), id, changes);
        return "패치 완료: " + weapon.name() + " · " + String.join(", ", done) + " · 다음 지급·장비 선택부터 적용됩니다.";
    }
    /**
     * Takes the {@code stats.N.name/value} edits out of {@code gear} and returns the secondary's whole new stats list
     * (null when none was edited). A row whose name and value are both blank is dropped; a half-filled row is refused.
     */
    private static com.google.gson.JsonArray stats(Loadouts.Weapon weapon, Map<String, String> gear) {
        var names = new TreeMap<Integer, String>(); var values = new TreeMap<Integer, String>();
        for (var it = gear.entrySet().iterator(); it.hasNext(); ) {
            var change = it.next(); var match = STAT_KEY.matcher(change.getKey());
            if (!match.matches()) continue;
            int index = Integer.parseInt(match.group(1));
            if (index >= MAX_STATS) throw new IllegalArgumentException("표시 항목은 최대 " + MAX_STATS + "개입니다.");
            (match.group(2).equals("name") ? names : values).put(index, change.getValue().strip());
            it.remove();
        }
        if (names.isEmpty() && values.isEmpty()) return null;
        var current = weapon.stats();
        int size = Math.max(current.size(), Math.max(names.isEmpty() ? 0 : names.lastKey() + 1, values.isEmpty() ? 0 : values.lastKey() + 1));
        var out = new com.google.gson.JsonArray();
        for (int i = 0; i < size; i++) {
            String name = names.getOrDefault(i, i < current.size() ? current.get(i).name() : "");
            String value = values.getOrDefault(i, i < current.size() ? current.get(i).value() : "");
            if (name.isEmpty() && value.isEmpty()) continue;
            if (name.isEmpty() || value.isEmpty()) throw new IllegalArgumentException("표시 항목 " + (i + 1) + ": 이름과 값을 모두 입력하세요 (둘 다 비우면 삭제).");
            if (name.length() > 24 || value.length() > 40) throw new IllegalArgumentException("표시 항목 " + (i + 1) + ": 이름은 24자, 값은 40자 이하여야 합니다.");
            var row = new com.google.gson.JsonObject(); row.addProperty("name", name); row.addProperty("value", value); out.add(row);
        }
        return out;
    }
    /** 전역 설정 tab: item effect settings such as health_regeneration (lobby only, like /gungang modify item). */
    private static String applyGeneral(ServerPlayer player, String id, Map<String, String> changes) throws Exception {
        if (!GENERAL_SETTINGS.containsKey(id)) throw new IllegalArgumentException("해당 설정이 없습니다: " + id);
        if (changes.isEmpty()) throw new IllegalArgumentException("변경한 항목이 없습니다.");
        var item = new LinkedHashMap<String, String>();
        for (var change : changes.entrySet()) {
            String key = change.getKey(), value = change.getValue() == null ? "" : change.getValue();
            if (!key.startsWith("item:") || value.length() > 200) throw new IllegalArgumentException("알 수 없는 항목: " + key);
            item.put(key.substring(5), value);
        }
        ConfigModify.apply("item", id, item);
        LogUtils.getLogger().info("GunGang general setting patch by {}: {} {}", player.getGameProfile().getName(), id, changes);
        return "전역 설정 저장: " + GENERAL_SETTINGS.get(id) + " · " + item.size() + "개 항목";
    }
    /** The weapon's TaCZ weight; a data file without the field weighs 0 kg, as TaCZ reads it. */
    private static Double weight(Loadouts.Weapon weapon) {
        String gun = gunId(weapon);
        if (gun.isEmpty()) return null;
        try {
            var value = TaczGunFile.find(gun).values().get("weight");
            return value == null || !value.number() ? 0d : Double.parseDouble(value.text());
        } catch (Exception missing) { return null; }
    }
    /** TaCZ's speed loss per kg (server config WeightSpeedMultiplier, default 0.015; negative disables it). */
    static double weightMultiplier() {
        try {
            var config = Class.forName("com.tacz.guns.config.sync.SyncConfig").getField("WEIGHT_SPEED_MULTIPLIER").get(null);
            double value = ((net.minecraftforge.common.ForgeConfigSpec.DoubleValue) config).get();
            return value > 0 ? value : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return 0.015; }
    }
    private static String number(String text) {
        return text.matches("-?[0-9]+\\.0") ? text.substring(0, text.length() - 2) : text;
    }

    /** Validates everything first, then saves GunGang fields and the TaCZ file; returns the status line. */
    private static String apply(ServerPlayer player, String id, Map<String, String> changes) throws Exception {
        if (changes.isEmpty()) throw new IllegalArgumentException("변경한 항목이 없습니다.");
        if (changes.size() > 128) throw new IllegalArgumentException("항목이 너무 많습니다.");
        var weapon = weapon(Loadouts.load().primary(), id).orElseThrow(() -> new IllegalArgumentException("해당 무기가 없습니다: " + id));
        var gear = new LinkedHashMap<String, String>();
        var item = new LinkedHashMap<String, String>();
        var tacz = new LinkedHashMap<String, String>();
        var melee = new LinkedHashMap<String, String>();
        String falloff = null;
        for (var change : changes.entrySet()) {
            String key = change.getKey(), value = change.getValue() == null ? "" : change.getValue();
            boolean multiline = key.startsWith("gg:") && MULTILINE.contains(key.substring(3));
            if (value.length() > (key.equals(FALLOFF) || multiline ? 1000 : 200)) throw new IllegalArgumentException("값이 너무 깁니다: " + key);
            if (key.startsWith("gg:")) gear.put(key.substring(3), multiline ? unescapeLines(value) : value);
            else if (key.startsWith("item:")) item.put(key.substring(5), value);
            else if (key.equals(FALLOFF)) falloff = value;
            else if (key.startsWith("tacz:")) tacz.put(key.substring(5), value);
            else if (key.startsWith("melee:")) melee.put(key.substring(6), value);
            else throw new IllegalArgumentException("알 수 없는 항목: " + key);
        }
        for (String field : gear.keySet())
            if (!ConfigModify.weaponFields(id).containsKey(field)) throw new IllegalArgumentException("수정할 수 없는 항목: " + field);
        String setting = itemSetting(weapon);
        if (!item.isEmpty() && setting == null) throw new IllegalArgumentException("이 무기에는 아이템 효과 설정이 없습니다.");
        if (!item.isEmpty() && !ConfigModify.attackSpeedOnly("item", item)) LobbyManager.requireMapEditing();
        TaczGunFile data = null;
        String patched = null;
        if (!tacz.isEmpty() || falloff != null) {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("tacz")) throw new IllegalStateException("서버에 TaCZ가 설치되어 있지 않습니다.");
            data = TaczGunFile.find(gunId(weapon));
            patched = tacz.isEmpty() ? data.source() : data.patched(tacz); // throws before anything is saved
            if (falloff != null) patched = withFalloff(data, patched, falloff);
        }
        TaczGunFile meleeData = null;
        String meleePatched = null;
        if (!melee.isEmpty()) {
            meleeData = TaczGunFile.findMelee(meleeId(weapon));
            meleePatched = meleeData.patched(melee); // throws before anything is saved
        }
        var done = new ArrayList<String>();
        if (!gear.isEmpty()) {
            ConfigModify.apply("weapon", id, gear);
            done.add("장비 " + gear.size() + "개");
        }
        if (!item.isEmpty()) {
            ConfigModify.apply("item", setting, item);
            done.add("아이템 효과 " + item.size() + "개");
        }
        if (data != null) {
            writeTacz(player, data, patched);
            done.add("TaCZ " + tacz.size() + "개" + (falloff != null ? " · 거리별 피해 자동 채움" : ""));
        }
        if (meleeData != null) {
            writeTacz(player, meleeData, meleePatched);
            done.add("근접 " + melee.size() + "개");
        }
        LogUtils.getLogger().info("GunGang weapon patch by {}: {} {}", player.getGameProfile().getName(), id, changes);
        String summary = "실시간 패치 완료: " + weapon.name() + " · " + String.join(", ", done);
        if (data != null || meleeData != null) summary += " · 총기팩을 다시 읽어 접속자에게 동기화했습니다";
        return summary + ".";
    }
    /** Change key carrying a {@link DamageFalloff} as JSON: regenerates the gun's damage_adjust list. */
    public static final String FALLOFF = "falloff";
    /** Applies a falloff curve on top of the other TaCZ edits, using the (possibly just changed) base damage. */
    private static String withFalloff(TaczGunFile data, String patched, String json) {
        DamageFalloff curve;
        try { curve = ServerFiles.GSON.fromJson(json, DamageFalloff.class); }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("거리별 피해 설정을 읽을 수 없습니다."); }
        if (curve == null) throw new IllegalArgumentException("거리별 피해 설정이 비어 있습니다.");
        var edited = TaczGunFile.parse(data.path(), patched);
        String base = edited.current("bullet.damage");
        if (base == null || !new TaczGunFile.Value("", base, 0, 0).number()) throw new IllegalArgumentException("기본 피해(bullet.damage)가 없는 총기입니다.");
        return edited.withFalloff(curve.bands(Double.parseDouble(base)));
    }
    /** Backs the original pack file up once, writes the patch, and has TaCZ re-read (and re-sync) its packs. */
    private static void writeTacz(ServerPlayer player, TaczGunFile data, String patched) throws Exception {
        Path relative = packs().relativize(data.path());
        Path backup = ServerFiles.root().resolve("backups/tacz").resolve(relative.toString()).toAbsolutePath().normalize();
        if (!Files.exists(backup)) { Files.createDirectories(backup.getParent()); Files.copy(data.path(), backup); }
        AreaEditor.atomicWrite(data.path(), patched);
        var server = player.server;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "tacz reload");
    }
    private WeaponPatch() { }
}
