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
 */
public final class WeaponPatch {
    /** Rows of the screen. {@code key} is {@code gg:<field>} or {@code tacz:<path>}. */
    public record Row(String key, String label, String group, boolean numeric, String value) { }
    /** {@code weight} is the TaCZ gun weight in kg (null: not a TaCZ gun or its data file was not found). */
    public record Entry(String id, String name, String category, Double weight, double movementSpeed) { }
    public record View(List<Entry> weapons, String id, String name, String gunId, String file, List<Row> rows, String message, String error, String phase, double weightMultiplier) { }
    public record Request(String id, Map<String, String> changes) { }
    private static final Map<String, String> LABELS = Map.of(
            "name", "이름", "category", "분류", "maxHealth", "최대 체력", "movementSpeed", "이동 속도 (%)",
            "specs.damage", "표시 · 피해", "specs.accuracy", "표시 · 정확도", "specs.stability", "표시 · 안정성", "specs.range", "표시 · 사거리", "specs.fireRate", "표시 · 연사력");
    private static final Map<UUID, Long> LAST_APPLY = new HashMap<>();
    private static final long COOLDOWN_MS = 1500;

    public static void open(ServerPlayer player, String id) throws Exception {
        requireAdmin(player);
        if (!GungangNetwork.installed(player)) throw new IllegalArgumentException("패치 화면은 GunGang 모드가 설치된 클라이언트에서만 열 수 있습니다.");
        send(player, id, "", "");
    }
    /** Server-thread handler for the screen's requests: {@code open} (select a weapon) or {@code apply}. */
    public static void receive(ServerPlayer player, String action, String json) {
        if (player == null) return;
        String id = "";
        try {
            requireAdmin(player);
            var request = ServerFiles.GSON.fromJson(json, Request.class);
            if (request == null || request.id() == null) throw new IllegalArgumentException("잘못된 요청입니다.");
            id = request.id();
            switch (action) {
                case "open" -> send(player, id, "", "");
                case "apply" -> {
                    long now = System.currentTimeMillis(), last = LAST_APPLY.getOrDefault(player.getUUID(), 0L);
                    if (now - last < COOLDOWN_MS) throw new IllegalStateException("잠시 후 다시 적용하세요.");
                    LAST_APPLY.put(player.getUUID(), now);
                    send(player, id, apply(player, id, request.changes() == null ? Map.of() : request.changes()), "");
                }
                default -> throw new IllegalArgumentException("알 수 없는 요청입니다.");
            }
        } catch (Exception failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            try { send(player, id, "", message); }
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
    private static Path packs() { return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("tacz").toAbsolutePath().normalize(); }

    private static void send(ServerPlayer player, String requested, String message, String error) throws Exception {
        var weapons = Loadouts.load().primary();
        var entries = weapons.stream().map(w -> new Entry(w.id(), w.name(), w.category(), weight(w), w.movementSpeed())).toList();
        String id = weapon(weapons, requested).map(Loadouts.Weapon::id).orElse(weapons.get(0).id());
        var weapon = weapon(weapons, id).orElseThrow();
        var rows = new ArrayList<Row>();
        for (var field : ConfigModify.weaponFields(id).entrySet()) {
            var value = field.getValue();
            String text = value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
            rows.add(new Row("gg:" + field.getKey(), LABELS.getOrDefault(field.getKey(), field.getKey()), field.getKey().startsWith("specs.") ? "display" : "gear",
                    ConfigModify.numeric("weapon", id, field.getKey()), number(text)));
        }
        String gun = gunId(weapon), file = "";
        if (!gun.isEmpty()) {
            try {
                var data = TaczGunFile.find(gun);
                file = packs().relativize(data.path()).toString().replace('\\', '/');
                for (var field : data.editable().values())
                    rows.add(new Row("tacz:" + field.path(), field.label(), "tacz", true, data.values().get(field.path()).text()));
            } catch (IllegalArgumentException missing) { file = "! " + missing.getMessage(); }
        }
        var view = new View(entries, id, weapon.name(), gun, file, rows, message, error, LobbyManager.phase(), weightMultiplier());
        GungangNetwork.send(player, new GungangNetwork.WeaponPatchView(ServerFiles.GSON.toJson(view)));
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
        var tacz = new LinkedHashMap<String, String>();
        for (var change : changes.entrySet()) {
            String key = change.getKey(), value = change.getValue() == null ? "" : change.getValue();
            if (value.length() > 200) throw new IllegalArgumentException("값이 너무 깁니다: " + key);
            if (key.startsWith("gg:")) gear.put(key.substring(3), value);
            else if (key.startsWith("tacz:")) tacz.put(key.substring(5), value);
            else throw new IllegalArgumentException("알 수 없는 항목: " + key);
        }
        for (String field : gear.keySet())
            if (!ConfigModify.weaponFields(id).containsKey(field)) throw new IllegalArgumentException("수정할 수 없는 항목: " + field);
        TaczGunFile data = null;
        String patched = null;
        if (!tacz.isEmpty()) {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("tacz")) throw new IllegalStateException("서버에 TaCZ가 설치되어 있지 않습니다.");
            data = TaczGunFile.find(gunId(weapon));
            patched = data.patched(tacz); // throws before anything is saved
        }
        var done = new ArrayList<String>();
        if (!gear.isEmpty()) {
            ConfigModify.apply("weapon", id, gear);
            done.add("장비 " + gear.size() + "개");
        }
        if (data != null) {
            Path relative = packs().relativize(data.path());
            Path backup = ServerFiles.root().resolve("backups/tacz").resolve(relative.toString()).toAbsolutePath().normalize();
            if (!Files.exists(backup)) { Files.createDirectories(backup.getParent()); Files.copy(data.path(), backup); }
            AreaEditor.atomicWrite(data.path(), patched);
            var server = player.server;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "tacz reload");
            done.add("TaCZ " + tacz.size() + "개");
        }
        LogUtils.getLogger().info("GunGang weapon patch by {}: {} {}", player.getGameProfile().getName(), id, changes);
        String summary = "실시간 패치 완료: " + weapon.name() + " · " + String.join(", ", done);
        if (data != null) summary += " · 총기팩을 다시 읽어 접속자에게 동기화했습니다";
        return summary + ".";
    }
    private WeaponPatch() { }
}
