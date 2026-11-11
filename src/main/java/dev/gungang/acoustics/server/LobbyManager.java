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
    /** {@code spot} is where a suicide happened (no killer, or the victim's own damage); the wait is spectated there. */
    private record Respawning(int deadline, int duration, UUID killer, ResourceKey<Level> dimension, Vec3 spot) { }
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
        try { ProgressionService.open(); } catch (Exception failure) { LogUtils.getLogger().error("GunGang player database could not be loaded", failure); }
        try { reload(); } catch (Exception failure) { LogUtils.getLogger().error("GunGang config could not be loaded; previous files were not overwritten", failure); }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { if (enabled) close(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { server = null; files = null; loadouts = null; PLAYERS.clear(); }
    private static void reload() throws Exception {
        reload(true);
    }
    private static void reload(boolean force) throws Exception {
        BombDrops.clear(server);
        if (!phase.equals("LOBBY")) throw new IllegalStateException("경기 중에는 설정을 다시 읽을 수 없습니다.");
        var candidate = ServerFiles.load();
        var weapons = Loadouts.load();
        GadgetConfig.load();
        SafeZones.load(server);
        ProgressionService.configure(server);
        if (!force && candidate.equals(files) && weapons.equals(loadouts)) return;
        files = candidate; loadouts = weapons;
        MapPreviewGenerator.clear();
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
    static void requireMapEditing() {
        if(!phase.equals("LOBBY"))throw new IllegalStateException("경기 준비/진행 중에는 맵 영역을 변경할 수 없습니다.");
    }
    static void refreshModifiedSettings() throws Exception {reload(true);}
    /** The weapon catalog the server runs with (replaced on every reload / live patch). */
    static Loadouts loaded() {return loadouts;}
    static String phase() {return phase;}
    /**
     * Live weapon patch: re-reads the weapon catalog without the lobby-only full reload (ready states and the match
     * are kept), then re-applies health/speed and the item name for everyone currently using that primary weapon.
     */
    static void refreshPatchedWeapon(String id) throws java.io.IOException {
        var weapons=Loadouts.load();
        loadouts=weapons;
        var weapon=weapons.primary().stream().filter(w->w.id().equals(id)).findFirst().orElse(null);
        if(weapon!=null&&server!=null)for(var p:server.getPlayerList().getPlayers()){
            String current;
            if(phase.equals("LOBBY")){var free=FREE_SELECTION.get(p.getUUID());current=free==null?null:free[0];}
            else{var e=PLAYERS.get(p.getUUID());current=e==null?null:e.primary;}
            if(!id.equals(current)||!p.isAlive()||!LoadoutAttributes.active(p))continue;
            LoadoutAttributes.apply(p,weapon);
            for(int i=0;i<p.getInventory().getContainerSize();i++){
                var stack=p.getInventory().getItem(i);
                if(stack.hasTag()&&(stack.getTag().contains(TAG)&&stack.getTag().getInt(TAG)==0||stack.getTag().contains("GunGangFreeSlot")&&stack.getTag().getInt("GunGangFreeSlot")==0))
                    stack.setHoverName(Component.literal(weapon.name()));
            }
            p.inventoryMenu.broadcastChanges();
        }
        broadcast(false);
    }
    static void refreshEditedMaps() throws java.io.IOException {
        requireMapEditing();
        var maps=ServerFiles.loadMaps(ServerFiles.root());
        if(files==null)return;
        files=new ServerFiles.Loaded(files.acoustics(),maps,files.options());
        MapPreviewGenerator.clear();
        if(maps.stream().noneMatch(m->m.id().equals(selected)))selected=maps.isEmpty()?"":maps.get(0).id();
        normalizeMode();PLAYERS.values().forEach(e->e.ready=false);broadcast(false);
    }
    private static void syncAcoustics(ServerPlayer p) {
        SafeZones.sync(p);
        if (files != null && server.isDedicatedServer()) GungangNetwork.send(p, new GungangNetwork.Acoustics(ServerFiles.GSON.toJson(files.acoustics())));
    }
    @SubscribeEvent public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        syncAcoustics(p);
        resetPlayer(p);
        // Joining only synchronizes lobby state; opening the menu requires an explicit action.
        if (enabled) { add(p); broadcast(false); }
        else send(p,false); // Team station colors are needed even before the lobby is opened.
        p.setGameMode(GameType.ADVENTURE);
    }
    @SubscribeEvent public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Emotes.forget(p.getUUID());
        if(MatchManager.propHunt()!=null)MatchManager.propHunt().leave(p);
        if(MatchManager.freeForAll()!=null)MatchManager.freeForAll().leave(p);
        Entry entry = PLAYERS.remove(p.getUUID());
        if (entry != null) restore(p, entry, true);
        LAST_ACTION.remove(p.getUUID());
        LAST_PREVIEW.remove(p.getUUID());
        FREE_SELECTION.remove(p.getUUID());
        LAST_EQUIPMENT.remove(p.getUUID());
        // A departure must never trigger an unexpected match start.
        if (phase.equals("LOBBY") && entry!=null && (entry.team.equals("ALPHA")||entry.team.equals("BRAVO")))
            PLAYERS.values().forEach(e -> e.ready = false);
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
        BotManager.clearLobby();
        enabled = false; phase = "LOBBY";
        for (var p : server.getPlayerList().getPlayers()) { Entry e = PLAYERS.get(p.getUUID()); if (e != null) restore(p, e, true); }
        broadcast(false); PLAYERS.clear(); activeMap = null;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("gungang");
        BotManager.register(root);
        HudPreferences.register(root);
        AreaEditor.register(root);
        ConfigModify.register(root);
        DebugPreview.register(root);
        root.then(Commands.literal("escorttrack").requires(s->s.hasPermission(2)).executes(c->run(c.getSource(),()->{
            if(!phase.equals("LOBBY"))throw new IllegalStateException("로비에서만 선로를 갱신할 수 있습니다.");
            var arena=selectedArena();if(arena==null||arena.escort()==null)throw new IllegalArgumentException("호위 맵을 먼저 선택하세요.");
            var route=EscortMatch.validate(server,arena);EscortTracks.get(level(arena)).rebuild(level(arena),route);
            c.getSource().sendSuccess(()->Component.literal("호위 선로 생성 완료: "+Math.round(route.length)+"m / 체크포인트 "+route.checkpoints.size()+"개"),false);
        })));
        root.then(Commands.literal("lobby").executes(c -> run(c.getSource(), () -> {
            reopenLobby(c.getSource().getPlayerOrException());
        }))
                .then(Commands.literal("open").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::open)))
                .then(Commands.literal("close").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::close))));
        root.then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), () -> { reload(); c.getSource().sendSuccess(() -> Component.literal("GunGang 설정을 다시 읽었습니다."), false); }))
                .then(Commands.literal("map_data").executes(c -> run(c.getSource(), () -> purgeMapData(c.getSource(), null)))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests((c,b) -> { if (files != null) files.maps().forEach(m -> b.suggest(m.id())); return b.buildFuture(); })
                                .executes(c -> run(c.getSource(), () -> purgeMapData(c.getSource(), StringArgumentType.getString(c,"id")))))));
        // Modded clients answer "/gungang prop list" themselves (PropListScreen's client command); this is the
        // fallback for the console and vanilla clients, and "off" for everyone who disguised from the list.
        root.then(Commands.literal("prop").then(Commands.literal("list").executes(c -> {
            if (MatchManager.active()) { c.getSource().sendFailure(Component.literal("경기 준비·진행 중에는 변신 블록 목록을 사용할 수 없습니다.")); return 0; }
            if (c.getSource().getEntity() instanceof ServerPlayer p) { openPropList(p); return 1; }
            for (var line : PropCatalog.listing()) c.getSource().sendSystemMessage(line);
            return 1;
        })).then(Commands.literal("off").executes(c -> { propMorph(c.getSource().getPlayerOrException(), "off"); return 1; })));
        root.then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::finish)));
        root.then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), LobbyManager::reset)));
        root.then(Commands.literal("cleardeployables").requires(s->s.hasPermission(2)).executes(c->run(c.getSource(),()->{
            int removed=GadgetManager.clearAll(c.getSource().getServer());
            c.getSource().sendSuccess(()->Component.literal("지뢰·방벽·컴파운드 보우 화살 "+removed+"개 제거, 미로드 설치물·화살도 무효화했습니다."),true);
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
        root.then(Commands.literal("cooldown").requires(s -> s.hasPermission(2)).then(Commands.literal("reset")
                .executes(c -> run(c.getSource(), () -> resetCooldowns(c.getSource(), List.of(c.getSource().getPlayerOrException()))))
                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                        .executes(c -> run(c.getSource(), () -> resetCooldowns(c.getSource(), net.minecraft.commands.arguments.EntityArgument.getPlayers(c, "targets")))))));
        root.then(Commands.literal("forcestart").requires(s -> s.hasPermission(2)).executes(c -> run(c.getSource(), () -> {
            forceStart(); c.getSource().sendSuccess(() -> Component.literal("준비 상태와 관계없이 현재 팀 배정대로 경기를 시작합니다."), true);
        })));
        root.then(Commands.literal("ready").executes(c -> { action(c.getSource().getPlayerOrException(), new GungangNetwork.Action("ready", "", "")); return 1; }));
        root.then(Commands.literal("team").then(Commands.argument("team", StringArgumentType.word()).suggests((c,b) -> { b.suggest("ALPHA"); b.suggest("BRAVO"); b.suggest("SPECTATOR"); return b.buildFuture(); })
                .executes(c -> { action(c.getSource().getPlayerOrException(), new GungangNetwork.Action("team", StringArgumentType.getString(c,"team").toUpperCase(Locale.ROOT), "")); return 1; })));
        root.then(Commands.literal("map").requires(s -> s.hasPermission(2)).then(Commands.argument("id", StringArgumentType.word()).suggests((c,b) -> { if (files != null) { b.suggest(LobbyRules.RANDOM_MAP); files.maps().forEach(m -> b.suggest(m.id())); } return b.buildFuture(); })
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
    private static void purgeMapData(net.minecraft.commands.CommandSourceStack source, String id) throws Exception {
        if (id != null && !id.matches("[a-z0-9_-]{1,48}")) throw new IllegalArgumentException("맵 ID: 영문 소문자/숫자/_/- 만 가능합니다.");
        int removed = MapPreviewGenerator.purge(id);
        source.sendSuccess(() -> Component.literal("저장된 맵 지도 " + removed + "개를 삭제했습니다. 다음 미리보기 때 다시 생성합니다."), false);
    }
    /**
     * Disguise button of the block list: outside matches anyone becomes the chosen block (by catalog number) with the
     * hider controls of the prop hunt test session; "off" restores inventory, game mode and health.
     */
    /** The prop list screen (/gungang prop list, the capsule machine): lobby only; vanilla clients get the text listing. */
    static void openPropList(ServerPlayer p) {
        if (MatchManager.active()) { p.displayClientMessage(Component.literal("경기 준비·진행 중에는 변신 블록 목록을 사용할 수 없습니다.").withStyle(ChatFormatting.RED), true); return; }
        if (GungangNetwork.installed(p)) { GungangNetwork.send(p, new GungangNetwork.PropList()); return; }
        for (var line : PropCatalog.listing()) p.sendSystemMessage(line);
    }
    static void propMorph(ServerPlayer p, String value) {
        if (MatchManager.active()) { p.sendSystemMessage(Component.literal("경기 준비·진행 중에는 변신할 수 없습니다.").withStyle(net.minecraft.ChatFormatting.RED)); return; }
        if (value.equals("off")) {
            if (!PropHuntMatch.testing(p.getUUID())) { p.sendSystemMessage(Component.literal("변신 중이 아닙니다.").withStyle(net.minecraft.ChatFormatting.GRAY)); return; }
            PropHuntMatch.test(p, null, "");
            p.sendSystemMessage(Component.literal("변신을 해제했습니다. 인벤토리·게임 모드·체력을 되돌렸습니다."));
            return;
        }
        String type = PropCatalog.type(value);
        if (type == null) { p.sendSystemMessage(Component.literal("변신할 수 없는 블록입니다.").withStyle(net.minecraft.ChatFormatting.RED)); return; }
        try {
            String block = PropCatalog.variant(type, new Random());
            PropHuntMatch.test(p, "ALPHA", block);
            p.sendSystemMessage(Component.literal("변신: ").append(PropCatalog.name(block)).append(Component.literal(" [" + PropCatalog.number(block) + "] · 숨는 팀과 같은 조작·체력 50. 목록의 변신 해제 버튼 또는 /gungang prop off로 해제")));
        } catch (RuntimeException failure) { p.sendSystemMessage(Component.literal(String.valueOf(failure.getMessage())).withStyle(net.minecraft.ChatFormatting.RED)); }
    }
    private interface Work { void run() throws Exception; }
    private static int run(net.minecraft.commands.CommandSourceStack source, Work work) {
        try { work.run(); return 1; } catch (Exception error) { source.sendFailure(Component.literal(String.valueOf(error.getMessage()))); return 0; }
    }
    public static void action(ServerPlayer sender, GungangNetwork.Action action) {
        if (server == null) return;
        if(action.action().equals("m95Mode")){M95.cycle(sender);return;}
        if(action.action().equals("turretReload")){Turrets.key(sender,action.value());return;}
        if(action.action().equals("gimmickAttack")){GimmickEvents.request(sender);return;}
        if(action.action().equals("airCannon")){WindBlower.fire(sender);return;}
        if(action.action().equals("shurikenThrow")){ShurikenItem.attack(sender);return;}
        if(action.action().equals("shurikenMode")){ShurikenItem.cycleMode(sender);return;}
        if(action.action().equals("shurikenReload")){ShurikenItem.reload(sender);return;}
        if(action.action().equals("shurikenJump")){ShurikenItem.doubleJump(sender);return;}
        if(action.action().equals("shurikenClimb")){ShurikenItem.climb(sender);return;}
        if(action.action().equals("bladesReset")){TwinBlades.resetCombo(sender);return;}
        if(action.action().equals("defibTarget")){Defibrillator.choose(sender,action.value());return;}
        if(action.action().equals("rapierAttack")){int aimed;try{aimed=action.value().isEmpty()?-1:Integer.parseInt(action.value());}catch(NumberFormatException e){aimed=-1;}RapierItem.thrust(sender,aimed);return;}
        // Plant/defuse key: press and release must both arrive, so it bypasses the action rate limit.
        if(action.action().equals("bomb")){var bomb=MatchManager.bomb();if(bomb!=null)bomb.key(sender,action.value().equals("down"));return;}
        if(action.action().equals("emote")){Emotes.request(sender,action.value());return;}
        if(action.action().equals("prop")){if(MatchManager.propHunt()!=null)MatchManager.propHunt().action(sender,action.value());return;}
        if(action.action().equals("place")||action.action().equals("cancelplace")) { GadgetManager.action(sender,action.action());return; }
        int now = server.getTickCount();
        boolean preview=action.action().equals("mapPreview")||action.action().equals("weaponPreview");
        var rate=preview?LAST_PREVIEW:LAST_ACTION;
        // Weapon previews are a cached item stack each (the loadout grid asks for a whole category); map previews are images.
        if (now - rate.getOrDefault(sender.getUUID(), -100) < (action.action().equals("weaponPreview")?2:preview?10:4)) return;
        rate.put(sender.getUUID(), now);
        try {
            if(action.action().equals("bombSpectate")){
                var bomb=MatchManager.bomb();
                if(bomb!=null&&bomb.dead(sender.getUUID()))bomb.cycleSpectator(sender,action.value().equals("next")?1:action.value().equals("previous")?-1:0);
                return;
            }
            if(action.action().equals("lobby")){reopenLobby(sender);return;}
            if(action.action().equals("propMorph")){propMorph(sender,action.value());return;}
            if(action.action().equals("mapPreview")) {
                if(files==null)return;
                var map=files.maps().stream().filter(m->m.id().equals(action.value())).findFirst().orElse(null);
                if(map==null)return;
                if(map.previewArea()!=null){MapPreviewGenerator.request(sender,map);return;}
                byte[] image=new byte[0];String error="미리보기 이미지가 지정되지 않았습니다.";
                if(!map.previewImage().isEmpty())try{image=MapPreviewFiles.read(ServerFiles.root().resolve("server/maps/preview"),map.previewImage());error="";}
                catch(java.io.IOException failure){error="미리보기 이미지를 읽을 수 없습니다.";com.mojang.logging.LogUtils.getLogger().warn("Map preview {}: {}",map.id(),failure.getMessage());}
                GungangNetwork.send(sender,new GungangNetwork.MapPreview(map.id(),image,error));return;
            }
            if(action.action().equals("weaponPreview")) {
                if(loadouts==null||!canChoose(sender))return;
                var group=action.target().equals("primary")?catalog().primary():catalog().secondary();
                var weapon=group.stream().filter(w->w.id().equals(action.value())).findFirst().orElse(null);
                if(weapon!=null)GungangNetwork.send(sender,new GungangNetwork.WeaponPreview(action.target()+":"+weapon.id(),weapon.stack()));
                return;
            }
            if (action.action().equals("equipment")) { equipment(sender, true); return; }
            if (action.action().equals("weaponPick")) { try { WeaponRotation.pick(sender, Integer.parseInt(action.value())-1); } catch (NumberFormatException ignored) { } return; }
            if (action.action().equals("primary") || action.action().equals("secondary")) { chooseWeapon(sender, action.action(), action.value()); return; }
            if (action.action().equals("randomLoadout")) { randomLoadout(sender); return; }
            if (action.action().startsWith("preset")) { preset(sender, action); return; }
            if (action.action().equals("effectColor")) { EffectColors.choose(sender, action.value(), action.target()); return; }
            if (action.action().equals("effectColorSave") || action.action().equals("effectColorForget")) { EffectColors.savedColor(sender, action.value(), action.action().equals("effectColorSave")); return; }
            if (!enabled || !PLAYERS.containsKey(sender.getUUID())) return;
            if (!phase.equals("LOBBY")) throw new IllegalStateException("준비 또는 경기 진행 중에는 로비 설정을 변경할 수 없습니다.");
            switch (action.action()) {
                case "gameOptions" -> {
                    admin(sender);
                    var game=ServerFiles.GSON.fromJson(action.value(),GameOptions.class);
                    if(game==null)throw new IllegalArgumentException("게임 옵션이 없습니다.");
                    var arena=selectedArena();
                    if(arena!=null)game.apply(modeArena(arena,selectedMode,game),selectedMode);
                    var old=options();var next=new TeamOptions(old.alphaColor(),old.bravoColor(),old.respawnSeconds(),old.deathmatch(),old.experience(),game);
                    var path=ServerFiles.root().resolve("server/option.json");
                    var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
                    json.add("game",ServerFiles.GSON.toJsonTree(game));
                    java.nio.file.Files.writeString(path,ServerFiles.GSON.toJson(json),java.nio.charset.StandardCharsets.UTF_8);
                    files=new ServerFiles.Loaded(files.acoustics(),files.maps(),next);
                    PLAYERS.values().forEach(e->e.ready=false);
                }
                case "botAdd" -> {
                    admin(sender);
                    if(!LobbyRules.bots(selectedMode))throw new IllegalArgumentException("이 모드에서는 AI 봇을 추가할 수 없습니다.");
                    BotManager.lobbyAdd(catalog());botChanged();return;
                }
                case "botRemove" -> {admin(sender);BotManager.lobbyRemove(action.target().isEmpty()?null:UUID.fromString(action.target()));botChanged();return;}
                case "map" -> { admin(sender); select(action.value()); }
                case "mode" -> { admin(sender); if(!action.target().isEmpty()&&!selected.equals(action.target())){send(sender,false);return;} selectMode(action.value()); }
                case "random" -> { admin(sender); randomize(); }
                case "team" -> {
                    UUID target = action.target().isEmpty() ? sender.getUUID() : UUID.fromString(action.target());
                    if (!target.equals(sender.getUUID())) admin(sender);
                    if(BotManager.identity(target)){boolean changed=!BotManager.team(target).equals(action.value());BotManager.lobbyTeam(target,action.value());if(changed)botChanged();else send(sender,false);return;}
                    var player = server.getPlayerList().getPlayer(target);
                    if (player == null || !PLAYERS.containsKey(target)) return;
                    setTeam(player, action.value());
                }
                case "swap" -> {
                    // Two lobby members trade panels (administrators): each takes the other's team, a spectator/unassigned slot becomes SPECTATOR.
                    admin(sender);
                    UUID first=UUID.fromString(action.value()),second=UUID.fromString(action.target());
                    String a=lobbyTeam(first),b=lobbyTeam(second);
                    if(a==null||b==null)throw new IllegalArgumentException("로비에 없는 참가자입니다.");
                    if(a.equals(b)){send(sender,false);return;}
                    if(BotManager.identity(first)&&!Set.of("ALPHA","BRAVO").contains(b)||BotManager.identity(second)&&!Set.of("ALPHA","BRAVO").contains(a))
                        throw new IllegalArgumentException("봇은 관전석으로 옮길 수 없습니다.");
                    moveMember(first,b);moveMember(second,a);
                    if(BotManager.identity(first)||BotManager.identity(second))botChanged();
                }
                case "ready" -> {
                    if(!action.target().isEmpty()&&!action.target().equals(selected+":"+selectedMode)){send(sender,false);return;}
                    var e = PLAYERS.get(sender.getUUID());
                    if (e.team.equals("ALPHA") || e.team.equals("BRAVO"))
                        e.ready = action.value().isEmpty() ? !e.ready : Boolean.parseBoolean(action.value());
                }
                default -> { return; }
            }
            if (allReady()) prepare();
            broadcast(false);
        } catch (Exception failure) {
            if(action.action().equals("gameOptions"))LogUtils.getLogger().warn("Could not apply lobby game options from {}",sender.getGameProfile().getName(),failure);
            sender.sendSystemMessage(Component.literal(String.valueOf(failure.getMessage())).withStyle(ChatFormatting.RED));
        }
    }
    /** A lobby member's panel team (ALPHA, BRAVO, else SPECTATOR), or null when the id is not in the lobby. */
    private static String lobbyTeam(UUID id){
        String team;
        if(BotManager.identity(id)){if(!BotManager.lobbyIds().contains(id))return null;team=BotManager.team(id);}
        else{var e=PLAYERS.get(id);if(e==null||server.getPlayerList().getPlayer(id)==null)return null;team=e.team;}
        return team.equals("ALPHA")||team.equals("BRAVO")?team:"SPECTATOR";
    }
    private static void moveMember(UUID id,String team){
        if(BotManager.identity(id)){BotManager.lobbyTeam(id,team);return;}
        setTeam(server.getPlayerList().getPlayer(id),team);
    }
    private static void admin(ServerPlayer p) { if (!p.hasPermissions(2)) throw new IllegalArgumentException("관리자 권한이 필요합니다."); }
    private static void select(String id) {
        if (LobbyRules.RANDOM_MAP.equals(id) && files != null && files.maps().stream().noneMatch(m -> m.id().equals(id))) { randomMap(); return; }
        if (!enabled || !phase.equals("LOBBY") || files.maps().stream().noneMatch(m -> m.id().equals(id))) throw new IllegalArgumentException("선택할 수 없는 맵입니다.");
        if(selected.equals(id))return;
        var candidate=files.maps().stream().filter(m->m.id().equals(id)).findFirst().orElseThrow();
        if(candidate.escort()!=null){var route=EscortMatch.validate(server,candidate);EscortTracks.get(level(candidate)).rebuild(level(candidate),route);}
        selected = id; normalizeMode(); PLAYERS.values().forEach(e -> e.ready = false); broadcast(false);
    }
    private static List<String> modes(ArenaMap arena) {
        if(arena.propHunt()!=null)return List.of("PROP_HUNT");
        var modes=new ArrayList<>(arena.freeForAll()!=null?(arena.bomb()!=null?List.of("BOMB","FREE_FOR_ALL","DEATHMATCH"):List.of("FREE_FOR_ALL","DEATHMATCH")):arena.escort()!=null?List.of("ESCORT","DEATHMATCH"):LobbyRules.modes(arena.normandy() != null, arena.capture() != null, arena.bomb() != null));
        if(PropHuntRules.supported(arena))modes.add("PROP_HUNT");
        return modes;
    }
    /** Random selection keeps to a map's own modes; prop hunt on an ordinary map needs a deliberate pick. */
    private static List<String> randomModes(ArenaMap arena) {
        var modes=modes(arena);
        return arena.propHunt()!=null?modes:modes.stream().filter(m->!m.equals("PROP_HUNT")).toList();
    }
    /** The lobby's random row: a random map (another one when possible), then a random mode that map supports. */
    private static void randomMap() {
        if (!enabled || !phase.equals("LOBBY") || files.maps().isEmpty()) throw new IllegalArgumentException("선택할 수 있는 맵이 없습니다.");
        var random = new Random();
        var current = selectedArena();
        // Rerolling the same map is allowed only when it offers another mode.
        var maps = files.maps().stream().filter(m -> m != current || randomModes(m).size() > 1).toList();
        ArenaMap map = LobbyRules.pick(maps.isEmpty() ? files.maps() : maps, current, random);
        String mode = LobbyRules.pick(randomModes(map), map == current ? selectedMode : null, random);
        if(map.escort()!=null){var route=EscortMatch.validate(server,map);EscortTracks.get(level(map)).rebuild(level(map),route);}
        selected = map.id(); selectedMode = mode;
        PLAYERS.values().forEach(e -> e.ready = false);
        String label = switch (mode) { case "PROP_HUNT" -> "숨바꼭질"; case "FREE_FOR_ALL" -> "개인전"; case "ESCORT" -> "호위"; case "BOMB" -> "폭탄전"; case "DEATHMATCH" -> "팀 데스매치"; case "NORMANDY" -> "이동점령"; default -> "쟁탈전"; };
        var message = Component.literal("랜덤 선택: " + map.name() + " · " + label).withStyle(ChatFormatting.GOLD);
        PLAYERS.keySet().forEach(id -> { var player = server.getPlayerList().getPlayer(id); if (player != null) player.sendSystemMessage(message); });
        broadcast(false);
    }
    /** Random primary and secondary, each different from the current pick when the catalog allows. */
    private static void randomLoadout(ServerPlayer p) throws java.io.IOException {
        loadouts = Loadouts.load();
        var random = new Random();
        var entry = PLAYERS.get(p.getUUID());
        String[] free = FREE_SELECTION.get(p.getUUID());
        boolean lobby = phase.equals("LOBBY");
        String currentPrimary = lobby ? (free == null ? null : free[0]) : entry == null ? null : entry.primary;
        String currentSecondary = lobby ? (free == null ? null : free[1]) : entry == null ? null : entry.secondary;
        String primary = LobbyRules.pick(catalog().primary().stream().map(Loadouts.Weapon::id).filter(id->!weaponTaken(p.getUUID(),true,id)).toList(), currentPrimary, random);
        String secondary = LobbyRules.pick(catalog().secondary().stream().map(Loadouts.Weapon::id).filter(id->!weaponTaken(p.getUUID(),false,id)).toList(), currentSecondary, random);
        if (primary != null) chooseWeapon(p, "primary", primary);
        // A rejected secondary (e.g. bomb carrier) keeps the new primary and reports why.
        if (secondary != null) try { chooseWeapon(p, "secondary", secondary); }
        catch (IllegalStateException refused) { p.sendSystemMessage(Component.literal(String.valueOf(refused.getMessage())).withStyle(ChatFormatting.RED)); }
    }
    /** The default preset wins at match start; allocation resolves unavailable ids from unused weapons. */
    private static String[] startChoice(ServerPlayer p,String[] fallback) {
        var preset=ProgressionService.defaultPreset(p);
        return preset==null?fallback.clone():new String[]{preset.primary(),preset.secondary()};
    }
    private static void preset(ServerPlayer p,GungangNetwork.Action action) throws java.io.IOException {
        int slot;
        try { slot=Integer.parseInt(action.value()); } catch (NumberFormatException invalid) { slot=0; }
        if(slot<1||slot>PlayerRecord.PRESETS)throw new IllegalArgumentException("프리셋 번호는 1~"+PlayerRecord.PRESETS+" 사이여야 합니다.");
        var record=ProgressionService.record(p);
        var existing=record.preset(slot);
        if(existing==null&&!action.action().equals("presetSave"))throw new IllegalArgumentException("비어 있는 프리셋입니다.");
        String message;
        switch(action.action()) {
            case "presetSave" -> {
                loadouts=Loadouts.load();
                var entry=PLAYERS.get(p.getUUID());
                String[] current=phase.equals("LOBBY")?FREE_SELECTION.getOrDefault(p.getUUID(),new String[]{"",""}):entry==null?new String[]{"",""}:new String[]{entry.primary,entry.secondary};
                // Nothing chosen yet means the first catalog weapon, which is what the match would issue.
                String primary=catalog().primary().stream().map(Loadouts.Weapon::id).filter(current[0]::equals).findFirst().orElse(catalog().primary().get(0).id());
                String secondary=catalog().secondary().stream().map(Loadouts.Weapon::id).filter(current[1]::equals).findFirst().orElse(catalog().secondary().get(0).id());
                var saved=new PlayerRecord.Preset(slot,existing==null?"":existing.name(),primary,secondary);
                ProgressionService.store(record.withPreset(saved));message=saved.name()+"에 현재 장비를 저장했습니다.";
            }
            case "presetRename" -> {
                var renamed=new PlayerRecord.Preset(slot,action.target(),existing.primary(),existing.secondary());
                ProgressionService.store(record.withPreset(renamed));message="프리셋 이름을 '"+renamed.name()+"'(으)로 변경했습니다.";
            }
            case "presetDelete" -> {ProgressionService.store(record.withoutPreset(slot));message=existing.name()+"을(를) 삭제했습니다.";}
            case "presetDefault" -> {
                boolean clear=record.defaultPreset()==slot;
                ProgressionService.store(record.withDefault(clear?0:slot));
                message=clear?"기본 프리셋을 해제했습니다.":existing.name()+"을(를) 경기 시작 기본 장비로 설정했습니다.";
            }
            case "presetApply" -> {
                choosePresetWeapon(p,true,existing.primary());
                // A rejected secondary (e.g. bomb carrier) keeps the new primary and reports why.
                try { choosePresetWeapon(p,false,existing.secondary()); }
                catch (IllegalStateException refused) { p.sendSystemMessage(Component.literal(String.valueOf(refused.getMessage())).withStyle(ChatFormatting.RED)); }
                return;
            }
            default -> { return; }
        }
        p.displayClientMessage(Component.literal(message),true);
        equipment(p,true);
    }
    /** Ids of the loaded maps, for command suggestions. */
    static List<String> mapIds() { return files==null?List.of():files.maps().stream().map(ArenaMap::id).toList(); }
    private static ArenaMap selectedArena() { return files==null?null:files.maps().stream().filter(m->m.id().equals(selected)).findFirst().orElse(null); }
    private static void normalizeMode() {
        var arena=selectedArena();
        if(arena!=null&&modes(arena).contains(selectedMode))return;
        if(arena!=null&&arena.propHunt()!=null){selectedMode="PROP_HUNT";return;}
        if(selectedMode.equals("PROP_HUNT")&&PropHuntRules.supported(arena))return;
        if(arena!=null&&arena.freeForAll()!=null&&arena.bomb()==null){if(!selectedMode.equals("DEATHMATCH"))selectedMode="FREE_FOR_ALL";return;}
        if(arena!=null&&arena.escort()!=null){if(!selectedMode.equals("DEATHMATCH"))selectedMode="ESCORT";}
        else if(arena!=null&&arena.normandy()!=null)selectedMode="NORMANDY";
        else if(DeathmatchRules.exclusive(arena))selectedMode="DEATHMATCH";
        else if(arena!=null&&arena.bomb()!=null&&!selectedMode.equals("DEATHMATCH"))selectedMode="BOMB";
        else if(!selectedMode.equals("DEATHMATCH")||!DeathmatchRules.supported(arena))selectedMode="CONTROL";
    }
    private static void selectMode(String mode) {
        var arena=selectedArena();
        if(arena==null||!modes(arena).contains(mode)
                ||(mode.equals("BOMB")&&arena.bomb()==null)||(mode.equals("CONTROL")&&arena.capture()==null)
                ||(mode.equals("DEATHMATCH")&&!DeathmatchRules.supported(arena)))throw new IllegalArgumentException("선택할 수 없는 경기 모드입니다.");
        if(selectedMode.equals(mode))return;
        selectedMode=mode;PLAYERS.values().forEach(e->e.ready=false);
    }
    private static void setTeam(ServerPlayer p, String team) {
        if (!Set.of("ALPHA", "BRAVO", "SPECTATOR").contains(team)) throw new IllegalArgumentException("ALPHA / BRAVO / SPECTATOR 중 선택하세요.");
        Entry e = PLAYERS.get(p.getUUID());
        if(e.team.equals(team))return;
        e.team = team;
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
        if(MatchManager.realPropHunt()!=null)throw new IllegalStateException("숨바꼭질 도중에는 참가할 수 없습니다. 다음 경기를 기다려 주세요.");
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
        String[] choice=startChoice(p,new String[]{"",""});
        var primary=availableWeapon(p,team,true,choice[0]);
        var secondary=availableWeapon(p,team,false,choice[1]);
        if(existing==null) add(p);
        setTeam(p,team);
        if(MatchManager.freeForAll()!=null)MatchManager.freeForAll().add(p);
        var entry=PLAYERS.get(p.getUUID());
        teleportSpawn(p,entry); p.setGameMode(GameType.byName(activeMap.gameMode()));
        entry.primary=primary.id(); entry.secondary=secondary.id();
        grant(p,primary,0,true); grant(p,secondary,40,true);
        notifyReplacement(p,true,choice[0],primary.id());notifyReplacement(p,false,choice[1],secondary.id());
        if(loadouts.slot9()!=null) grant(p,loadouts.slot9(),8,true);
        MatchManager.participant(p); WeaponRotation.joined(p); equipment(p,!WeaponRotation.active()); broadcast(false);
        if(MatchManager.bomb()!=null)MatchManager.bomb().lateJoin(p);
    }
    private static void resetCooldowns(net.minecraft.commands.CommandSourceStack source, java.util.Collection<ServerPlayer> targets) {
        if (MatchManager.active()) throw new IllegalStateException("경기 준비·진행 중에는 쿨타임을 초기화할 수 없습니다.");
        targets.forEach(SecondaryRuntime::resetCooldowns);
        int count = targets.size();
        source.sendSuccess(() -> Component.literal(count + "명의 보조무기·장비 쿨타임을 초기화했습니다."), true);
    }
    /** Starts with the current team entries regardless of ready flags; unassigned players watch as spectators. */
    private static void forceStart() {
        if (!enabled || !phase.equals("LOBBY")) throw new IllegalStateException("열린 로비에서만 강제 시작할 수 있습니다.");
        if (selected.isEmpty()) throw new IllegalStateException("먼저 맵을 선택하세요.");
        if (PLAYERS.values().stream().noneMatch(e -> e.team.equals("ALPHA") || e.team.equals("BRAVO"))) throw new IllegalStateException("ALPHA 또는 BRAVO 참가자가 없습니다.");
        PLAYERS.values().stream().filter(e -> e.team.equals("UNASSIGNED")).forEach(e -> e.team = "SPECTATOR");
        prepare(); broadcast(false);
    }
    private static boolean allReady() {
        return !selected.isEmpty() && LobbyRules.allReady(PLAYERS.values().stream().map(e -> new LobbyRules.Readiness(e.team, e.ready)).toList());
    }
    private static ServerLevel level(ArenaMap map) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(map.dimension())));
        if (level == null) throw new IllegalArgumentException("맵 차원이 존재하지 않습니다: " + map.dimension());
        for (var spawn : map.freeForAll()!=null?map.freeForAll().spawns():List.of(map.alphaSpawn(), map.bravoSpawn(), map.spectatorSpawn()))
            if (spawn.y() < level.getMinBuildHeight() || spawn.y() >= level.getMaxBuildHeight()) throw new IllegalArgumentException("스폰 Y가 차원 높이 범위를 벗어납니다.");
        return level;
    }
    private static void prepare() {
        activeMap = files.maps().stream().filter(m -> m.id().equals(selected)).findFirst().orElseThrow();
        DeathmatchRules deathmatch=selectedMode.equals("DEATHMATCH")?options().game().deathmatch(options().deathmatch()):null;
        activeMode=selectedMode;
        activeMap=options().game().apply(modeArena(activeMap,selectedMode,options().game()),selectedMode);
        level(activeMap);
        if(activeMap.propHunt()!=null)PropHuntMatch.validate(server,activeMap,catalog());
        if(activeMap.escort()!=null)EscortMatch.validate(server,activeMap);
        if(LobbyRules.bots(selectedMode))BotManager.validate(catalog());
        handicap();
        var choices=allocateLoadouts();
        choices.forEach((id,choice)->{var e=PLAYERS.get(id);e.primary=choice[0];e.secondary=choice[1];});
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
                if(MatchManager.propHunt()!=null){MatchManager.propHunt().equip(p);equipment(p,false);continue;}
                String[] choice=choices.get(p.getUUID());
                var primary=catalog().primary().stream().filter(w->w.id().equals(choice[0])).findFirst().orElse(catalog().primary().get(0));
                var secondary=catalog().secondary().stream().filter(w->w.id().equals(choice[1])).findFirst().orElse(catalog().secondary().get(0));
                e.primary = primary.id(); e.secondary = secondary.id();
                grant(p, primary, 0, true); grant(p, secondary, 40, true);
                if (loadouts.slot9() != null) grant(p, loadouts.slot9(), 8, true);
                equipment(p, true);
            }
            else giveSlot9(p);
        }
        if(MatchManager.bomb()!=null)MatchManager.bomb().announceRound();
        WeaponRotation.begin(server,activeMode,options().game(),activeMap.timeLimitSeconds());
    }
    private static void teleportSpawn(ServerPlayer p, Entry e) {
        if(MatchManager.freeForAll()!=null&&!e.team.equals("SPECTATOR")){MatchManager.freeForAll().spawn(p);return;}
        var spawn = e.team.equals("ALPHA") ? activeMap.alphaSpawn() : e.team.equals("BRAVO") ? activeMap.bravoSpawn() : activeMap.spectatorSpawn();
        if(MatchManager.bomb()!=null&&(e.team.equals("ALPHA")||e.team.equals("BRAVO")))spawn=MatchManager.bomb().spawn(e.team);
        if(MatchManager.escort()!=null&&(e.team.equals("ALPHA")||e.team.equals("BRAVO")))spawn=MatchManager.escort().spawn(e.team);
        if(phase.equals("RUNNING")&&e.team.equals("BRAVO")&&activeMap.normandy()!=null) spawn=MatchManager.attackSpawn();
        p.stopRiding(); p.teleportTo(level(activeMap), spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()); p.fallDistance = 0;
    }
    public static int preparationSeconds(ArenaMap arena) {
        if(arena.propHunt()!=null)return 0;if(arena.freeForAll()!=null)return 0;if(arena.bomb()!=null)return arena.bomb().preparationSeconds();
        Integer custom=options().game().preparation(activeMode);
        return custom!=null?custom:arena.normandy()==null?30:45;
    }
    /** The mode of the match being prepared or played (the lobby's selection may differ once it closes). */
    private static String activeMode="CONTROL";
    /**
     * Whether the current match plays 랜덤 무기 (weapons change every few minutes). True from preparation on, before
     * WeaponRotation starts, so loadouts handed out at match start already know it.
     */
    static boolean randomWeaponMatch(){return WeaponRotation.active()||activeMap!=null&&options().game().randomWeapons(activeMode);}
    /** The selected map as the chosen mode plays it, before lobby overrides. */
    private static ArenaMap modeArena(ArenaMap arena,String mode,GameOptions game){
        if(mode.equals("PROP_HUNT"))arena=PropHuntRules.arena(arena);
        if(mode.equals("DEATHMATCH"))arena=game.deathmatch(options().deathmatch()).arena(arena);
        if(mode.equals("ESCORT"))arena=arena.escortStart();
        if(mode.equals("BOMB")&&arena.freeForAll()!=null)arena=arena.withoutFreeForAll();
        if(mode.equals("FREE_FOR_ALL")&&arena.bomb()!=null)arena=arena.withoutBomb();
        return arena;
    }
    /** Head-count handicap of the current match: the bigger team (no secondaries, less health) and the smaller one (more health); "" when even or off. */
    private static String handicapLarge="",handicapSmall="";
    private static void handicap(){
        handicapLarge=handicapSmall="";
        var common=options().game().common();
        boolean health=common.handicapHealth()!=null&&common.handicapHealth()>1;
        boolean largeHealth=common.handicapLargeHealth()!=null&&common.handicapLargeHealth()<100;
        if(!GameOptions.teamMode(activeMode)||!common.handicapSecondary()&&!health&&!largeHealth)return;
        long alpha=PLAYERS.values().stream().filter(e->e.team.equals("ALPHA")).count(),bravo=PLAYERS.values().stream().filter(e->e.team.equals("BRAVO")).count();
        if(LobbyRules.bots(activeMode))for(var bot:BotManager.lobbyIds()){String t=BotManager.team(bot);if(t.equals("ALPHA"))alpha++;else if(t.equals("BRAVO"))bravo++;}
        if(alpha==bravo)return;
        handicapLarge=alpha>bravo?"ALPHA":"BRAVO";handicapSmall=alpha>bravo?"BRAVO":"ALPHA";
        var parts=new ArrayList<String>();
        if(common.handicapSecondary())parts.add(handicapLarge+" 보조무기 금지");
        if(largeHealth)parts.add(handicapLarge+" 체력 "+common.handicapLargeHealth()+"%");
        if(health)parts.add(handicapSmall+" 체력 x"+String.format(java.util.Locale.ROOT,"%.2f",common.handicapHealth()));
        for(var p:server.getPlayerList().getPlayers())if(PLAYERS.containsKey(p.getUUID()))
            p.sendSystemMessage(Component.literal("인원 핸디캡 (ALPHA "+alpha+" : BRAVO "+bravo+") · "+String.join(" · ",parts)).withStyle(net.minecraft.ChatFormatting.YELLOW));
    }
    /** Whether this team plays without secondaries in the current match. */
    static boolean secondaryBanned(String team){return activeMap!=null&&!handicapLarge.isEmpty()&&team.equals(handicapLarge)&&options().game().common().handicapSecondary();}
    /** Max-health multiplier for this team in the current match (1 outside a match or without a handicap). */
    static double healthScale(String team){
        if(activeMap==null||handicapSmall.isEmpty())return 1;
        var common=options().game().common();
        if(team.equals(handicapSmall))return common.handicapHealth()!=null?common.handicapHealth():1;
        if(team.equals(handicapLarge))return common.handicapLargeHealth()!=null?common.handicapLargeHealth()/100.0:1;
        return 1;
    }
    static double healthScale(UUID player){var e=PLAYERS.get(player);return e==null?1:healthScale(e.team);}
    /** Free-for-all respawn wait unless the lobby sets one. */
    static final int FFA_RESPAWN_TICKS=100;
    static int respawnTicks(){
        var custom=options().game().common().respawnSeconds();
        if(custom!=null)return custom*20;
        return MatchManager.freeForAll()!=null?FFA_RESPAWN_TICKS:options().respawnSeconds()*20;
    }
    static void bombTeleport(ServerPlayer p){var e=PLAYERS.get(p.getUUID());if(e!=null)teleportSpawn(p,e);}
    static void prepareBombRound(){
        phase="PREPARING";deadline=server.getTickCount()+preparationSeconds(activeMap)*20;
        RESPAWNING.clear();GadgetManager.clearAll(server);CombatGadgets.reset(server);GimmickEpoch.reset(server);
        for(var p:server.getPlayerList().getPlayers()){
            var e=PLAYERS.get(p.getUUID());if(e==null)continue;
            p.stopUsingItem();p.setCamera(p);teleportSpawn(p,e);p.removeAllEffects();Bleeding.clear(p);p.clearFire();p.setAbsorptionAmount(0);p.setAirSupply(p.getMaxAirSupply());
            p.getFoodData().setFoodLevel(SprintStamina.FOOD);p.getFoodData().setSaturation(0);SecondaryRuntime.clear(p);
            p.setGameMode(e.team.equals("ALPHA")||e.team.equals("BRAVO")?GameType.byName(activeMap.gameMode()):GameType.SPECTATOR);
            resetMatchEquipment(p,e);p.setHealth(p.getMaxHealth());equipment(p,false);
        }
        hooks(activeMap.onPrepare());broadcast(false);
    }
    static void startBombRound(){
        phase="RUNNING";deadline=server.getTickCount()+activeMap.timeLimitSeconds()*20;MatchManager.start();
        GadgetManager.clearAll(server);CombatGadgets.reset(server);
        for(var p:server.getPlayerList().getPlayers()){var e=PLAYERS.get(p.getUUID());if(e!=null&&!MatchManager.bomb().dead(p.getUUID()))resetMatchEquipment(p,e);}
        hooks(activeMap.onStart());broadcast(false);
    }
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
        if(phase.equals("LOBBY"))grantFree(p,loadouts.slot9(),8);else grant(p,loadouts.slot9(),8,false);
    }
    private static void resetMatchEquipment(ServerPlayer p,Entry e) {
        if(MatchManager.realPropHunt()!=null){MatchManager.realPropHunt().equip(p);return;}
        clearInventory(p,e);
        if(e.team.equals("ALPHA")||e.team.equals("BRAVO")) {
            catalog().primary().stream().filter(w->w.id().equals(e.primary)).findFirst().ifPresent(w->grant(p,w,0,false));
            catalog().secondary().stream().filter(w->w.id().equals(e.secondary)).findFirst().ifPresent(w->grant(p,w,40,false));
        }
        giveSlot9(p);
    }
    public static boolean inSpawn(ServerPlayer p) {
        if(MatchManager.freeForAll()!=null)return false;
        Entry e = PLAYERS.get(p.getUUID());
        if (!enabled || activeMap == null || e == null || e.team.equals("SPECTATOR") || !p.isAlive()
                || phase.equals("LOBBY") || !p.level().dimension().location().toString().equals(activeMap.dimension())) return false;
        var area = e.team.equals("ALPHA") ? activeMap.alphaArea() : activeMap.bravoArea();
        if(MatchManager.bomb()!=null)area=MatchManager.bomb().area(e.team);
        if(phase.equals("RUNNING")&&MatchManager.escort()!=null){
            area=MatchManager.escort().area(e.team);
            if(area==null){var s=MatchManager.escort().spawn(e.team);return p.distanceToSqr(s.x(),s.y(),s.z())<=16;}
        }
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
        if(activeMap!=null&&(phase.equals("PREPARING")||phase.equals("RUNNING"))){
            for(var p:server.getPlayerList().getPlayers())if(!p.isSpectator()&&p.isAlive()&&p.level().dimension().location().toString().equals(activeMap.dimension())){
                if(DeathZoneBlock.touching(p))MatchManager.deathZone(p);
            }
        }
        if (phase.equals("PREPARING")&&MatchManager.bomb()==null) {
            for (var p : server.getPlayerList().getPlayers()) { var e = PLAYERS.get(p.getUUID()); if (e != null && !e.team.equals("SPECTATOR") && p.isAlive()&&!p.isSpectator()
                    && activeMap.freeForAll()==null && !(activeMap.normandy()!=null&&e.team.equals("ALPHA"))
                    && !(activeMap.escort()!=null&&e.team.equals("BRAVO")) && !inSpawn(p)) teleportSpawn(p, e); } // escort defenders set up early
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
    /**
     * 랜덤 무기: the rotation's weapons replace the current ones in place (anywhere in the inventory), or wait for the
     * respawn of a player who is down; they are the player's loadout until the next change.
     */
    static void rotationLoadout(ServerPlayer p,Loadouts.Weapon primary,Loadouts.Weapon secondary){
        var e=PLAYERS.get(p.getUUID());if(e==null||activeMap==null)return;
        if(!secondary.id().equals(e.secondary))GadgetManager.clearDeployables(p);
        e.primary=primary.id();e.secondary=secondary.id();
        if(!RESPAWNING.containsKey(p.getUUID())&&p.isAlive()&&!p.isSpectator()){
            if(p.isUsingItem())p.stopUsingItem();
            grant(p,primary,0,false);grant(p,secondary,40,false);giveSlot9(p);
        }
        equipment(p,false);
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
        if(slot==40&&e!=null&&secondaryBanned(e.team)){p.inventoryMenu.broadcastChanges();return;} // head-count handicap
        if(weapon.commandOnly()) {executeSecondaryCommand(p,weapon,slot,TAG);return;}
        ItemStack displaced = p.getInventory().getItem(slot);
        ItemStack stack = weapon.stack(); LoadoutSlots.mark(stack,TAG,slot);
        if(slot==40) SecondaryRuntime.configure(p,stack,weapon);
        if(slot==0) LoadoutAttributes.apply(p,weapon);
        p.getInventory().setItem(slot, stack);
        if (!remember) LoadoutSlots.preserve(p,displaced);
        CompoundBow.syncAmmo(p);
        p.inventoryMenu.broadcastChanges();
    }
    /**
     * Individual mode has no teammates; opposing teams may use the same weapon. 근접전 always allows duplicates (only
     * four melee primaries), whatever the common options say.
     */
    private static boolean duplicates(boolean primary){if(options().game().meleeWeapons(selectedMode))return true;return primary?options().game().common().duplicatePrimary():options().game().common().duplicateSecondary();}
    private static boolean weaponTaken(UUID player,boolean primary,String id){
        var self=PLAYERS.get(player);
        if(!enabled||self==null||!Set.of("ALPHA","BRAVO").contains(self.team)||selectedMode.equals("FREE_FOR_ALL")||duplicates(primary))return false;
        return teamWeaponTaken(player,self.team,primary,id);
    }
    /** Weapon id to the teammate (or bot) holding it, for the loadout screen; empty when duplicates are allowed. */
    private static Map<String,String> takenWeapons(UUID player,boolean primary){
        var result=new LinkedHashMap<String,String>();var self=PLAYERS.get(player);
        if(loadouts==null||!enabled||self==null||!Set.of("ALPHA","BRAVO").contains(self.team)||selectedMode.equals("FREE_FOR_ALL")||duplicates(primary))return result;
        PLAYERS.forEach((id,e)->{
            if(id.equals(player)||!e.team.equals(self.team))return;
            var free=FREE_SELECTION.get(id);
            String choice=phase.equals("LOBBY")?(free==null?"":free[primary?0:1]):primary?e.primary:e.secondary;
            if(choice==null||choice.isEmpty())return;
            var other=server.getPlayerList().getPlayer(id);
            result.putIfAbsent(choice,other==null?"팀원":ProgressionService.name(other));
        });
        if(!phase.equals("LOBBY"))for(var weapon:primary?catalog().primary():catalog().secondary()){
            if(result.containsKey(weapon.id()))continue;
            var bot=BotManager.weaponHolder(player,self.team,primary,weapon.id());
            if(bot!=null)result.put(weapon.id(),BotManager.name(bot));
        }
        return result;
    }
    /** Re-sends the loadout state to this player's teammates so their screens mark the weapons now taken. */
    private static void refreshTeammates(ServerPlayer p){
        var self=PLAYERS.get(p.getUUID());if(self==null)return;
        PLAYERS.forEach((id,e)->{
            if(id.equals(p.getUUID())||!e.team.equals(self.team))return;
            var other=server.getPlayerList().getPlayer(id);if(other!=null)equipment(other,false);
        });
    }
    /** A player's chosen primary and secondary weapon ids ("" when none), or null outside the lobby roster. */
    static String[] weapons(UUID id){
        var e=PLAYERS.get(id);if(e==null)return null;
        if(phase.equals("LOBBY")){var free=FREE_SELECTION.get(id);return free==null?new String[]{"",""}:free.clone();}
        return new String[]{e.primary==null?"":e.primary,e.secondary==null?"":e.secondary};
    }
    static boolean teamWeaponTaken(UUID player,String team,boolean primary,String id){
        if(selectedMode.equals("FREE_FOR_ALL")||duplicates(primary))return false;
        if(!phase.equals("LOBBY")&&BotManager.weaponTaken(player,team,primary,id))return true;
        return PLAYERS.entrySet().stream().anyMatch(other->{
            if(other.getKey().equals(player)||!other.getValue().team.equals(team))return false;
            var e=other.getValue();var free=FREE_SELECTION.get(other.getKey());
            String choice=phase.equals("LOBBY")?(free==null?"":free[primary?0:1]):primary?e.primary:e.secondary;
            return id.equals(choice);
        });
    }
    private static Map<UUID,String[]> allocateLoadouts(){
        var result=new LinkedHashMap<UUID,String[]>();var used=new HashMap<String,Set<String>>();
        if(selectedMode.equals("PROP_HUNT"))return result;
        for(var row:PLAYERS.entrySet()){
            var e=row.getValue();var p=server.getPlayerList().getPlayer(row.getKey());
            if(p==null||!Set.of("ALPHA","BRAVO").contains(e.team))continue;
            var choice=startChoice(p,FREE_SELECTION.getOrDefault(row.getKey(),new String[]{e.primary,e.secondary}));
            for(int i=0;i<2;i++){
                var weapons=i==0?catalog().primary():catalog().secondary();var taken=used.computeIfAbsent(e.team+":"+i,k->new HashSet<>());
                boolean unique=!selectedMode.equals("FREE_FOR_ALL")&&!duplicates(i==0);String wanted=choice[i];
                choice[i]=LobbyRules.preferredOrRandom(weapons.stream().map(Loadouts.Weapon::id).filter(id->!unique||!taken.contains(id)).toList(),wanted,java.util.concurrent.ThreadLocalRandom.current());
                taken.add(choice[i]);
            }
            result.put(row.getKey(),choice);
        }
        // 숨바꼭질 bots carry the issued shotgun or nothing, so they take no primary from the pool.
        if(LobbyRules.bots(selectedMode)&&!selectedMode.equals("FREE_FOR_ALL")&&!selectedMode.equals("PROP_HUNT")&&!duplicates(true))for(var bot:BotManager.lobbyIds()){
            var taken=used.computeIfAbsent(BotManager.team(bot)+":0",k->new HashSet<>());
            String weapon=catalog().primary().stream().filter(w->!taken.contains(w.id())).filter(w->{var stack=w.stack();return stack.is(net.minecraft.world.item.Items.BOW)||BotGun.supports(stack);})
                .map(Loadouts.Weapon::id).findFirst().orElseThrow(()->new IllegalStateException("팀원과 봇에게 중복 없이 지급할 주무기가 부족합니다. 중복을 허용하거나 인원을 줄이세요."));
            taken.add(weapon);
        }
        result.forEach((id,actual)->{
            var p=server.getPlayerList().getPlayer(id);var e=PLAYERS.get(id);
            var wanted=startChoice(p,FREE_SELECTION.getOrDefault(id,new String[]{e.primary,e.secondary}));
            notifyReplacement(p,true,wanted[0],actual[0]);notifyReplacement(p,false,wanted[1],actual[1]);
        });
        return result;
    }
    private static Loadouts.Weapon availableWeapon(ServerPlayer p,String team,boolean primary,String wanted){
        var weapons=primary?catalog().primary():catalog().secondary();
        String selected=LobbyRules.preferredOrRandom(weapons.stream().map(Loadouts.Weapon::id).filter(id->!teamWeaponTaken(p.getUUID(),team,primary,id)).toList(),wanted,java.util.concurrent.ThreadLocalRandom.current());
        return weapons.stream().filter(w->w.id().equals(selected)).findFirst().orElseThrow();
    }
    private static void choosePresetWeapon(ServerPlayer p,boolean primary,String wanted)throws java.io.IOException{
        loadouts=Loadouts.load();var weapons=primary?catalog().primary():catalog().secondary();
        String selected=LobbyRules.preferredOrRandom(weapons.stream().map(Loadouts.Weapon::id).filter(id->!weaponTaken(p.getUUID(),primary,id)).toList(),wanted,java.util.concurrent.ThreadLocalRandom.current());
        chooseWeapon(p,primary?"primary":"secondary",selected);
        notifyReplacement(p,primary,wanted,selected);
    }
    private static void notifyReplacement(ServerPlayer p,boolean primary,String wanted,String selected){
        if(wanted==null||wanted.isEmpty()||wanted.equals(selected))return;
        var weapons=primary?catalog().primary():catalog().secondary();
        String name=weapons.stream().filter(w->w.id().equals(selected)).map(Loadouts.Weapon::name).findFirst().orElse(selected);
        p.sendSystemMessage(Component.literal((primary?"주무기":"보조무기")+" 프리셋을 사용할 수 없어 사용 가능한 무기 중 '"+name+"'을(를) 랜덤 선택했습니다.").withStyle(ChatFormatting.YELLOW));
    }
    private static void chooseWeapon(ServerPlayer p, String group, String id) throws java.io.IOException {
        if(SurvivalGadgets.jetting(p))throw new IllegalStateException("제트팩 이동 중에는 장비를 변경할 수 없습니다.");
        if (!canChoose(p)) throw new IllegalStateException("경기 중에는 리스폰 대기, 자기 팀 스폰 구역 또는 무기고 근처에서 무기를 선택할 수 있습니다.");
        loadouts = Loadouts.load();
        var weapons = group.equals("primary") ? catalog().primary() : catalog().secondary();
        var weapon = weapons.stream().filter(w -> w.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("등록되지 않은 무기입니다."));
        int slot = group.equals("primary") ? 0 : 40;
        if(weaponTaken(p.getUUID(),slot==0,id))throw new IllegalStateException("같은 팀에서 이미 선택한 무기입니다. 게임 옵션에서 중복을 허용할 수 있습니다.");
        if (phase.equals("LOBBY")) {
            var previous=FREE_SELECTION.get(p.getUUID());
            if(slot==40&&previous!=null&&!id.equals(previous[1]))GadgetManager.clearDeployables(p);
            grantFree(p,weapon,slot);
            giveSlot9(p);
            String[] choice=FREE_SELECTION.computeIfAbsent(p.getUUID(),ignored->new String[]{"",""});
            choice[slot==0?0:1]=id;
            equipment(p,true); refreshTeammates(p); return;
        }
        boolean found = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) { var item = p.getInventory().getItem(i); if (issued(item) && item.getTag().getInt(TAG) == slot) found = true; }
        var entry=PLAYERS.get(p.getUUID());
        boolean previousCommand=slot==40&&entry!=null&&catalog().secondary().stream().anyMatch(w->w.id().equals(entry.secondary)&&w.commandOnly());
        if (!found && !previousCommand && !RESPAWNING.containsKey(p.getUUID())) throw new IllegalStateException("기존 지급 무기를 인벤토리로 가져온 뒤 교체하세요.");
        // Respawn selection may have no old stack left to inspect.
        if(slot==40&&entry!=null&&!id.equals(entry.secondary))GadgetManager.clearDeployables(p);
        grant(p, weapon, slot, false);
        giveSlot9(p);
        Entry e = PLAYERS.get(p.getUUID()); if (slot == 0) e.primary = id; else e.secondary = id;
        equipment(p, true); refreshTeammates(p);
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
        if (show && !allowed) p.sendSystemMessage(Component.literal(WeaponRotation.active()?"랜덤 무기 모드에서는 무기를 직접 바꿀 수 없습니다. 교체 시간에 숫자 키로 고르세요."
                :"무기 선택 불가: 자기 팀 스폰 구역 또는 무기고 근처로 이동하세요. 서버 무기 설정도 확인하세요."));
        var choice=FREE_SELECTION.getOrDefault(p.getUUID(),new String[]{"",""});
        PlayerRecord record=null;
        try { record=ProgressionService.record(p); } catch (IllegalStateException unavailable) { }
        var snapshot = new EquipmentSnapshot(allowed, show && allowed, e == null ? "UNASSIGNED" : e.team,
                phase.equals("LOBBY") ? choice[0] : e == null ? "" : e.primary,
                phase.equals("LOBBY") ? choice[1] : e == null ? "" : e.secondary,
                RESPAWNING.containsKey(p.getUUID()) ? Math.max(0,(RESPAWNING.get(p.getUUID()).deadline()-server.getTickCount()+19)/20) : phase.equals("PREPARING") ? Math.max(0, (deadline - server.getTickCount() + 19) / 20) : 0, show && allowed ? catalog().view() : null,
                phase.equals("RUNNING") && allowed && inSpawn(p), record == null ? List.of() : record.presets(), record == null ? 0 : record.defaultPreset(),
                takenWeapons(p.getUUID(), true), takenWeapons(p.getUUID(), false));
        var status=new EquipmentSnapshot(snapshot.allowed(),false,snapshot.team(),snapshot.primary(),snapshot.secondary(),snapshot.preparationSeconds(),null,snapshot.spawnHint(),snapshot.presets(),snapshot.defaultPreset(),snapshot.takenPrimary(),snapshot.takenSecondary());
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
        if(MatchManager.realPropHunt()!=null||PropHuntMatch.testing(p.getUUID()))return false;
        // 랜덤 무기: the rotation hands out every loadout.
        if(WeaponRotation.active())return false;
        if(MatchManager.bomb()!=null&&(MatchManager.bomb().dead(p.getUUID())||MatchManager.bomb().game.phase()==BombGame.Phase.ROUND_END))return false;
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
        var stack=weapon.stack();LoadoutSlots.mark(stack,tag,slot);
        if(slot==40) SecondaryRuntime.configure(p,stack,weapon);
        if(slot==0) LoadoutAttributes.apply(p,weapon);
        p.getInventory().setItem(slot,stack);
        LoadoutSlots.preserve(p,displaced);
        CompoundBow.syncAmmo(p);
        p.inventoryMenu.broadcastChanges();
    }
    static void executeSecondaryCommand(ServerPlayer p,Loadouts.Weapon weapon,int slot,String tag) {
        if(slot!=40)throw new IllegalArgumentException("command는 보조무기에만 사용할 수 있습니다.");
        var displaced=p.getInventory().getItem(slot);
        p.getInventory().setItem(slot,ItemStack.EMPTY);
        LoadoutSlots.preserve(p,displaced);
        try {p.server.getCommands().getDispatcher().execute(weapon.command(),p.createCommandSourceStack().withPermission(2).withSuppressedOutput());}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException error) {throw new IllegalArgumentException("보조무기 command 실행 실패: "+error.getMessage(),error);}
        var stack=p.getOffhandItem();
        if(!stack.isEmpty()) {LoadoutSlots.mark(stack,tag,slot);SecondaryRuntime.configure(p,stack,weapon);}
        p.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if(activeMap == null || phase.equals("LOBBY")) {
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
            var choice=FREE_SELECTION.get(p.getUUID());
            if(choice!=null&&loadouts!=null)catalog().primary().stream().filter(w->w.id().equals(choice[0])).findFirst().ifPresent(w->LoadoutAttributes.apply(p,w));
            p.setHealth(p.getMaxHealth());p.setGameMode(GameType.ADVENTURE);
            return;
        }
        if(MatchManager.bomb()!=null){MatchManager.bomb().lateJoin(p);return;}
        var e = PLAYERS.get(p.getUUID()); if (e == null) return;
        server.execute(() -> { teleportSpawn(p, e); if (!e.team.equals("SPECTATOR")) {
            catalog().primary().stream().filter(w -> w.id().equals(e.primary)).findFirst().ifPresent(w -> grant(p, w, 0, false));
            catalog().secondary().stream().filter(w -> w.id().equals(e.secondary)).findFirst().ifPresent(w -> grant(p, w, 40, false));
            if (loadouts.slot9() != null) grant(p, loadouts.slot9(), 8, false);
            p.setHealth(p.getMaxHealth());
        } });
    }
    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        var ffa=MatchManager.freeForAll();
        if(ffa!=null&&((event.getEntity() instanceof ServerPlayer p&&ffa.participant(p.getUUID())&&ffa.opening())
            ||(event.getSource().getEntity() instanceof ServerPlayer attacker&&attacker!=event.getEntity()&&FreeForAllMatch.blocked(attacker)&&!Turrets.shot(event.getSource())))){
            event.setCanceled(true);return;
        }
        if(MatchManager.bomb()!=null&&!MatchManager.bomb().activePlay()&&event.getEntity() instanceof ServerPlayer p&&PLAYERS.containsKey(p.getUUID())){event.setCanceled(true);return;}
        if(event.getEntity() instanceof ServerPlayer waiting && RESPAWNING.containsKey(waiting.getUUID())) { event.setCanceled(true); return; }
        if (enabled && phase.equals("PREPARING") && ((event.getEntity() instanceof ServerPlayer p && PLAYERS.containsKey(p.getUUID()))
                || (event.getSource().getEntity() instanceof ServerPlayer attacker && PLAYERS.containsKey(attacker.getUUID())))) event.setCanceled(true);
    }
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PLAYERS.containsKey(p.getUUID())) event.getDrops().removeIf(item -> issued(item.getItem()));
    }
    /** Survival and adventure players keep every item (DropLock blocks Q on the client); in a match issued gear always stays. */
    @SubscribeEvent public static void toss(ItemTossEvent event) {
        boolean survival = event.getPlayer() instanceof ServerPlayer p && p.gameMode.getGameModeForPlayer().isSurvival();
        if (survival || PLAYERS.containsKey(event.getPlayer().getUUID()) && issued(event.getEntity().getItem()) && event.getEntity().getItem().getTag().getInt(TAG) != 8) {
            event.setCanceled(true); event.getPlayer().getInventory().add(event.getEntity().getItem());
        }
    }
    private static void send(ServerPlayer p, boolean show) {
        var mapViews = files == null ? List.<LobbySnapshot.MapView>of() : files.maps().stream().map(m -> new LobbySnapshot.MapView(m.id(),m.name(),m.description(),
                m.id().equals(selected)?selectedSeconds(m):selectedMode.equals("DEATHMATCH")&&DeathmatchRules.supported(m)?options().deathmatch().timeLimitSeconds():m.timeLimitSeconds(),
                m.propHunt()!=null?"숨바꼭질":m.freeForAll()!=null&&(m.bomb()==null||m.id().equals(selected)&&selectedMode.equals("FREE_FOR_ALL"))?"개인전":m.escort()!=null?"호위":m.normandy()!=null?"이동점령":(DeathmatchRules.exclusive(m)||selectedMode.equals("DEATHMATCH")&&DeathmatchRules.supported(m))?"팀 데스매치":m.bomb()!=null?"폭탄전":"쟁탈전",DeathmatchRules.supported(m),m.previewArea()!=null?"@generated":m.previewImage(),m.bomb()!=null,DeathmatchRules.exclusive(m),m.weather(),m.timeOfDay(),m.releaseDate(),modes(m))).toList();
        List<LobbySnapshot.Member> members = new ArrayList<>();
        for (var entry : PLAYERS.entrySet()) { var player = server.getPlayerList().getPlayer(entry.getKey()); if (player != null) members.add(new LobbySnapshot.Member(entry.getKey().toString(),ProgressionService.name(player),entry.getValue().team,entry.getValue().ready)); }
        if(LobbyRules.bots(selectedMode))for(var bot:BotManager.lobbyIds())members.add(new LobbySnapshot.Member(bot.toString(),BotManager.name(bot),BotManager.team(bot),true,BotManager.skin(bot)));
        var snapshot = new LobbySnapshot(enabled, show, p.hasPermissions(2), p.getUUID().toString(), selected, phase,
                phase.equals("LOBBY") ? 0 : Math.max(0,(deadline - server.getTickCount() + 19)/20), mapViews, members, options(),selectedMode,BotManager.settings().enabled(),BotManager.added(),BotManager.lobbyCount(),BotManager.MAX,
                gameDefaults(),normandyPoints());
        GungangNetwork.send(p, new GungangNetwork.Lobby(ServerFiles.GSON.toJson(snapshot)));
    }
    /** The selected map/mode's own values, shown as placeholders in the game options screen. */
    private static GameOptions gameDefaults(){
        var arena=selectedArena();ArenaMap mode=null;
        try{if(arena!=null&&modes(arena).contains(selectedMode))mode=modeArena(arena,selectedMode,GameOptions.defaults());}
        catch(RuntimeException failure){LogUtils.getLogger().debug("Game option defaults unavailable: {}",failure.getMessage());}
        return GameOptions.effectiveDefaults(mode,selectedMode,options());
    }
    private static List<String> normandyPoints(){var arena=selectedArena();return arena==null||arena.normandy()==null?List.of():arena.normandy().points().stream().map(NormandyRules.Point::name).toList();}
    /** Time limit the selected map plays with in the selected mode, lobby overrides included. */
    private static int selectedSeconds(ArenaMap m){
        try{
            if(selectedMode.equals("PROP_HUNT")){var t=options().game().propHunt().timeSeconds();return t!=null?t:PropHuntGame.huntTicks(m)/20;}
            return options().game().apply(modeArena(m,selectedMode,options().game()),selectedMode).timeLimitSeconds();
        }catch(RuntimeException failure){return m.timeLimitSeconds();}
    }
    private static void broadcast(boolean show) { if (server != null) for (var p : server.getPlayerList().getPlayers()) send(p, show); }
    /** Refresh every open lobby immediately; editing bot participation never triggers a match start. */
    static void botChanged(){if(phase.equals("LOBBY"))PLAYERS.values().forEach(e->e.ready=false);broadcast(false);}
    /** The weapons on offer in the selected mode: the whole catalog, or under 근접전 only the melee primaries. */
    private static Loadouts catalog(){return catalog(loadouts);}
    static Loadouts catalog(Loadouts all){return all!=null&&options().game().meleeWeapons(selectedMode)?all.melee():all;}
    public static TeamOptions options() { return files == null ? TeamOptions.defaults() : files.options(); }
    public static boolean respawning(UUID id) { return RESPAWNING.containsKey(id); }
    static boolean beginRespawn(ServerPlayer victim, net.minecraft.world.entity.LivingEntity killer) {
        return beginRespawn(victim,killer,false);
    }
    static boolean beginRespawn(ServerPlayer victim, net.minecraft.world.entity.LivingEntity killer,boolean suicide) {
        var entry=PLAYERS.get(victim.getUUID());
        if((!phase.equals("RUNNING")&&!(suicide&&phase.equals("PREPARING")))||entry==null||(!entry.team.equals("ALPHA")&&!entry.team.equals("BRAVO"))||RESPAWNING.containsKey(victim.getUUID())) return false;
        var message=suicide?MatchManager.deathZoneMessage(victim):MatchManager.deathMessage(victim,killer);
        int duration=respawnTicks();
        boolean self=suicide||killer==null||killer==victim;
        // Void deaths would otherwise leave the camera below the world.
        Vec3 spot=self?new Vec3(victim.getX(),Math.max(victim.getY(),victim.level().getMinBuildHeight()+2),victim.getZ()):null;
        RESPAWNING.put(victim.getUUID(),new Respawning(server.getTickCount()+duration,duration,killer==null?null:killer.getUUID(),victim.level().dimension(),spot));
        victim.setHealth(victim.getMaxHealth()); victim.removeAllEffects();Bleeding.clear(victim); victim.clearFire(); victim.fallDistance=0;
        victim.setGameMode(GameType.SPECTATOR);
        if(killer!=null&&killer!=victim&&killer.isAlive()&&!killer.isSpectator()&&killer.level()==victim.level()) victim.setCamera(killer);
        for(var player:server.getPlayerList().getPlayers()) player.sendSystemMessage(message);
        equipment(victim,false);
        return true;
    }
    private static void tickRespawns(int now) {
        for(var id:List.copyOf(RESPAWNING.keySet())) {
            var wait=RESPAWNING.get(id); var p=server.getPlayerList().getPlayer(id); var entry=PLAYERS.get(id);
            if(p==null||entry==null||activeMap==null||(!phase.equals("RUNNING")&&!phase.equals("PREPARING"))) { RESPAWNING.remove(id); continue; }
            if(now>=wait.deadline()) {
                RESPAWNING.remove(id); p.setCamera(p); teleportSpawn(p,entry);
                if(MatchManager.freeForAll()!=null)MatchManager.freeForAll().protect(p);
                p.setHealth(p.getMaxHealth()); p.removeAllEffects();Bleeding.clear(p); p.clearFire(); p.setAirSupply(p.getMaxAirSupply());
                p.getFoodData().setFoodLevel(SprintStamina.FOOD); p.getFoodData().setSaturation(0); p.setAbsorptionAmount(0);
                p.setGameMode(GameType.byName(activeMap.gameMode()));
                if(activeMap.normandy()!=null&&entry.team.equals("BRAVO"))
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,60,5,false,false));
                catalog().primary().stream().filter(w->w.id().equals(entry.primary)).findFirst().ifPresent(w->grant(p,w,0,false));
                catalog().secondary().stream().filter(w->w.id().equals(entry.secondary)).findFirst().ifPresent(w->grant(p,w,40,false));
                if(loadouts.slot9()!=null) grant(p,loadouts.slot9(),8,false);
                p.setHealth(p.getMaxHealth());
                equipment(p,false);
            } else {
                if(!p.isSpectator()) p.setGameMode(GameType.SPECTATOR);
                // The killer may be a bot: its body is followed like a player's.
                net.minecraft.world.entity.LivingEntity killer=wait.killer()==null?null:server.getPlayerList().getPlayer(wait.killer());
                if(killer==null&&wait.killer()!=null)killer=BotManager.body(wait.killer());
                net.minecraft.world.entity.Entity camera=killer!=null&&killer!=p&&killer.isAlive()&&!killer.isSpectator()&&killer.level()==p.level()?killer:p;
                if(p.getCamera()!=camera) p.setCamera(camera);
                if(camera!=p) continue;
                var spotLevel=wait.spot()==null?null:server.getLevel(wait.dimension());
                if(spotLevel==null) teleportSpawn(p,entry);
                // Hold the death spot but leave the view free to look around.
                else if(p.level()!=spotLevel||p.position().distanceToSqr(wait.spot())>.25) {
                    p.stopRiding(); p.teleportTo(spotLevel,wait.spot().x,wait.spot().y,wait.spot().z,p.getYRot(),p.getXRot()); p.fallDistance=0;
                }
            }
        }
    }
    public static List<MatchSnapshot.Respawn> respawns() {
        return RESPAWNING.entrySet().stream().map(e->new MatchSnapshot.Respawn(e.getKey().toString(),Math.max(0,e.getValue().deadline()-server.getTickCount()),e.getValue().duration())).toList();
    }
    private static void resetPlayer(ServerPlayer p) {
        LoadoutAttributes.clear(p);GadgetManager.clear(p);SecondaryRuntime.clear(p);
        RESPAWNING.remove(p.getUUID());FREE_SELECTION.remove(p.getUUID());LAST_EQUIPMENT.remove(p.getUUID());
        p.setCamera(p);p.stopRiding();p.removeAllEffects();Bleeding.clear(p);p.clearFire();p.fallDistance=0;
        clearPlayerInventory(p);
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        p.setHealth(100);p.setAbsorptionAmount(0);p.setGameMode(GameType.ADVENTURE);
        p.setRespawnPosition(Level.OVERWORLD,HOME,0,true,false);
        p.teleportTo(p.server.overworld(),HOME.getX(),HOME.getY(),HOME.getZ(),0,0);
    }
    private static void reset() {
        TeamPoints.clear(server);
        GimmickEpoch.reset(server);
        close();DebugPreview.endAll(server);MapEnvironment.resetDefaults(server);MapPreviewGenerator.clear();GadgetManager.clearAll(server);CombatGadgets.reset(server);
        server.overworld().setDefaultSpawnPos(HOME,0);
        for(var p:server.getPlayerList().getPlayers()) {resetPlayer(p);equipment(p,false);}
        broadcast(false);
    }
    private LobbyManager() { }
}
