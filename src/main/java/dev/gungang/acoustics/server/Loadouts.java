package dev.gungang.acoustics.server;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

public record Loadouts(List<Weapon> primary, List<Weapon> secondary, Weapon slot9) {
    public record Specs(String damage, String accuracy, String stability, String range, String fireRate) { }
    public record Weapon(String id, String itemNbt, String name, String description, Specs specs, String usage, String category) {
        public Weapon {
            category = category == null || category.isBlank() ? "미분류" : category.strip();
        }
        public Weapon(String id, String itemNbt, String name, String description, Specs specs, String usage) {
            this(id,itemNbt,name,description,specs,usage,"미분류");
        }
        public ItemStack stack() {
            try {
                ItemStack stack = ItemStack.of(TagParser.parseTag(itemNbt));
                if (stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Invalid item stack: " + id);
                stack.setHoverName(Component.literal(name));
                return stack;
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                throw new IllegalArgumentException("Invalid SNBT for " + id, failure);
            }
        }
        public void validate(boolean requireSpecs) {
            if (id == null || !id.matches("[a-z0-9_-]{1,48}") || itemNbt == null || itemNbt.length() > 16384
                    || name == null || name.isBlank() || name.length() > 80 || description == null || description.length() > 1000
                    || usage == null || usage.length() > 1000 || category.length() > 32 || category.chars().anyMatch(Character::isISOControl)
                    || (requireSpecs && specs == null)) throw new IllegalArgumentException("Invalid weapon: " + id);
            if (specs != null) for (String value : new String[]{specs.damage, specs.accuracy, specs.stability, specs.range, specs.fireRate})
                if (value == null || value.length() > 80) throw new IllegalArgumentException("Invalid weapon specs: " + id);
            stack();
        }
        public Weapon view() { return new Weapon(id, "", name, description, specs, usage, category); }
    }
    public Loadouts view() { return new Loadouts(primary.stream().map(Weapon::view).toList(), secondary.stream().map(Weapon::view).toList(), slot9 == null ? null : slot9.view()); }
    public static Loadouts load() throws java.io.IOException {
        Path root = ServerFiles.root(), directory = root.resolve("server");
        Path marker = directory.resolve(".weapons-migrated");
        Files.createDirectories(directory.resolve("weapons"));
        Files.createDirectories(directory.resolve("sub_weapons"));
        if (!Files.exists(marker)) {
            var example = new Loadouts(List.of(new Weapon("bow", "{id:\"minecraft:bow\",Count:1b}", "Training Bow", "Replace with the full TaCZ item SNBT.", new Specs("6", "Manual", "High", "30m", "Manual"), "Hold use to draw and release to fire.")),
                    List.of(new Weapon("sword", "{id:\"minecraft:iron_sword\",Count:1b}", "Backup Sword", "Example secondary item", null, "Attack with the configured offhand key.")),
                    new Weapon("arrows", "{id:\"minecraft:arrow\",Count:64b}", "Arrows", "Default hotbar slot 9 item", null, "Ammunition for the example bow."));
            Path legacy = root.resolve("loadouts.json");
            Loadouts initial = Files.exists(legacy) ? ServerFiles.GSON.fromJson(read(legacy, 2_000_000), Loadouts.class) : example;
            validate(initial);
            writeCategory(directory.resolve("weapons"), initial.primary);
            writeCategory(directory.resolve("sub_weapons"), initial.secondary);
            ConfigLayout.writeNew(directory.resolve("slot9.json"), ServerFiles.GSON.toJson(initial.slot9));
            ConfigLayout.writeNew(marker, "Migrated; loadouts.json is no longer loaded.\n");
        }
        Path slot9 = directory.resolve("slot9.json");
        Loadouts result = new Loadouts(readCategory(directory.resolve("weapons")), readCategory(directory.resolve("sub_weapons")),
                Files.exists(slot9) ? ServerFiles.GSON.fromJson(read(slot9, 65536), Weapon.class) : null);
        validate(result);
        return result;
    }
    private static String read(Path path, int limit) throws java.io.IOException {
        if (Files.size(path) > limit) throw new java.io.IOException("Config is too large: " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
    private static List<Weapon> readCategory(Path directory) throws java.io.IOException {
        try (var files = Files.list(directory)) {
            var paths = files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json")).sorted().toList();
            if (paths.isEmpty() || paths.size() > 64) throw new java.io.IOException("Configure 1–64 weapon JSON files: " + directory);
            List<Weapon> weapons = new ArrayList<>();
            for (Path file : paths) {
                Weapon weapon = ServerFiles.GSON.fromJson(read(file,65536), Weapon.class);
                if (weapon == null) throw new java.io.IOException("Empty weapon: " + file);
                weapons.add(weapon);
            }
            return List.copyOf(weapons);
        }
    }
    private static void writeCategory(Path directory, List<Weapon> weapons) throws java.io.IOException {
        // Existing custom categories are authoritative; never mix in defaults or stale legacy weapons.
        try (var files = Files.list(directory)) { if (files.anyMatch(p -> p.getFileName().toString().endsWith(".json"))) return; }
        for (int i=0;i<weapons.size();i++) {
            Weapon weapon=weapons.get(i);
            ConfigLayout.writeNew(directory.resolve(String.format(Locale.ROOT,"%02d_%s.json",i+1,weapon.id)),ServerFiles.GSON.toJson(weapon));
        }
    }
    private static void validate(Loadouts result) {
        if (result == null || result.primary == null || result.secondary == null || result.primary.isEmpty() || result.secondary.isEmpty()
                || result.primary.size() > 64 || result.secondary.size() > 64) throw new IllegalArgumentException("Configure 1–64 weapons per category");
        for (List<Weapon> group : List.of(result.primary, result.secondary)) {
            Set<String> ids = new HashSet<>();
            for (Weapon weapon : group) {
                if (weapon == null) throw new IllegalArgumentException("Null weapon entry");
                weapon.validate(group == result.primary);
                if (!ids.add(weapon.id)) throw new IllegalArgumentException("Duplicate weapon id: " + weapon.id);
            }
        }
        if (result.slot9 != null) result.slot9.validate(false);
    }
}
