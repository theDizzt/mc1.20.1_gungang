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
    private static final net.minecraft.core.BlockPos HOME = new net.minecraft.core.BlockPos(39,5,-179);
    private static MinecraftServer server;
    private static ServerFiles.Loaded files;
    private static Loadouts loadouts;
    private static final Map<UUID, Entry> PLAYERS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, Integer> LAST_PREVIEW = new HashMap<>();
    private static final Map<UUID, String[]> FREE_SELECTION = new HashMap<>();
    private static final Map<UUID, EquipmentSnapshot> LAST_EQUIPMENT = new HashMap<>();
    private record Respawning(int deadline, int duration, UUID killer) { }
    private static final Map<UUID, Respawning> RESPAWNING = new HashMap<>();
    private static boolean enabled;
    private static String phase = "LOBBY", selected = "";
    private static String selectedMode="CONTROL";
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
        server = event.getServer(); PLAYERS.clear(); LAST_ACTION.clear(); LAST_PREVIEW.clear(); FREE_SELECTION.clear(); LAST_EQUIPMENT.clear(); RESPAWNING.clear(); enabled = false; phase = "LOBBY";
        NormandyBeacon.get(server).restore(server);
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
        GadgetConfig.load();
        SafeZones.load(server);
        ProgressionService.configure(server);
        if (!force && candidate.equals(files) && weapons.equals(loadouts)) return;
        files = candidate; loadouts = weapons;
        for (String name : List.of("ALPHA", "BRAVO")) {
            var team = server.getScoreboard().getPlayerTeam(name.equals("ALPHA") ? "Red" : "Blue");
            if (team != null) team.setColor(options().formatting(name));
        }
        if (files.maps().stream().noneMatch(m -> m.id().equals(selected))) selected = files.maps().isEmpty() ? "" : files.maps().get(0).id();
        normalizeMode();
        PLAYERS.values().forEach(e -> e.ready = false);
        if (server != null) for (var p : server.getPlayerList().getPlayers()) syncAcoustics(p);
        broadcast(false);
    }
    private static void syncAcoustics(ServerPlayer p) {
        SafeZones.sync(p);
        if (files != null && server.isDedicatedServer()) GungangNetwork.send(p, new GungangNetwork.Acoustics(ServerFiles.GSON.toJson(files.acoustics())));
    }
    @SubscribeEvent public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        syncAcoustics(p);
        resetPlayer(p);
        if (enabled) { add(p); broadcast(false); send(p, true); }
        else send(p,false); // Team station colors are needed even before the lobby is opened.
        p.setGameMode(GameType.ADVENTURE);
    }
    @SubscribeEvent public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Entry entry = PLAYERS.remove(p.getUUID());
        if (entry != null) restore(p, entry, true);
        LAST_ACTION.remove(p.getUUID());
        LAST_PREVIEW.remove(p.getUUID());
        FREE_SELECTION.remove(p.getUUID());
        LAST_EQUIPMENT.remove(p.getUUID());
        // A departure must never trigger an unexpected match start.
        if (phase.equals("LOBBY")) PLAYERS.values().forEach(e -> e.ready = false);
        broadcast(false);
    }
    private static void add(ServerPlayer p) {
        if (PLAYERS.containsKey(p.getUUID())) return;
        PLAYERS.put(p.getUUID(), new Entry(p));
        PLAYERS.get(p.getUUID()).team = phase.equals("LOBBY") ? "UNASSIGNED" : "SPECTATOR";
        PLAYERS.get(p.getUUID()).ready = false;
        if(!phase.equals("LOBBY"))p.setGameMode(GameType.SPECTATOR);
        server.getScoreboard().removePlayerFromTeam(p.getScoreboardName());
    }
    private static void unassign(ServerPlayer p) {
        var entry=PLAYERS.get(p.getUUID());
        if(entry!=null) { entry.team="UNASSIGNED"; entry.ready=false; }
        server.getScoreboard().removePlayerFromTeam(p.getScoreboardName());
    }
    private static void open() throws Exception {
        ensureLobby();
        broadcast(true);
    }
    private static void ensureLobby() throws Exception {
        if(!phase.equals("LOBBY"))throw new IllegalStateException("이미 준비 중이거나 진행 중인 경기가 있습니다. 서버에서는 한 경기만 운영할 수 있습니다.");
        if(enabled)return;
        reload(false);
        if (files == null || loadouts == null) throw new IllegalStateException("설정 오류를 수정한 뒤 /gungang reload 를 실행하세요.");
        enabled=true;
        for(var p:server.getPlayerList().getPlayers())add(p);
    }
    static void reopenLobby(ServerPlayer p) {
        if(!enabled)throw new IllegalStateException("열린 로비가 없습니다. 전투 테이블을 사용하거나 관리자에게 /gungang lobby open을 요청하세요.");
        if(!phase.equals("LOBBY"))throw new IllegalStateException("이미 준비 또는 경기 중입니다. 도중 참가는 /gungang join을 사용하세요.");
        add(p);
        var entry=PLAYERS.get(p.getUUID());
        if(entry.team.equals("UNASSIGNED")&&p.isSpectator())p.setGameMode(entry.originalMode==GameType.SPECTATOR?GameType.ADVENTURE:entry.originalMode);
        send(p,true);
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
            reopenLobby(c.getSource().getPlayerOrException());
        }))
                .then(Commands.literal("open").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::open)))
                .then(Commands.literal("close").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::close))));
        root.then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), () -> { reload(); c.getSource().sendSuccess(() -> Component.literal("GunGang 설정을 다시 읽었습니다."), false); })));
        root.then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::finish)));
        root.then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::reset)));
        root.then(Commands.literal("cleardeployables").requires(s->s.hasPermission(2)).executes(c->run(c.getSource(),()->{
            int removed=GadgetManager.clearAll(c.getSource().getServer());
            c.getSource().sendSuccess(()->Component.literal("지뢰·방벽 "+removed+"개 제거, 미로드 설치물도 무효화했습니다."),true);
        })));
        root.then(Commands.literal("join").executes(c -> run(c.getSource(), () -> joinMatch(c.getSource().getPlayerOrException(), "")))
                .then(Commands.argument("team", StringArgumentType.word()).suggests((c,b) -> { b.suggest("ALPHA"); b.suggest("BRAVO"); return b.buildFuture(); })
                        .executes(c -> run(c.getSource(), () -> joinMatch(c.getSource().getPlayerOrException(), StringArgumentType.getString(c,"team").toUpperCase(Locale.ROOT))))
                        .then(Commands.argument("players", net.minecraft.commands.arguments.EntityArgument.players()).requires(s -> s.hasPermission(2))
                                .executes(c -> run(c.getSource(), () -> {
                                    for(var p:net.minecraft.commands.arguments.EntityArgument.getPlayers(c,"players"))
                                        joinMatch(p,StringArgumentType.getString(c,"team").toUpperCase(Locale.ROOT));
                                })))));
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
        event.getDispatcher().register(Commands.literal("reset").requires(s -> s.hasPermission(2))
                .executes(c -> run(c.getSource(), LobbyManager::reset)));
    }
    private interface Work { void run() throws Exception; }
    private static int run(net.minecraft.commands.CommandSourceStack source, Work work) {
        try { work.run(); return 1; } catch (Exception error) { source.sendFailure(Component.literal(String.valueOf(error.getMessage()))); return 0; }
    }
    public static void action(ServerPlayer sender, GungangNetwork.Action action) {
        if (server == null) return;
        if(action.action().equals("place")||action.action().equals("cancelplace")) { GadgetManager.action(sender,action.action());return; }
        int now = server.getTickCount();
        boolean preview=action.action().equals("mapPreview")||action.action().equals("weaponPreview");
        var rate=preview?LAST_PREVIEW:LAST_ACTION;
        if (now - rate.getOrDefault(sender.getUUID(), -100) < (preview?10:4)) return;
        rate.put(sender.getUUID(), now);
        try {
            if(action.action().equals("lobby")){reopenLobby(sender);return;}
            if(action.action().equals("mapPreview")) {
                if(files==null)return;
                var map=files.maps().stream().filter(m->m.id().equals(action.value())).findFirst().orElse(null);
                if(map==null)return;
                byte[] image=new byte[0];String error="미리보기 이미지가 지정되지 않았습니다.";
                if(!map.previewImage().isEmpty())try{image=MapPreviewFiles.read(ServerFiles.root().resolve("server/maps/preview"),map.previewImage());error="";}
                catch(java.io.IOException failure){error="미리보기 이미지를 읽을 수 없습니다.";com.mojang.logging.LogUtils.getLogger().warn("Map preview {}: {}",map.id(),failure.getMessage());}
                GungangNetwork.send(sender,new GungangNetwork.MapPreview(map.id(),image,error));return;
            }
            if(action.action().equals("weaponPreview")) {
                if(loadouts==null||!canChoose(sender))return;
                var group=action.target().equals("primary")?loadouts.primary():loadouts.secondary();
                var weapon=group.stream().filter(w->w.id().equals(action.value())).findFirst().orElse(null);
                if(weapon!=null)GungangNetwork.send(sender,new GungangNetwork.WeaponPreview(action.target()+":"+weapon.id(),weapon.stack()));
                return;
            }
            if (action.action().equals("equipment")) { equipment(sender, true); return; }
            if (action.action().equals("primary") || action.action().equals("secondary")) { chooseWeapon(sender, action.action(), action.value()); return; }
            if (!enabled || !PLAYERS.containsKey(sender.getUUID())) return;
            if (!phase.equals("LOBBY")) throw new IllegalStateException("준비 또는 경기 진행 중에는 로비 설정을 변경할 수 없습니다.");
            switch (action.action()) {
                case "map" -> { admin(sender); select(action.value()); }
                case "mode" -> { admin(sender); selectMode(action.value()); }
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
        selected = id; normalizeMode(); PLAYERS.values().forEach(e -> e.ready = false); broadcast(false);
    }
    private static ArenaMap selectedArena() { return files==null?null:files.maps().stream().filter(m->m.id().equals(selected)).findFirst().orElse(null); }
    private static void normalizeMode() {
        var arena=selectedArena();
        if(arena!=null&&arena.normandy()!=null)selectedMode="NORMANDY";
        else if(!selectedMode.equals("DEATHMATCH")||!DeathmatchRules.supported(arena))selectedMode="CONTROL";
    }
    private static void selectMode(String mode) {
        var arena=selectedArena();
        if(!Set.of("CONTROL","DEATHMATCH").contains(mode)||arena==null||arena.normandy()!=null
                ||(mode.equals("DEATHMATCH")&&!DeathmatchRules.supported(arena)))throw new IllegalArgumentException("선택할 수 없는 경기 모드입니다.");
        selectedMode=mode;PLAYERS.values().forEach(e->e.ready=false);
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
            scoreboardTeam.setColor(options().formatting(team));
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
    private static void joinMatch(ServerPlayer p, String requested) {
        if (!enabled || activeMap == null || (!phase.equals("RUNNING") && !phase.equals("PREPARING")))
            throw new IllegalStateException("참여할 진행 중인 경기가 없습니다.");
        var existing=PLAYERS.get(p.getUUID());
        if (existing!=null && (existing.team.equals("ALPHA") || existing.team.equals("BRAVO")))
            throw new IllegalStateException("이미 경기 참가 중입니다.");
        String team=requested;
        if(team.isEmpty()) {
            long alpha=PLAYERS.values().stream().filter(e->e.team.equals("ALPHA")).count();
            long bravo=PLAYERS.values().stream().filter(e->e.team.equals("BRAVO")).count();
            team=alpha<=bravo?"ALPHA":"BRAVO";
        }
        if(!team.equals("ALPHA")&&!team.equals("BRAVO")) throw new IllegalArgumentException("ALPHA / BRAVO 중 선택하세요.");
        if(existing==null) add(p);
        setTeam(p,team);
        var entry=PLAYERS.get(p.getUUID());
        teleportSpawn(p,entry); p.setGameMode(GameType.byName(activeMap.gameMode()));
        var primary=loadouts.primary().get(0); var secondary=loadouts.secondary().get(0);
        entry.primary=primary.id(); entry.secondary=secondary.id();
        grant(p,primary,0,true); grant(p,secondary,40,true);
        if(loadouts.slot9()!=null) grant(p,loadouts.slot9(),8,true);
        MatchManager.participant(p); equipment(p,true); broadcast(false);
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
        DeathmatchRules deathmatch=selectedMode.equals("DEATHMATCH")?options().deathmatch():null;
        if(deathmatch!=null)activeMap=deathmatch.arena(activeMap);
        level(activeMap);
        phase = "PREPARING"; deadline = server.getTickCount() + preparationSeconds(activeMap)*20;
        GadgetManager.clearAll(server);
        CombatGadgets.reset(server);
        MatchManager.prepare(server, activeMap, deathmatch);
        hooks(activeMap.onPrepare());
        for (var p : server.getPlayerList().getPlayers()) {
            Entry e = PLAYERS.get(p.getUUID()); if (e == null) continue;
            clearInventory(p,e);
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
            else giveSlot9(p);
        }
    }
    private static void teleportSpawn(ServerPlayer p, Entry e) {
        var spawn = e.team.equals("ALPHA") ? activeMap.alphaSpawn() : e.team.equals("BRAVO") ? activeMap.bravoSpawn() : activeMap.spectatorSpawn();
        if(phase.equals("RUNNING")&&e.team.equals("BRAVO")&&activeMap.normandy()!=null) spawn=MatchManager.attackSpawn();
        p.stopRiding(); p.teleportTo(level(activeMap), spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()); p.fallDistance = 0;
    }
    public static int preparationSeconds(ArenaMap arena) {return arena.normandy()==null?30:45;}
    private static void clearInventory(ServerPlayer p,Entry entry) {
        clearPlayerInventory(p);
        if(entry!=null)entry.displaced.clear();p.inventoryMenu.broadcastChanges();
    }
    static void clearPlayerInventory(ServerPlayer p) {
        p.containerMenu.setCarried(ItemStack.EMPTY);p.closeContainer();p.getInventory().clearContent();
        p.inventoryMenu.broadcastChanges();
    }
    private static void giveSlot9(ServerPlayer p) {
        if(loadouts==null||loadouts.slot9()==null)return;
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var stack=p.getInventory().getItem(i);var tag=stack.getTag();
            if(tag!=null&&((tag.contains(TAG)&&tag.getInt(TAG)==8)||(tag.contains("GunGangFreeSlot")&&tag.getInt("GunGangFreeSlot")==8)))p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        p.getInventory().setItem(8,ItemStack.EMPTY);
        if(phase.equals("LOBBY"))grantFree(p,loadouts.slot9(),8);else grant(p,loadouts.slot9(),8,false);
    }
    private static void resetMatchEquipment(ServerPlayer p,Entry e) {
        clearInventory(p,e);
        if(e.team.equals("ALPHA")||e.team.equals("BRAVO")) {
            loadouts.primary().stream().filter(w->w.id().equals(e.primary)).findFirst().ifPresent(w->grant(p,w,0,false));
            loadouts.secondary().stream().filter(w->w.id().equals(e.secondary)).findFirst().ifPresent(w->grant(p,w,40,false));
        }
        giveSlot9(p);
    }
    public static boolean inSpawn(ServerPlayer p) {
        Entry e = PLAYERS.get(p.getUUID());
        if (!enabled || activeMap == null || e == null || e.team.equals("SPECTATOR") || !p.isAlive()
                || phase.equals("LOBBY") || !p.level().dimension().location().toString().equals(activeMap.dimension())) return false;
        var area = e.team.equals("ALPHA") ? activeMap.alphaArea() : activeMap.bravoArea();
        if(phase.equals("RUNNING")&&e.team.equals("BRAVO")&&activeMap.normandy()!=null) {
            var s=MatchManager.attackSpawn();
            return p.distanceToSqr(s.x(),s.y(),s.z())<=16;
        }
        var bounds = p.getBoundingBox();
        return area.contains(bounds.minX, bounds.minY, bounds.minZ) && area.contains(bounds.maxX, bounds.maxY, bounds.maxZ);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        int now = server.getTickCount();
        tickRespawns(now);
        if (phase.equals("PREPARING")) {
            for (var p : server.getPlayerList().getPlayers()) { var e = PLAYERS.get(p.getUUID()); if (e != null && !e.team.equals("SPECTATOR") && p.isAlive()
                    && !(activeMap.normandy()!=null&&e.team.equals("ALPHA")) && !inSpawn(p)) teleportSpawn(p, e); }
            if (now >= deadline) {
                phase = "RUNNING"; deadline = now + activeMap.timeLimitSeconds() * 20; MatchManager.start();
                GadgetManager.clearAll(server);
                CombatGadgets.reset(server);
                for(var p:server.getPlayerList().getPlayers()) {var e=PLAYERS.get(p.getUUID());if(e!=null)resetMatchEquipment(p,e);}
                if(activeMap.normandy()!=null) {
                    var s=activeMap.normandy().attackLaunch();
                    for(var p:server.getPlayerList().getPlayers()) if(team(p.getUUID()).equals("BRAVO"))
                        p.teleportTo(level(activeMap),s.x(),s.y(),s.z(),s.yaw(),s.pitch());
                }
                hooks(activeMap.onStart());
                for(var p:server.getPlayerList().getPlayers()) if(PLAYERS.containsKey(p.getUUID())) {
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10,50,10));
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.empty()));
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                            Component.literal("경기 시작!").withStyle(net.minecraft.ChatFormatting.GOLD,net.minecraft.ChatFormatting.BOLD)));
                }
                broadcast(false);
            }
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
        GadgetManager.clearAll(server);
        CombatGadgets.reset(server);
        for (var p : server.getPlayerList().getPlayers()) { var e = PLAYERS.get(p.getUUID()); if (e != null) {
            clearInventory(p,e);restore(p, e, false);unassign(p);giveSlot9(p);
        } }
        activeMap = null; broadcast(false); MatchManager.end(winner);
    }
    private static void restore(ServerPlayer p, Entry e, boolean team) {
        LoadoutAttributes.clear(p);
        GadgetManager.clear(p);
        RESPAWNING.remove(p.getUUID()); p.setCamera(p);
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
        if(slot==40)GadgetManager.changingSecondary(p,weapon);
        Entry e = PLAYERS.get(p.getUUID());
        if (remember) e.displaced.putIfAbsent(slot, p.getInventory().getItem(slot).copy());
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack old = p.getInventory().getItem(i);
            if (issued(old) && old.getTag().getInt(TAG) == slot) p.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if(weapon.commandOnly()) {executeSecondaryCommand(p,weapon,slot,TAG);return;}
        ItemStack displaced = p.getInventory().getItem(slot);
        ItemStack stack = weapon.stack(); stack.getOrCreateTag().putInt(TAG, slot);
        if(slot==40) SecondaryRuntime.configure(p,stack,weapon);
        if(slot==0) LoadoutAttributes.apply(p,weapon);
        p.getInventory().setItem(slot, stack);
        if (!remember && !displaced.isEmpty() && !p.getInventory().add(displaced)) p.drop(displaced,false);
        p.inventoryMenu.broadcastChanges();
    }
    private static void chooseWeapon(ServerPlayer p, String group, String id) throws java.io.IOException {
        if(SurvivalGadgets.jetting(p))throw new IllegalStateException("제트팩 이동 중에는 장비를 변경할 수 없습니다.");
        if (!canChoose(p)) throw new IllegalStateException("경기 중에는 리스폰 대기, 자기 팀 스폰 구역 또는 무기고 근처에서 무기를 선택할 수 있습니다.");
        loadouts = Loadouts.load();
        var weapons = group.equals("primary") ? loadouts.primary() : loadouts.secondary();
        var weapon = weapons.stream().filter(w -> w.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("등록되지 않은 무기입니다."));
        int slot = group.equals("primary") ? 0 : 40;
        if (phase.equals("LOBBY")) {
            var previous=FREE_SELECTION.get(p.getUUID());
            if(slot==40&&previous!=null&&!id.equals(previous[1]))GadgetManager.clearDeployables(p);
            grantFree(p,weapon,slot);
            giveSlot9(p);
            String[] choice=FREE_SELECTION.computeIfAbsent(p.getUUID(),ignored->new String[]{"",""});
            choice[slot==0?0:1]=id;
            equipment(p,true); return;
        }
        boolean found = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) { var item = p.getInventory().getItem(i); if (issued(item) && item.getTag().getInt(TAG) == slot) found = true; }
        var entry=PLAYERS.get(p.getUUID());
        boolean previousCommand=slot==40&&entry!=null&&loadouts.secondary().stream().anyMatch(w->w.id().equals(entry.secondary)&&w.commandOnly());
        if (!found && !previousCommand && !RESPAWNING.containsKey(p.getUUID())) throw new IllegalStateException("기존 지급 무기를 인벤토리로 가져온 뒤 교체하세요.");
        // Respawn selection may have no old stack left to inspect.
        if(slot==40&&entry!=null&&!id.equals(entry.secondary))GadgetManager.clearDeployables(p);
        grant(p, weapon, slot, false);
        giveSlot9(p);
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
        if (show && !allowed) p.sendSystemMessage(Component.literal("무기 선택 불가: 자기 팀 스폰 구역 또는 무기고 근처로 이동하세요. 서버 무기 설정도 확인하세요."));
        var choice=FREE_SELECTION.getOrDefault(p.getUUID(),new String[]{"",""});
        var snapshot = new EquipmentSnapshot(allowed, show && allowed, e == null ? "UNASSIGNED" : e.team,
                phase.equals("LOBBY") ? choice[0] : e == null ? "" : e.primary,
                phase.equals("LOBBY") ? choice[1] : e == null ? "" : e.secondary,
                RESPAWNING.containsKey(p.getUUID()) ? Math.max(0,(RESPAWNING.get(p.getUUID()).deadline()-server.getTickCount()+19)/20) : phase.equals("PREPARING") ? Math.max(0, (deadline - server.getTickCount() + 19) / 20) : 0, show && allowed ? loadouts.view() : null,
                phase.equals("RUNNING") && allowed && inSpawn(p));
        var status=new EquipmentSnapshot(snapshot.allowed(),false,snapshot.team(),snapshot.primary(),snapshot.secondary(),snapshot.preparationSeconds(),null,snapshot.spawnHint());
        if (!show && status.equals(LAST_EQUIPMENT.get(p.getUUID()))) return;
        LAST_EQUIPMENT.put(p.getUUID(),status);
        GungangNetwork.send(p, new GungangNetwork.Equipment(ServerFiles.GSON.toJson(snapshot)));
    }
    public static void station(ServerPlayer p,StationBlock.Kind kind,net.minecraft.core.BlockPos pos) {
        try {
            if(kind==StationBlock.Kind.ARMORY){equipment(p,true);return;}
            ensureLobby();add(p);
            if(kind==StationBlock.Kind.WAR_TABLE){reopenLobby(p);return;}
            setTeam(p,kind==StationBlock.Kind.ALPHA?"ALPHA":"BRAVO");
            p.setRespawnPosition(p.level().dimension(),pos.above(),p.getYRot(),true,false);
            broadcast(false);
            p.displayClientMessage(Component.literal(kind==StationBlock.Kind.ALPHA?"ALPHA 팀 선택":"BRAVO 팀 선택"),true);
        } catch(Exception failure){p.sendSystemMessage(Component.literal(String.valueOf(failure.getMessage())).withStyle(ChatFormatting.RED));}
    }
    private static boolean nearArmory(ServerPlayer p) {
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-3,-2,-3),p.blockPosition().offset(3,2,3)))
            if(p.level().getBlockState(pos).is(StationBlocks.ARMORY.get())&&p.distanceToSqr(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5)<=9)return true;
        return false;
    }
    private static boolean canChoose(ServerPlayer p) {
        var e=PLAYERS.get(p.getUUID());
        boolean team=e!=null&&(e.team.equals("ALPHA")||e.team.equals("BRAVO"));
        return LoadoutRules.allowed(phase,p.isAlive(),team,inSpawn(p)||RESPAWNING.containsKey(p.getUUID())||nearArmory(p));
    }
    private static void grantFree(ServerPlayer p,Loadouts.Weapon weapon,int slot) {
        if(slot==40)GadgetManager.changingSecondary(p,weapon);
        String tag="GunGangFreeSlot";
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var old=p.getInventory().getItem(i);
            if(old.hasTag()&&old.getTag().contains(tag)&&old.getTag().getInt(tag)==slot)p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        if(weapon.commandOnly()) {executeSecondaryCommand(p,weapon,slot,tag);return;}
        var displaced=p.getInventory().getItem(slot);
        var stack=weapon.stack();stack.getOrCreateTag().putInt(tag,slot);
        if(slot==40) SecondaryRuntime.configure(p,stack,weapon);
        if(slot==0) LoadoutAttributes.apply(p,weapon);
        p.getInventory().setItem(slot,stack);
        if(!displaced.isEmpty()&&!p.getInventory().add(displaced))p.drop(displaced,false);
        p.inventoryMenu.broadcastChanges();
    }
    static void executeSecondaryCommand(ServerPlayer p,Loadouts.Weapon weapon,int slot,String tag) {
        if(slot!=40)throw new IllegalArgumentException("command는 보조무기에만 사용할 수 있습니다.");
        p.getInventory().setItem(slot,ItemStack.EMPTY);
        try {p.server.getCommands().getDispatcher().execute(weapon.command(),p.createCommandSourceStack().withPermission(2).withSuppressedOutput());}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException error) {throw new IllegalArgumentException("보조무기 command 실행 실패: "+error.getMessage(),error);}
        var stack=p.getOffhandItem();
        if(!stack.isEmpty()) {stack.getOrCreateTag().putInt(tag,slot);SecondaryRuntime.configure(p,stack,weapon);}
        p.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if(activeMap == null || phase.equals("LOBBY")) {
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
            p.setHealth(100);p.setGameMode(GameType.ADVENTURE);
            return;
        }
        var e = PLAYERS.get(p.getUUID()); if (e == null) return;
        server.execute(() -> { teleportSpawn(p, e); if (!e.team.equals("SPECTATOR")) {
            loadouts.primary().stream().filter(w -> w.id().equals(e.primary)).findFirst().ifPresent(w -> grant(p, w, 0, false));
            loadouts.secondary().stream().filter(w -> w.id().equals(e.secondary)).findFirst().ifPresent(w -> grant(p, w, 40, false));
            if (loadouts.slot9() != null) grant(p, loadouts.slot9(), 8, false);
        } });
    }
    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if(event.getEntity() instanceof ServerPlayer waiting && RESPAWNING.containsKey(waiting.getUUID())) { event.setCanceled(true); return; }
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
        var mapViews = files == null ? List.<LobbySnapshot.MapView>of() : files.maps().stream().map(m -> new LobbySnapshot.MapView(m.id(),m.name(),m.description(),
                selectedMode.equals("DEATHMATCH")&&DeathmatchRules.supported(m)?options().deathmatch().timeLimitSeconds():m.timeLimitSeconds(),
                m.normandy()!=null?"노르망디":selectedMode.equals("DEATHMATCH")&&DeathmatchRules.supported(m)?"팀 데스매치":"쟁탈전",DeathmatchRules.supported(m),m.previewImage())).toList();
        List<LobbySnapshot.Member> members = new ArrayList<>();
        for (var entry : PLAYERS.entrySet()) { var player = server.getPlayerList().getPlayer(entry.getKey()); if (player != null) members.add(new LobbySnapshot.Member(entry.getKey().toString(),player.getGameProfile().getName(),entry.getValue().team,entry.getValue().ready)); }
        var snapshot = new LobbySnapshot(enabled, show, p.hasPermissions(2), p.getUUID().toString(), selected, phase,
                phase.equals("LOBBY") ? 0 : Math.max(0,(deadline - server.getTickCount() + 19)/20), mapViews, members, options(),selectedMode);
        GungangNetwork.send(p, new GungangNetwork.Lobby(ServerFiles.GSON.toJson(snapshot)));
    }
    private static void broadcast(boolean show) { if (server != null) for (var p : server.getPlayerList().getPlayers()) send(p, show); }
    public static TeamOptions options() { return files == null ? TeamOptions.defaults() : files.options(); }
    public static boolean respawning(UUID id) { return RESPAWNING.containsKey(id); }
    static boolean beginRespawn(ServerPlayer victim, ServerPlayer killer) {
        var entry=PLAYERS.get(victim.getUUID());
        if(!phase.equals("RUNNING")||entry==null||(!entry.team.equals("ALPHA")&&!entry.team.equals("BRAVO"))||RESPAWNING.containsKey(victim.getUUID())) return false;
        var message=victim.getCombatTracker().getDeathMessage();
        int duration=options().respawnSeconds()*20;
        RESPAWNING.put(victim.getUUID(),new Respawning(server.getTickCount()+duration,duration,killer==null?null:killer.getUUID()));
        victim.setHealth(victim.getMaxHealth()); victim.removeAllEffects(); victim.clearFire(); victim.fallDistance=0;
        victim.setGameMode(GameType.SPECTATOR);
        if(killer!=null&&killer!=victim&&killer.isAlive()&&!killer.isSpectator()&&killer.level()==victim.level()) victim.setCamera(killer);
        for(var player:server.getPlayerList().getPlayers()) player.sendSystemMessage(message);
        equipment(victim,false);
        return true;
    }
    private static void tickRespawns(int now) {
        for(var id:List.copyOf(RESPAWNING.keySet())) {
            var wait=RESPAWNING.get(id); var p=server.getPlayerList().getPlayer(id); var entry=PLAYERS.get(id);
            if(p==null||entry==null||activeMap==null||!phase.equals("RUNNING")) { RESPAWNING.remove(id); continue; }
            if(now>=wait.deadline()) {
                RESPAWNING.remove(id); p.setCamera(p); teleportSpawn(p,entry);
                p.setHealth(p.getMaxHealth()); p.removeAllEffects(); p.clearFire(); p.setAirSupply(p.getMaxAirSupply());
                p.getFoodData().setFoodLevel(20); p.getFoodData().setSaturation(5); p.setAbsorptionAmount(0);
                p.setGameMode(GameType.byName(activeMap.gameMode()));
                if(activeMap.normandy()!=null&&entry.team.equals("BRAVO"))
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,60,5,false,false));
                loadouts.primary().stream().filter(w->w.id().equals(entry.primary)).findFirst().ifPresent(w->grant(p,w,0,false));
                loadouts.secondary().stream().filter(w->w.id().equals(entry.secondary)).findFirst().ifPresent(w->grant(p,w,40,false));
                if(loadouts.slot9()!=null) grant(p,loadouts.slot9(),8,false);
                equipment(p,false);
            } else {
                if(!p.isSpectator()) p.setGameMode(GameType.SPECTATOR);
                var killer=wait.killer()==null?null:server.getPlayerList().getPlayer(wait.killer());
                var camera=killer!=null&&killer!=p&&killer.isAlive()&&!killer.isSpectator()&&killer.level()==p.level()?killer:p;
                if(p.getCamera()!=camera) p.setCamera(camera);
                if(camera==p) teleportSpawn(p,entry);
            }
        }
    }
    public static List<MatchSnapshot.Respawn> respawns() {
        return RESPAWNING.entrySet().stream().map(e->new MatchSnapshot.Respawn(e.getKey().toString(),Math.max(0,e.getValue().deadline()-server.getTickCount()),e.getValue().duration())).toList();
    }
    private static void resetPlayer(ServerPlayer p) {
        LoadoutAttributes.clear(p);GadgetManager.clear(p);SecondaryRuntime.clear(p);
        RESPAWNING.remove(p.getUUID());FREE_SELECTION.remove(p.getUUID());LAST_EQUIPMENT.remove(p.getUUID());
        p.setCamera(p);p.stopRiding();p.removeAllEffects();p.clearFire();p.fallDistance=0;
        clearPlayerInventory(p);
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        p.setHealth(100);p.setAbsorptionAmount(0);p.setGameMode(GameType.ADVENTURE);
        p.setRespawnPosition(Level.OVERWORLD,HOME,0,true,false);
        p.teleportTo(p.server.overworld(),HOME.getX(),HOME.getY(),HOME.getZ(),0,0);
    }
    private static void reset() {
        GimmickEpoch.reset(server);
        close();GadgetManager.clearAll(server);CombatGadgets.reset(server);
        server.overworld().setDefaultSpawnPos(HOME,0);
        for(var p:server.getPlayerList().getPlayers()) {resetPlayer(p);equipment(p,false);}
        broadcast(false);
    }
    private LobbyManager() { }
}
