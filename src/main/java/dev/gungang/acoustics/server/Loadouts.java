package dev.gungang.acoustics.server;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

public record Loadouts(List<Weapon> primary, List<Weapon> secondary, Weapon slot9) {
    public record Specs(String damage, String accuracy, String stability, String range, String fireRate) { }
    /** Free-form spec row for the loadout screen, e.g. {"name":"속박 시간","value":"3초"}. */
    public record Stat(String name, String value) { }
    public record Weapon(String id, @com.google.gson.annotations.SerializedName(value="itemNbt",alternate={"nbt"}) String itemNbt, String name, String description, Specs specs, String usage, String category,
                         Double maxHealth, Double movementSpeed, Double cooldownSeconds, String command, boolean jetpackVerticalFlight, String releaseDate, List<Stat> stats) {
        public Weapon {
            stats=stats==null?List.of():List.copyOf(stats);
            releaseDate=ArenaMap.releaseDate(releaseDate,id);
            itemNbt=itemNbt==null?"":itemNbt;
            command=command==null?"":command.strip();
            if(command.startsWith("/"))command=command.substring(1);
            if(command.length()>4096||command.contains("\n")||command.contains("\r"))throw new IllegalArgumentException("Invalid weapon command");
            category = category == null || category.isBlank() ? "미분류" : category.strip();
            maxHealth=maxHealth==null?100:maxHealth;
            movementSpeed=movementSpeed==null?100:movementSpeed;
            cooldownSeconds=cooldownSeconds==null?0:cooldownSeconds;
            if(!Double.isFinite(maxHealth)||maxHealth<1||maxHealth>1024||!Double.isFinite(movementSpeed)||movementSpeed<1||movementSpeed>1000
                    ||!Double.isFinite(cooldownSeconds)||cooldownSeconds<0||cooldownSeconds>3600) throw new IllegalArgumentException("Invalid weapon health, speed or cooldown: "+id);
        }
        public Weapon(String id,String itemNbt,String name,String description,Specs specs,String usage,String category,Double maxHealth,Double movementSpeed,Double cooldownSeconds,String command,boolean jetpackVerticalFlight,String releaseDate) {
            this(id,itemNbt,name,description,specs,usage,category,maxHealth,movementSpeed,cooldownSeconds,command,jetpackVerticalFlight,releaseDate,null);
        }
        public Weapon(String id,String itemNbt,String name,String description,Specs specs,String usage,String category,Double maxHealth,Double movementSpeed,Double cooldownSeconds,String command,boolean jetpackVerticalFlight) {
            this(id,itemNbt,name,description,specs,usage,category,maxHealth,movementSpeed,cooldownSeconds,command,jetpackVerticalFlight,"");
        }
        public Weapon(String id,String itemNbt,String name,String description,Specs specs,String usage,String category,Double maxHealth,Double movementSpeed,Double cooldownSeconds,String command) {
            this(id,itemNbt,name,description,specs,usage,category,maxHealth,movementSpeed,cooldownSeconds,command,false);
        }
        public Weapon(String id,String itemNbt,String name,String description,Specs specs,String usage,String category,Double maxHealth,Double movementSpeed,Double cooldownSeconds) {
            this(id,itemNbt,name,description,specs,usage,category,maxHealth,movementSpeed,cooldownSeconds,"");
        }
        public boolean commandOnly() {return (itemNbt.isBlank()||itemNbt.strip().equals("{}"))&&!command.isBlank();}
        public Weapon(String id,String itemNbt,String name,String description,Specs specs,String usage,String category) {
            this(id,itemNbt,name,description,specs,usage,category,null,null,null);
        }
        public Weapon(String id, String itemNbt, String name, String description, Specs specs, String usage) {
            this(id,itemNbt,name,description,specs,usage,"미분류");
        }
        public ItemStack stack() {
            if(commandOnly())return ItemStack.EMPTY;
            try {
                var tag=TagParser.parseTag(itemNbt);
                String oldId=tag.getString("id");
                if(java.util.Set.of("gungang_acoustics:landmine","gungang_acoustics:recovery_potion","gungang_acoustics:adrenaline","gungang_acoustics:barrier").contains(oldId))
                    tag.putString("id",oldId.replace("gungang_acoustics:","gungang:"));
                ItemStack stack = ItemStack.of(tag);
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
            if(commandOnly()&&requireSpecs)throw new IllegalArgumentException("Command fallback is only supported for secondary weapons");
            if (specs != null) for (String value : new String[]{specs.damage, specs.accuracy, specs.stability, specs.range, specs.fireRate})
                if (value == null || value.length() > 80) throw new IllegalArgumentException("Invalid weapon specs: " + id);
            if (stats.size() > 12) throw new IllegalArgumentException("At most 12 stats per weapon: " + id);
            for (var stat : stats)
                if (stat == null || stat.name() == null || stat.name().isBlank() || stat.name().length() > 24 || stat.value() == null || stat.value().length() > 40
                        || (stat.name() + stat.value()).chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid weapon stats: " + id);
            stack();
        }
        public Weapon view() { return new Weapon(id, "", name, description, specs, usage, category,maxHealth,movementSpeed,cooldownSeconds,"",false,releaseDate,stats); }
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
        ensureGadgets(directory.resolve("sub_weapons"));
        ensureCombatGadgets(directory.resolve("sub_weapons"));
        ensureOrdnance(directory.resolve("sub_weapons"));
        BombEquipment.load(directory.resolve("sub_weapons"));
        Loadouts result = new Loadouts(readCategory(directory.resolve("weapons")), readCategory(directory.resolve("sub_weapons")),
                Files.exists(slot9) ? ServerFiles.GSON.fromJson(read(slot9, 65536), Weapon.class) : null);
        validate(result);
        result=new Loadouts(result.primary().stream().filter(w->!BombEquipment.reserved(w)).toList(),result.secondary().stream().filter(w->!BombEquipment.reserved(w)).toList(),result.slot9());
        if(result.primary().isEmpty()||result.secondary().isEmpty())throw new IllegalArgumentException("Configure at least one selectable weapon per category");
        return result;
    }
    private static void ensureGadgets(Path directory) throws java.io.IOException {
        Path marker=directory.resolve(".gungang-gadgets-created");if(Files.exists(marker))return;
        var definitions=List.of(
                new Weapon("landmine","{id:'gungang:landmine',Count:1b}","지뢰","최대 3개 설치, 아군 피해 없음",null,"사용 후 우클릭 설치 / 좌클릭 취소. 자기 지뢰를 조준하고 사용 키를 5초 유지하면 철거합니다.","설치형"),
                new Weapon("recovery_potion","{id:'gungang:recovery_potion',Count:1b}","회복약","5초 동안 총 50 회복",null,"보조무기 사용 키로 사용합니다.","지원"),
                new Weapon("adrenaline","{id:'gungang:adrenaline',Count:1b}","전투자극제","최대 체력의 50% 소모, 7초 동안 이동/공격 속도 및 최종 피해 +20%",null,"보조무기 사용 키로 사용합니다. 체력이 비용보다 많아야 합니다.","지원"),
                new Weapon("barrier","{id:'gungang:barrier',Count:1b}","방벽","3×3 나무 방벽, 체력 200, 최대 1개",null,"사용 후 우클릭 설치 / 좌클릭 취소.","설치형"));
        var existing=readCategory(directory);
        for(var weapon:definitions)if(existing.stream().noneMatch(w->w.id().equals(weapon.id())||(w.itemNbt().contains("gungang:"+weapon.id())||w.itemNbt().contains("gungang_acoustics:"+weapon.id()))))
            ConfigLayout.writeNew(directory.resolve("gungang_"+weapon.id()+".json"),ServerFiles.GSON.toJson(weapon));
        ConfigLayout.writeNew(marker,"Built-in gadget examples created without overwriting existing files.\n");
    }
    private static void ensureCombatGadgets(Path directory)throws java.io.IOException {
        Path marker=directory.resolve(".gungang-combat-gadgets-created");if(Files.exists(marker))return;
        var existing=readCategory(directory);
        for(var weapon:List.of(
                new Weapon("96_infrared-scanner","{id:'gungang:infrared_scanner',Count:1b}","적외선 스캐너","적 위치를 10초간 팀에 공유합니다. 쿨타임 120초.",null,"사용 키로 스캔. 적 팀 색상 발광 테두리가 벽 너머로 표시됩니다.","지원",100d,100d,120d),
                new Weapon("97_shield","{id:'gungang:shield',Count:1b}","방패","정면 피해 총 75 방어. 파손 후 90초 수리. 초과 피해는 체력에 적용됩니다.",null,"주손에 들 때만 방어하며 공격할 수 없습니다. 보조 손에 보관하면 주무기를 사용할 수 있습니다.","방어",100d,100d,90d))) {
            String item=weapon.id().startsWith("96_")?"infrared_scanner":"shield";
            if(existing.stream().noneMatch(w->w.id().equals(weapon.id())||w.itemNbt().contains("gungang:"+item)))
                ConfigLayout.writeNew(directory.resolve(weapon.id()+".json"),ServerFiles.GSON.toJson(weapon));
        }
        ConfigLayout.writeNew(marker,"Scanner and shield defaults created without overwriting custom loadouts.\n");
    }
    private static void ensureOrdnance(Path directory)throws java.io.IOException {
        var existing=readCategory(directory);
        for(var weapon:OrdnanceLoadouts.defaults()) {
            String item=switch(weapon.id()) {case "90"->"turret";case "81"->"molotov";case "87"->"orbital_strike";case "89"->"bear_trap";case "80"->"hand_axe";case "92"->"life_support";default->"jetpack";};
            if(existing.stream().noneMatch(w->w.id().equals(weapon.id())||w.itemNbt().contains("gungang:"+item)))
                ConfigLayout.writeNew(directory.resolve(weapon.id()+"_"+item+".json"),ServerFiles.GSON.toJson(weapon));
        }
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
                weapons.add(directory.getFileName().toString().equals("sub_weapons")?OrdnanceLoadouts.upgradePlaceholder(weapon):weapon);
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
        if (result.slot9 != null) {
            if(result.slot9.commandOnly())throw new IllegalArgumentException("slot9.json requires itemNbt");
            result.slot9.validate(false);
        }
    }
}
