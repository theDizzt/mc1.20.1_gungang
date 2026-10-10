package dev.gungang.acoustics.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID)
public final class LobbyManager {
    private static MinecraftServer server;
    private static ServerFiles.Loaded files;
    private static Loadouts loadouts;
    private static final Map<UUID, Entry> PLAYERS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, String[]> FREE_SELECTION = new HashMap<>();
    private static final Map<UUID, EquipmentSnapshot> LAST_EQUIPMENT = new HashMap<>();
    private static boolean enabled;
    private static String phase = "LOBBY", selected = "";
    private static int deadline;
    private static ArenaMap activeMap;
    private static final String TAG = "GunGangIssued";
    private static final class Entry {
        String team = "UNASSIGNED", primary = "", secondary = "";
        boolean ready;
        final GameType originalMode;
        final ResourceKey<Level> originalDimension;
        final Vec3 originalPosition;
        final float yaw, pitch;
        final Map<Integer, ItemStack> displaced = new HashMap<>();
        Entry(ServerPlayer p) {
            originalMode = p.gameMode.getGameModeForPlayer(); originalDimension = p.level().dimension();
            originalPosition = p.position(); yaw = p.getYRot(); pitch = p.getXRot();
        }
    }
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        server = event.getServer(); PLAYERS.clear(); LAST_ACTION.clear(); FREE_SELECTION.clear(); LAST_EQUIPMENT.clear(); enabled = false; phase = "LOBBY";
        try { reload(); } catch (Exception failure) { LogUtils.getLogger().error("GunGang config could not be loaded; previous files were not overwritten", failure); }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { if (enabled) close(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { server = null; files = null; loadouts = null; PLAYERS.clear(); }
    private static void reload() throws Exception {
        reload(true);
    }
    private static void reload(boolean force) throws Exception {
        if (!phase.equals("LOBBY")) throw new IllegalStateException("경기 중에는 설정을 다시 읽을 수 없습니다.");
        var candidate = ServerFiles.load();
        var weapons = Loadouts.load();
        if (!force && candidate.equals(files) && weapons.equals(loadouts)) return;
        files = candidate; loadouts = weapons;
        if (files.maps().stream().noneMatch(m -> m.id().equals(selected))) selected = files.maps().isEmpty() ? "" : files.maps().get(0).id();
        PLAYERS.values().forEach(e -> e.ready = false);
        if (server != null) for (var p : server.getPlayerList().getPlayers()) syncAcoustics(p);
        broadcast(false);
    }
    private static void syncAcoustics(ServerPlayer p) {
        if (files != null && server.isDedicatedServer()) GungangNetwork.send(p, new GungangNetwork.Acoustics(ServerFiles.GSON.toJson(files.acoustics())));
    }
    @SubscribeEvent public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        syncAcoustics(p);
        if (enabled) { add(p); broadcast(false); send(p, true); }
    }
    @SubscribeEvent public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Entry entry = PLAYERS.remove(p.getUUID());
        if (entry != null) restore(p, entry, true);
        LAST_ACTION.remove(p.getUUID());
        FREE_SELECTION.remove(p.getUUID());
        LAST_EQUIPMENT.remove(p.getUUID());
        // A departure must never trigger an unexpected match start.
        if (phase.equals("LOBBY")) PLAYERS.values().forEach(e -> e.ready = false);
        broadcast(false);
    }
    private static void add(ServerPlayer p) {
        if (!PLAYERS.containsKey(p.getUUID())) PLAYERS.put(p.getUUID(), new Entry(p));
        PLAYERS.get(p.getUUID()).team = phase.equals("LOBBY") ? "UNASSIGNED" : "SPECTATOR";
        PLAYERS.get(p.getUUID()).ready = false;
        p.setGameMode(GameType.SPECTATOR);
        server.getScoreboard().removePlayerFromTeam(p.getScoreboardName());
    }
    private static void unassign(ServerPlayer p) {
        var entry=PLAYERS.get(p.getUUID());
        if(entry!=null) { entry.team="UNASSIGNED"; entry.ready=false; }
        server.getScoreboard().removePlayerFromTeam(p.getScoreboardName());
    }
    private static void open() throws Exception {
        if (phase.equals("LOBBY")) reload(false);
        if (files == null || loadouts == null) throw new IllegalStateException("설정 오류를 수정한 뒤 /gungang reload 를 실행하세요.");
        if (!enabled) { enabled = true; phase = "LOBBY"; for (var p : server.getPlayerList().getPlayers()) add(p); }
        if (phase.equals("LOBBY")) for (var p:server.getPlayerList().getPlayers()) unassign(p);
        broadcast(true);
    }
    private static void close() {
        if (!phase.equals("LOBBY") && activeMap != null) finish("ABORTED");
        enabled = false; phase = "LOBBY";
        for (var p : server.getPlayerList().getPlayers()) { Entry e = PLAYERS.get(p.getUUID()); if (e != null) restore(p, e, true); }
        broadcast(false); PLAYERS.clear(); activeMap = null;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("gungang");
        root.then(Commands.literal("lobby").executes(c -> run(c.getSource(), () -> {
            if (!enabled) throw new IllegalStateException("로비가 닫혀 있습니다.");
            var player=c.getSource().getPlayerOrException();
            if (phase.equals("LOBBY")) { reload(false); unassign(player); broadcast(false); }
            send(player,true);
        }))
                .then(Commands.literal("open").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::open)))
                .then(Commands.literal("close").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::close))));
        root.then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), () -> { reload(); c.getSource().sendSuccess(() -> Component.literal("GunGang 설정을 다시 읽었습니다."), false); })));
        root.then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::finish)));
        root.then(Commands.literal("random").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::randomize)));
        root.then(Commands.literal("ready").executes(c -> { action(c.getSource().getPlayerOrException(), new GungangNetwork.Action("ready", "", "")); return 1; }));
        root.then(Commands.literal("team").then(Commands.argument("team", StringArgumentType.word()).suggests((c,b) -> { b.suggest("ALPHA"); b.suggest("BRAVO"); b.suggest("SPECTATOR"); return b.buildFuture(); })
                .executes(c -> { action(c.getSource().getPlayerOrException(), new GungangNetwork.Action("team", StringArgumentType.getString(c,"team").toUpperCase(Locale.ROOT), "")); return 1; })));
        root.then(Commands.literal("map").requires(s -> s.hasPermission(2)).then(Commands.argument("id", StringArgumentType.word()).suggests((c,b) -> { if (files != null) files.maps().forEach(m -> b.suggest(m.id())); return b.buildFuture(); })
                .executes(c -> run(c.getSource(), () -> select(StringArgumentType.getString(c,"id"))))));
        root.then(Commands.literal("loadout").executes(c -> { equipment(c.getSource().getPlayerOrException(), true); return 1; }));
        root.then(Commands.literal("exportitem").requires(s -> s.hasPermission(2)).then(Commands.argument("id", StringArgumentType.word()).executes(c -> run(c.getSource(), () -> {
            String id = StringArgumentType.getString(c, "id");
            if (!id.matches("[a-z0-9_-]{1,48}")) throw new IllegalArgumentException("파일 ID: 영문 소문자/숫자/_/- 만 가능합니다.");
            var p = c.getSource().getPlayerOrException(); ItemStack item = p.getMainHandItem();
            if (item.isEmpty()) throw new IllegalArgumentException("내보낼 아이템을 주 손에 드세요.");
            var weapon = new Loadouts.Weapon(id, item.save(new net.minecraft.nbt.CompoundTag()).toString(), item.getHoverName().getString(), "", new Loadouts.Specs("", "", "", "", ""), "");
            java.nio.file.Path dir = ServerFiles.root().resolve("exports"); java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve(id + ".json"), ServerFiles.GSON.toJson(weapon), java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            c.getSource().sendSuccess(() -> Component.literal("config/gungang/exports/" + id + ".json 에 NBT를 저장했습니다."), false);
        }))));
        event.getDispatcher().register(root);
    }
    private interface Work { void run() throws Exception; }
    private static int run(net.minecraft.commands.CommandSourceStack source, Work work) {
        try { work.run(); return 1; } catch (Exception error) { source.sendFailure(Component.literal(String.valueOf(error.getMessage()))); return 0; }
    }
    public static void action(ServerPlayer sender, GungangNetwork.Action action) {
        if (server == null) return;
        int now = server.getTickCount();
        if (now - LAST_ACTION.getOrDefault(sender.getUUID(), -100) < 4) return;
        LAST_ACTION.put(sender.getUUID(), now);
        try {
            if (action.action().equals("equipment")) { equipment(sender, true); return; }
            if (action.action().equals("primary") || action.action().equals("secondary")) { chooseWeapon(sender, action.action(), action.value()); return; }
            if (!enabled || !PLAYERS.containsKey(sender.getUUID())) return;
            if (!phase.equals("LOBBY")) throw new IllegalStateException("준비 또는 경기 진행 중에는 로비 설정을 변경할 수 없습니다.");
            switch (action.action()) {
                case "map" -> { admin(sender); select(action.value()); }
                case "random" -> { admin(sender); randomize(); }
                case "team" -> {
                    UUID target = action.target().isEmpty() ? sender.getUUID() : UUID.fromString(action.target());
                    if (!target.equals(sender.getUUID())) admin(sender);
                    var player = server.getPlayerList().getPlayer(target);
                    if (player == null || !PLAYERS.containsKey(target)) return;
                    setTeam(player, action.value());
                }
                case "ready" -> { var e = PLAYERS.get(sender.getUUID()); if (e.team.equals("ALPHA") || e.team.equals("BRAVO")) e.ready = !e.ready; }
                default -> { return; }
            }
            if (allReady()) prepare();
            broadcast(false);
        } catch (Exception failure) { sender.sendSystemMessage(Component.literal(String.valueOf(failure.getMessage())).withStyle(ChatFormatting.RED)); }
    }
    private static void admin(ServerPlayer p) { if (!p.hasPermissions(2)) throw new IllegalArgumentException("관리자 권한이 필요합니다."); }
    private static void select(String id) {
        if (!enabled || !phase.equals("LOBBY") || files.maps().stream().noneMatch(m -> m.id().equals(id))) throw new IllegalArgumentException("선택할 수 없는 맵입니다.");
        selected = id; PLAYERS.values().forEach(e -> e.ready = false); broadcast(false);
    }
    private static void setTeam(ServerPlayer p, String team) {
        if (!Set.of("ALPHA", "BRAVO", "SPECTATOR").contains(team)) throw new IllegalArgumentException("ALPHA / BRAVO / SPECTATOR 중 선택하세요.");
        Entry e = PLAYERS.get(p.getUUID()); e.team = team;
        PLAYERS.values().forEach(entry -> entry.ready = false);
        if (team.equals("SPECTATOR")) { server.getScoreboard().removePlayerFromTeam(p.getScoreboardName()); p.setGameMode(GameType.SPECTATOR); }
        else {
            String id = team.equals("ALPHA") ? "Red" : "Blue";
            PlayerTeam scoreboardTeam = server.getScoreboard().getPlayerTeam(id);
            if (scoreboardTeam == null) scoreboardTeam = server.getScoreboard().addPlayerTeam(id);
            scoreboardTeam.setDisplayName(Component.literal(team.equals("ALPHA") ? "Team ALPHA" : "Team Bravo"));
            scoreboardTeam.setColor(team.equals("ALPHA") ? ChatFormatting.RED : ChatFormatting.BLUE);
            server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), scoreboardTeam);
            p.setGameMode(e.originalMode == GameType.SPECTATOR ? GameType.ADVENTURE : e.originalMode);
        }
    }
    private static void randomize() {
        if (!enabled || !phase.equals("LOBBY")) throw new IllegalStateException("로비에서만 팀을 배치할 수 있습니다.");
        var ids = new ArrayList<>(PLAYERS.keySet());
        // Explicit spectators stay spectators; new, unassigned players can be randomized.
        ids.removeIf(id -> PLAYERS.get(id).team.equals("SPECTATOR"));
        Collections.shuffle(ids);
        for (int i = 0; i < ids.size(); i++) { var p = server.getPlayerList().getPlayer(ids.get(i)); if (p != null) setTeam(p, i % 2 == 0 ? "ALPHA" : "BRAVO"); }
        broadcast(false);
    }
    private static boolean allReady() {
        return !selected.isEmpty() && LobbyRules.allReady(PLAYERS.values().stream().map(e -> new LobbyRules.Readiness(e.team, e.ready)).toList());
    }
    private static ServerLevel level(ArenaMap map) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(map.dimension())));
        if (level == null) throw new IllegalArgumentException("맵 차원이 존재하지 않습니다: " + map.dimension());
        for (var spawn : List.of(map.alphaSpawn(), map.bravoSpawn(), map.spectatorSpawn()))
            if (spawn.y() < level.getMinBuildHeight() || spawn.y() >= level.getMaxBuildHeight()) throw new IllegalArgumentException("스폰 Y가 차원 높이 범위를 벗어납니다.");
        return level;
    }
    private static void prepare() {
        activeMap = files.maps().stream().filter(m -> m.id().equals(selected)).findFirst().orElseThrow();
        level(activeMap);
        phase = "PREPARING"; deadline = server.getTickCount() + 600;
        MatchManager.prepare(server, activeMap);
        hooks(activeMap.onPrepare());
        for (var p : server.getPlayerList().getPlayers()) {
            Entry e = PLAYERS.get(p.getUUID()); if (e == null) continue;
            teleportSpawn(p, e); p.setGameMode(e.team.equals("SPECTATOR") ? GameType.SPECTATOR : GameType.byName(activeMap.gameMode()));
            if (!e.team.equals("SPECTATOR")) {
                String[] choice=FREE_SELECTION.getOrDefault(p.getUUID(),new String[]{e.primary,e.secondary});
                var primary=loadouts.primary().stream().filter(w->w.id().equals(choice[0])).findFirst().orElse(loadouts.primary().get(0));
                var secondary=loadouts.secondary().stream().filter(w->w.id().equals(choice[1])).findFirst().orElse(loadouts.secondary().get(0));
                e.primary = primary.id(); e.secondary = secondary.id();
                grant(p, primary, 0, true); grant(p, secondary, 40, true);
                if (loadouts.slot9() != null) grant(p, loadouts.slot9(), 8, true);
                equipment(p, true);
            }
        }
    }
    private static void teleportSpawn(ServerPlayer p, Entry e) {
        var spawn = e.team.equals("ALPHA") ? activeMap.alphaSpawn() : e.team.equals("BRAVO") ? activeMap.bravoSpawn() : activeMap.spectatorSpawn();
        p.stopRiding(); p.teleportTo(level(activeMap), spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()); p.fallDistance = 0;
    }
    public static boolean inSpawn(ServerPlayer p) {
        Entry e = PLAYERS.get(p.getUUID());
        if (!enabled || activeMap == null || e == null || e.team.equals("SPECTATOR") || !p.isAlive()
                || phase.equals("LOBBY") || !p.level().dimension().location().toString().equals(activeMap.dimension())) return false;
        var area = e.team.equals("ALPHA") ? activeMap.alphaArea() : activeMap.bravoArea();
        var bounds = p.getBoundingBox();
        return area.contains(bounds.minX, bounds.minY, bounds.minZ) && area.contains(bounds.maxX, bounds.maxY, bounds.maxZ);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        int now = server.getTickCount();
        if (phase.equals("PREPARING")) {
            for (var p : server.getPlayerList().getPlayers()) { var e = PLAYERS.get(p.getUUID()); if (e != null && !e.team.equals("SPECTATOR") && p.isAlive() && !inSpawn(p)) teleportSpawn(p, e); }
            if (now >= deadline) { phase = "RUNNING"; deadline = now + activeMap.timeLimitSeconds() * 20; MatchManager.start(); hooks(activeMap.onStart()); broadcast(false); }
        }
        if (phase.equals("PREPARING") || phase.equals("RUNNING")) MatchManager.tick(Math.max(0,(deadline-now+19)/20));
        if (now % 4 == 0) {
            for (var p : server.getPlayerList().getPlayers()) {
                equipment(p, false);
            }
        }
    }
    private static void hooks(List<String> functions) {
        if (functions == null) return;
        for (String id : functions) {
            var function = server.getFunctions().get(new ResourceLocation(id));
            if (function.isPresent()) server.getFunctions().execute(function.get(), server.createCommandSourceStack().withPermission(2).withSuppressedOutput());
            else LogUtils.getLogger().warn("GunGang map function not found: {}", id);
        }
    }
    static String team(UUID id) { var entry=PLAYERS.get(id); return entry==null?"SPECTATOR":entry.team; }
    private static void finish() { finish("ABORTED"); }
    static void finish(String winner) {
        if (activeMap == null || phase.equals("LOBBY") || phase.equals("FINISHING")) return;
        phase = "FINISHING"; hooks(activeMap.onEnd()); phase = "LOBBY";
        for (var p : server.getPlayerList().getPlayers()) { var e = PLAYERS.get(p.getUUID()); if (e != null) {
            restore(p, e, false); unassign(p);
        } }
        activeMap = null; broadcast(false); MatchManager.end(winner);
    }
    private static void restore(ServerPlayer p, Entry e, boolean team) {
        LAST_EQUIPMENT.remove(p.getUUID());
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (issued(p.getInventory().getItem(i))) p.getInventory().setItem(i, ItemStack.EMPTY);
        for (var saved : e.displaced.entrySet()) {
            ItemStack current = p.getInventory().getItem(saved.getKey());
            p.getInventory().setItem(saved.getKey(), saved.getValue().copy());
            if (!current.isEmpty() && !p.getInventory().add(current)) p.drop(current, false);
        }
        e.displaced.clear();
        var home = server.getLevel(e.originalDimension);
        if (home != null) p.teleportTo(home, e.originalPosition.x, e.originalPosition.y, e.originalPosition.z, e.yaw, e.pitch);
        p.setGameMode(e.originalMode);
        if (team) unassign(p);
        p.inventoryMenu.broadcastChanges();
        GungangNetwork.send(p, new GungangNetwork.Equipment(ServerFiles.GSON.toJson(new EquipmentSnapshot(false, false, e.team, e.primary, e.secondary, 0, null))));
    }
    private static boolean issued(ItemStack stack) { return stack.hasTag() && stack.getTag().contains(TAG); }
    private static void grant(ServerPlayer p, Loadouts.Weapon weapon, int slot, boolean remember) {
        Entry e = PLAYERS.get(p.getUUID());
        if (remember) e.displaced.putIfAbsent(slot, p.getInventory().getItem(slot).copy());
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack old = p.getInventory().getItem(i);
            if (issued(old) && old.getTag().getInt(TAG) == slot) p.getInventory().setItem(i, ItemStack.EMPTY);
        }
        ItemStack displaced = p.getInventory().getItem(slot);
        ItemStack stack = weapon.stack(); stack.getOrCreateTag().putInt(TAG, slot);
        p.getInventory().setItem(slot, stack);
        if (!remember && !displaced.isEmpty() && !p.getInventory().add(displaced)) p.drop(displaced,false);
        p.inventoryMenu.broadcastChanges();
    }
    private static void chooseWeapon(ServerPlayer p, String group, String id) throws java.io.IOException {
        if (!canChoose(p)) throw new IllegalStateException("경기 중에는 자기 팀 스폰 구역에서만 무기를 선택할 수 있습니다.");
        loadouts = Loadouts.load();
        var weapons = group.equals("primary") ? loadouts.primary() : loadouts.secondary();
        var weapon = weapons.stream().filter(w -> w.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("등록되지 않은 무기입니다."));
        int slot = group.equals("primary") ? 0 : 40;
        if (phase.equals("LOBBY")) {
            grantFree(p,weapon,slot);
            String[] choice=FREE_SELECTION.computeIfAbsent(p.getUUID(),ignored->new String[]{"",""});
            choice[slot==0?0:1]=id;
            equipment(p,true); return;
        }
        boolean found = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) { var item = p.getInventory().getItem(i); if (issued(item) && item.getTag().getInt(TAG) == slot) found = true; }
        if (!found) throw new IllegalStateException("기존 지급 무기를 인벤토리로 가져온 뒤 교체하세요.");
        grant(p, weapon, slot, false);
        Entry e = PLAYERS.get(p.getUUID()); if (slot == 0) e.primary = id; else e.secondary = id;
        equipment(p, true);
    }
    private static void equipment(ServerPlayer p, boolean show) {
        var e = PLAYERS.get(p.getUUID());
        boolean allowed = loadouts != null && canChoose(p);
        if (show && allowed) {
            try { loadouts = Loadouts.load(); }
            catch (Exception failure) {
                p.sendSystemMessage(Component.literal("서버 무기 설정을 읽지 못했습니다: " + failure.getMessage()).withStyle(ChatFormatting.RED));
                return;
            }
        }
        if (show && !allowed) p.sendSystemMessage(Component.literal("무기 선택 불가: 경기 중에는 자기 팀 스폰 구역에 있어야 합니다. 서버 무기 설정도 확인하세요."));
        var choice=FREE_SELECTION.getOrDefault(p.getUUID(),new String[]{"",""});
        var snapshot = new EquipmentSnapshot(allowed, show && allowed, e == null ? "UNASSIGNED" : e.team,
                phase.equals("LOBBY") ? choice[0] : e == null ? "" : e.primary,
                phase.equals("LOBBY") ? choice[1] : e == null ? "" : e.secondary,
                phase.equals("PREPARING") ? Math.max(0, (deadline - server.getTickCount() + 19) / 20) : 0, show && allowed ? loadouts.view() : null,
                phase.equals("RUNNING") && allowed && inSpawn(p));
        var status=new EquipmentSnapshot(snapshot.allowed(),false,snapshot.team(),snapshot.primary(),snapshot.secondary(),snapshot.preparationSeconds(),null,snapshot.spawnHint());
        if (!show && status.equals(LAST_EQUIPMENT.get(p.getUUID()))) return;
        LAST_EQUIPMENT.put(p.getUUID(),status);
        GungangNetwork.send(p, new GungangNetwork.Equipment(ServerFiles.GSON.toJson(snapshot)));
    }
    private static boolean canChoose(ServerPlayer p) {
        var e=PLAYERS.get(p.getUUID());
        boolean team=e!=null&&(e.team.equals("ALPHA")||e.team.equals("BRAVO"));
        return LoadoutRules.allowed(phase,p.isAlive(),team,inSpawn(p));
    }
    private static void grantFree(ServerPlayer p,Loadouts.Weapon weapon,int slot) {
        String tag="GunGangFreeSlot";
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var old=p.getInventory().getItem(i);
            if(old.hasTag()&&old.getTag().contains(tag)&&old.getTag().getInt(tag)==slot)p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        var displaced=p.getInventory().getItem(slot);
        var stack=weapon.stack();stack.getOrCreateTag().putInt(tag,slot);
        p.getInventory().setItem(slot,stack);
        if(!displaced.isEmpty()&&!p.getInventory().add(displaced))p.drop(displaced,false);
        p.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || activeMap == null || phase.equals("LOBBY")) return;
        var e = PLAYERS.get(p.getUUID()); if (e == null) return;
        server.execute(() -> { teleportSpawn(p, e); if (!e.team.equals("SPECTATOR")) {
            loadouts.primary().stream().filter(w -> w.id().equals(e.primary)).findFirst().ifPresent(w -> grant(p, w, 0, false));
            loadouts.secondary().stream().filter(w -> w.id().equals(e.secondary)).findFirst().ifPresent(w -> grant(p, w, 40, false));
            if (loadouts.slot9() != null) grant(p, loadouts.slot9(), 8, false);
        } });
    }
    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (enabled && phase.equals("PREPARING") && ((event.getEntity() instanceof ServerPlayer p && PLAYERS.containsKey(p.getUUID()))
                || (event.getSource().getEntity() instanceof ServerPlayer attacker && PLAYERS.containsKey(attacker.getUUID())))) event.setCanceled(true);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PLAYERS.containsKey(p.getUUID())) event.getDrops().removeIf(item -> issued(item.getItem()));
    }
    @SubscribeEvent public static void toss(ItemTossEvent event) {
        if (PLAYERS.containsKey(event.getPlayer().getUUID()) && issued(event.getEntity().getItem()) && event.getEntity().getItem().getTag().getInt(TAG) != 8) {
            event.setCanceled(true); event.getPlayer().getInventory().add(event.getEntity().getItem());
        }
    }
    private static void send(ServerPlayer p, boolean show) {
        var mapViews = files == null ? List.<LobbySnapshot.MapView>of() : files.maps().stream().map(m -> new LobbySnapshot.MapView(m.id(),m.name(),m.description(),m.timeLimitSeconds(),m.gameMode())).toList();
        List<LobbySnapshot.Member> members = new ArrayList<>();
        for (var entry : PLAYERS.entrySet()) { var player = server.getPlayerList().getPlayer(entry.getKey()); if (player != null) members.add(new LobbySnapshot.Member(entry.getKey().toString(),player.getGameProfile().getName(),entry.getValue().team,entry.getValue().ready)); }
        var snapshot = new LobbySnapshot(enabled, show, p.hasPermissions(2), p.getUUID().toString(), selected, phase,
                phase.equals("LOBBY") ? 0 : Math.max(0,(deadline - server.getTickCount() + 19)/20), mapViews, members);
        GungangNetwork.send(p, new GungangNetwork.Lobby(ServerFiles.GSON.toJson(snapshot)));
    }
    private static void broadcast(boolean show) { if (server != null) for (var p : server.getPlayerList().getPlayers()) send(p, show); }
    private LobbyManager() { }
}
