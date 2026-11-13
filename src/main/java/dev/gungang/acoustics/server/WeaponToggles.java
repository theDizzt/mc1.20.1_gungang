package dev.gungang.acoustics.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Primary / secondary weapons switched off from {@code /gungang modify ui} ({@code server/disabled_weapons.json}).
 * A disabled weapon keeps its file and stays editable, but leaves the lobby catalog: it cannot be chosen, picked at
 * random, saved to a preset or given to bots. A group never empties: when every weapon of a group is disabled the
 * whole group is offered.
 */
public final class WeaponToggles {
    record Data(List<String> primary, List<String> secondary) {
        Data { primary = primary == null ? List.of() : List.copyOf(new TreeSet<>(primary)); secondary = secondary == null ? List.of() : List.copyOf(new TreeSet<>(secondary)); }
    }
    private static Data data;
    static Path path() { return ServerFiles.root().resolve("server").resolve("disabled_weapons.json"); }
    /** Re-reads the file; a missing or broken file enables everything (and the broken one is reported). */
    static synchronized void load() {
        var file = path();
        try { data = Files.exists(file) ? ServerFiles.GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class) : null; }
        catch (Exception broken) { com.mojang.logging.LogUtils.getLogger().error("[GunGang] disabled_weapons.json could not be read; every weapon is enabled", broken); data = null; }
        if (data == null) data = new Data(null, null);
    }
    private static synchronized Data data() { if (data == null) load(); return data; }
    public static boolean disabled(boolean primary, String id) { return (primary ? data().primary() : data().secondary()).contains(id); }
    /** {@code all} without its disabled weapons. */
    public static Loadouts filter(Loadouts all) {
        if (all == null) return null;
        var d = data();
        if (d.primary().isEmpty() && d.secondary().isEmpty()) return all;
        return new Loadouts(keep(all.primary(), d.primary()), keep(all.secondary(), d.secondary()), all.slot9());
    }
    private static List<Loadouts.Weapon> keep(List<Loadouts.Weapon> weapons, List<String> off) {
        var kept = weapons.stream().filter(w -> !off.contains(w.id())).toList();
        return kept.isEmpty() ? weapons : kept;
    }
    /** Switches one weapon on or off and saves the file. Refuses to switch off the last enabled weapon of a group. */
    static synchronized void set(boolean primary, String id, boolean enabled, List<Loadouts.Weapon> group) throws Exception {
        if (group.stream().noneMatch(w -> w.id().equals(id))) throw new IllegalArgumentException("해당 무기가 없습니다: " + id);
        var d = data();
        var off = new TreeSet<>(primary ? d.primary() : d.secondary());
        if (enabled ? !off.remove(id) : !off.add(id)) return;
        if (!enabled && group.stream().allMatch(w -> off.contains(w.id())))
            throw new IllegalArgumentException((primary ? "주무기" : "보조무기") + "는 최소 1개 이상 사용 상태여야 합니다.");
        var next = primary ? new Data(new ArrayList<>(off), d.secondary()) : new Data(d.primary(), new ArrayList<>(off));
        Files.createDirectories(path().getParent());
        AreaEditor.atomicWrite(path(), ServerFiles.GSON.toJson(next) + "\n");
        data = next;
    }
    private WeaponToggles() { }
}
