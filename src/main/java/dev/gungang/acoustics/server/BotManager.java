package dev.gungang.acoustics.server;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.GungangAcoustics;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/**
 * Up to {@link #MAX} rule-based TDM bots, with no external inference/training service and bounded decision work.
 * This class holds what the bots share (settings, profiles, lobby reservations, events, commands); each bot in a
 * match is a {@link BotAgent}. Bots fight players and each other by team, never their own side.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class BotManager {
    /** The first bot's id; every bot id is {@link BotDatabase#id(int)}. */
    public static final UUID ID=BotDatabase.id(0);
    public static final int MAX=BotDatabase.MAX;
    public static final String NAME="Rookie";
    private static BotDatabase database;
    private static BotSettings settings=BotSettings.defaults();
    private static MinecraftServer server;
    private static ArenaMap arena;
    private static String fault="";
    /** Lobby reservations: slot → team chosen by an admin, or "" to auto-balance. */
    private static final TreeMap<Integer,String> lobby=new TreeMap<>();
    private static final List<BotAgent> agents=new ArrayList<>();
    private static final byte[][] skinPng=new byte[MAX][];
    private static long workNanos,workSamples,maxWorkNanos;
    private static int slowLogAt;
    private BotManager(){}

    static MinecraftServer server(){return server;}
    static ArenaMap arena(){return arena;}
    static BotSettings settings(){return settings;}
    static ServerLevel level(){return server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(arena.dimension())));}

    // ---- identity ----
    public static boolean identity(UUID id){return BotDatabase.slot(id)>=0;}
    public static boolean owns(Entity entity){return agent(entity)!=null;}
    static BotAgent agent(Entity entity){if(entity==null)return null;for(var a:agents)if(a.bot==entity)return a;return null;}
    static BotAgent agent(UUID id){for(var a:agents)if(a.id.equals(id))return a;return null;}
    /** The agent in a slot during a match (smoke checks and status). */
    static BotAgent agent(int slot){for(var a:agents)if(a.slot==slot)return a;return null;}
    public static String name(){return name(0);}
    static String name(int slot){return database==null?(slot==0?NAME:NAME+" "+(slot+1)):database.profile(slot).displayName();}
    static String name(UUID id){int slot=BotDatabase.slot(id);return slot<0?"":name(slot);}
    public static String skin(){return skin(0);}
    static String skin(int slot){return database==null?BotDatabase.DEFAULT_SKIN:database.profile(slot).skin();}
    public static String skin(UUID id){int slot=BotDatabase.slot(id);return slot<0?"":skin(slot);}
    /** Team of the first bot in the match (or its lobby team). */
    static String team(){return agents.isEmpty()?lobbyTeam():agents.get(0).team;}
    static String team(UUID id){var a=agent(id);if(a!=null)return a.team;int slot=BotDatabase.slot(id);return slot>=0&&lobby.containsKey(slot)?lobbyTeam(slot):"";}
    /** Team of a match participant: a player's lobby team or a bot's side. */
    static String side(LivingEntity entity){
        var a=agent(entity);if(a!=null)return a.team;
        return entity instanceof ServerPlayer p?LobbyManager.team(p.getUUID()):"";
    }
    /** Live bots of the running match. */
    static List<BotEntity> entities(){
        if(!MatchManager.botRunning())return List.of();
        var list=new ArrayList<BotEntity>();for(var a:agents)if(a.bot!=null&&a.bot.isAlive()&&!a.bot.isRemoved())list.add(a.bot);return list;
    }
    static boolean live(BotEntity bot){return entities().contains(bot);}
    static Set<UUID> activeIds(){var ids=new HashSet<UUID>();for(var a:agents)ids.add(a.id);return ids;}
    static boolean boosted(Entity attacker){var a=agent(attacker);return a!=null&&a.gadgets.boosted(attacker.level().getGameTime());}
    /** Pathfinding cost hook: ground this bot knows is mined, trapped or burning. */
    static boolean avoid(BotEntity bot,int x,int y,int z){var a=agent(bot);return a!=null&&a.perception.avoid(x+.5,y,z+.5);}
    static float routeCost(BotEntity bot,int x,int y,int z){var a=agent(bot);return a==null?0:
            (float)a.movement.routeMemory.risk(x+.5,y,z+.5,bot.getServer().getTickCount())*4;}
    static void damageDealt(Entity attacker,double amount){var a=agent(attacker);if(a!=null)a.damageDealt(amount);}

    // ---- lobby ----
    static boolean added(){return settings.enabled()&&!lobby.isEmpty();}
    static int lobbyCount(){return settings.enabled()?lobby.size():0;}
    static List<UUID> lobbyIds(){return settings.enabled()?lobby.keySet().stream().map(BotDatabase::id).toList():List.of();}
    static String lobbyTeam(){return lobby.isEmpty()?chooseTeam(0):lobbyTeam(lobby.firstKey());}
    /** An admin's choice, or the smaller team counting players and the bots before this one. */
    static String lobbyTeam(int slot){
        String chosen=lobby.getOrDefault(slot,"");if(!chosen.isEmpty())return chosen;
        return chooseTeam(slot);
    }
    private static String chooseTeam(int slot){
        long a=0,b=0;
        if(server!=null)for(var p:server.getPlayerList().getPlayers()){String t=LobbyManager.team(p.getUUID());if(t.equals("ALPHA"))a++;else if(t.equals("BRAVO"))b++;}
        for(int other:lobby.keySet()){
            if(other>=slot)break;
            String t=lobbyTeam(other);if(t.equals("ALPHA"))a++;else if(t.equals("BRAVO"))b++;
        }
        return settings.chooseTeam(a,b);
    }
    /** Reserves the next free bot slot for the coming match. */
    static void lobbyAdd(Loadouts loadouts){
        requireLobby();
        if(!settings.enabled())throw new IllegalStateException("봇 기능이 꺼져 있습니다. 관리자가 /gungang bot on으로 먼저 켜야 합니다.");
        selectWeapon(loadouts);selectSecondary(loadouts);
        for(int slot=0;slot<MAX;slot++)if(!lobby.containsKey(slot)){lobby.put(slot,"");return;}
        throw new IllegalStateException("AI 봇은 최대 "+MAX+"명까지 추가할 수 있습니다.");
    }
    /** Removes one bot from the lobby: the given one, or the last added. */
    static void lobbyRemove(UUID id){
        requireLobby();
        if(lobby.isEmpty())throw new IllegalStateException("로비에 추가된 봇이 없습니다.");
        int slot=id==null?lobby.lastKey():BotDatabase.slot(id);
        if(slot<0||lobby.remove(slot)==null)throw new IllegalArgumentException("로비에 없는 봇입니다.");
    }
    static void lobbyRemove(){lobbyRemove(null);}
    static void clearLobby(){lobby.clear();}
    static void lobbyTeam(UUID id,String value){
        requireLobby();int slot=BotDatabase.slot(id);
        if(!lobby.containsKey(slot))throw new IllegalStateException("로비에 추가된 봇이 아닙니다.");
        if(!Set.of("ALPHA","BRAVO").contains(value))throw new IllegalArgumentException("봇은 ALPHA / BRAVO에만 배치할 수 있습니다.");
        lobby.put(slot,value);
    }

    // ---- profiles and skins ----
    /** PNG bytes of a file: skin per slot, empty for resource-pack skins or when the file could not be read. */
    private static String loadSkin(int slot){
        String warning="";skinPng[slot]=new byte[0];
        if(skin(slot).startsWith(BotSkinFiles.PREFIX))try{skinPng[slot]=BotSkinFiles.read(skin(slot));}
        catch(Exception error){warning=error.getMessage();LogUtils.getLogger().warn("[GunGang] Bot skin {} unavailable: {}",skin(slot),warning);}
        if(server!=null)for(var p:server.getPlayerList().getPlayers())sendSkin(p,slot);
        return warning;
    }
    private static String loadSkins(){var warnings=new StringBuilder();for(int i=0;i<MAX;i++){String w=loadSkin(i);if(!w.isEmpty())warnings.append(" ").append(i+1).append("번: ").append(w);}return warnings.toString().strip();}
    private static void sendSkin(ServerPlayer p,int slot){if(skinPng[slot]!=null)GungangNetwork.send(p,new GungangNetwork.BotSkin(skin(slot),skinPng[slot]));}
    @SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer p)for(int i=0;i<MAX;i++)sendSkin(p,i);
    }
    static void finish(MatchSnapshot match){
        if(database!=null)try{database.finish(match,LobbyManager.options().experience());}
        catch(Exception e){LogUtils.getLogger().error("[GunGang] Could not save bot-db.json",e);}
        if(server!=null)ProgressionService.broadcastLevels(server);
    }
    /** Career XP of a bot, for its level emblem; 0 when the profile store is unavailable. */
    static long experience(UUID id){int slot=BotDatabase.slot(id);return database==null||slot<0?0:database.profile(slot).statistics().experience();}

    // ---- lifecycle ----
    private static Path file(){return ServerFiles.root().resolve("server/bot.json");}
    @SubscribeEvent public static void starting(ServerStartingEvent event){
        server=event.getServer();settings=BotSettings.defaults();fault="";lobby.clear();database=null;
        try{
            database=new BotDatabase(ServerFiles.root().resolve("bot-db.json"));
            Files.createDirectories(BotSkinFiles.directory());loadSkins();
            ConfigLayout.writeNew(file(),ServerFiles.GSON.toJson(settings));
            if(Files.size(file())>16384)throw new IllegalArgumentException("bot.json is too large");
            var parsed=ServerFiles.GSON.fromJson(Files.readString(file()),BotSettings.class);
            if(parsed==null)throw new IllegalArgumentException("Empty bot.json");settings=parsed;
        }catch(Exception error){fault="설정 읽기 실패: "+error.getMessage();LogUtils.getLogger().error("[GunGang] Bot disabled: invalid bot.json or bot-db.json",error);}
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){close();}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){close();server=null;settings=BotSettings.defaults();database=null;Arrays.fill(skinPng,null);}
    private static void save(BotSettings value)throws java.io.IOException{
        Files.createDirectories(file().getParent());
        Path temp=file().resolveSibling("bot.json.tmp");
        Files.writeString(temp,ServerFiles.GSON.toJson(value));
        Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING);
        settings=value;
        for(var a:agents)a.updateSpeed();
    }
    static void validate(Loadouts loadouts){if(added()){selectWeapon(loadouts);selectSecondary(loadouts);}}
    /** Swords, axes and any modded melee item that carries main-hand attack damage (e.g. lrtactical knives). */
    static boolean melee(ItemStack stack){
        if(stack.getItem() instanceof SwordItem||stack.getItem() instanceof AxeItem)return true;
        if(stack.isEmpty()||stack.getItem() instanceof GadgetItem||BotGun.supports(stack))return false;
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream().anyMatch(m->m.getAmount()>0);
    }
    /** Held secondaries replace the primary in hand; everything else is used as an ability by {@link BotGadgets}. */
    static boolean held(ItemStack stack){return BotGun.supports(stack)||stack.is(Items.BOW)||melee(stack);}
    static boolean supportedSecondary(Loadouts.Weapon w){
        // Command-only entries run a player command; the bot has no player to run it as.
        if(w.commandOnly())return false;
        var stack=w.stack();return held(stack)||BotGadgets.supported(stack);
    }
    static Loadouts.Weapon selectSecondary(Loadouts loadouts){
        if(settings.secondary().equals("none"))return null;
        var selected=loadouts.secondary().stream().filter(w->settings.secondary().isEmpty()||w.id().equals(settings.secondary()))
                .filter(BotManager::supportedSecondary).findFirst().orElse(null);
        if(selected==null&&!settings.secondary().isEmpty())throw new IllegalArgumentException("봇이 쓸 수 없는 보조무기입니다(명령어 전용·폭탄·해체기 제외). ID를 확인하세요.");
        return selected;
    }
    static Loadouts.Weapon selectWeapon(Loadouts loadouts){
        return loadouts.primary().stream().filter(w->settings.weapon().isEmpty()||w.id().equals(settings.weapon()))
                .filter(w->{var stack=w.stack();return stack.is(Items.BOW)||BotGun.supports(stack);}).findFirst()
                .orElseThrow(()->new IllegalArgumentException("봇 주무기는 TaCZ 총기 또는 minecraft:bow여야 합니다. /gungang bot weapon <주무기 ID>"));
    }
    static boolean weaponTaken(UUID player,String team,boolean primary,String id){
        return agents.stream().anyMatch(a->!a.id.equals(player)&&a.team.equals(team)&&(primary?a.weapon:a.secondary)!=null&&(primary?a.weapon:a.secondary).id().equals(id));
    }
    static void prepare(MinecraftServer s,ArenaMap map){
        close();server=s;arena=map;workNanos=workSamples=maxWorkNanos=0;
        if(added())try{activate();}catch(Exception error){fail(error);}
    }
    /** Every reserved slot joins on the team it has now; off/on during a match never changes sides or resets scores. */
    private static void activate()throws Exception{
        if(arena==null||!added())return;
        var loadouts=Loadouts.load();fault="";
        for(int slot:lobby.keySet()){
            if(agent(BotDatabase.id(slot))!=null)continue;
            var agent=new BotAgent(slot,lobbyTeam(slot));agents.add(agent);agent.activate(loadouts);
        }
        // Bots now in the match need their level emblems on every client.
        ProgressionService.broadcastLevels(server);
    }
    static void close(){
        BotDiagnostics.clear();
        for(var a:agents)a.close();agents.clear();
        BotMovement.forgetExits();BotWaypoints.forget();arena=null;
    }
    private static void removeAll(){for(var a:agents)a.remove();agents.clear();}
    static void died(UUID bot,UUID killer){var a=agent(bot);if(a!=null)a.died(killer);}
    private static void fail(Exception error){
        fault=error.getClass().getSimpleName()+": "+error.getMessage();removeAll();
        LogUtils.getLogger().error("[GunGang] Bots suspended; use /gungang bot status and /gungang bot on after fixing the cause",error);
        if(server!=null)for(var p:server.getPlayerList().getPlayers())if(p.hasPermissions(2))p.sendSystemMessage(Component.literal("[GunGang] 봇 중지: "+fault));
    }
    static void tick(boolean running){
        if(arena==null||!added()||!fault.isEmpty())return;
        long start=System.nanoTime();
        BotMovement.newTick();
        try{
            for(var a:List.copyOf(agents)){
                long begin=System.nanoTime();a.tick(running);
                long ms=(System.nanoTime()-begin)/1_000_000;
                // A bot tick over 20 ms is a visible hitch: log which bot and what it was doing (at most every 5 s).
                if(ms>=20&&server.getTickCount()>=slowLogAt){
                    slowLogAt=server.getTickCount()+100;
                    LogUtils.getLogger().warn("[GunGang] Slow bot tick: {} took {} ms ({})",a.name(),ms,a.bot==null?"spawning":a.tactics.action());
                }
            }
        }
        catch(Exception error){fail(error);}
        finally{long elapsed=System.nanoTime()-start;workNanos+=elapsed;workSamples++;maxWorkNanos=Math.max(maxWorkNanos,elapsed);}
    }

    // ---- events ----
    /** Gunfire within earshot of every other bot: players' and bots' shots alike. */
    static void heard(LivingEntity shooter){for(var a:agents)a.heard(shooter);}
    @SubscribeEvent public static void bow(net.minecraftforge.event.entity.player.ArrowLooseEvent event){if(event.getEntity() instanceof ServerPlayer p)heard(p);}
    @SuppressWarnings("unchecked")
    public static void registerTacz(){
        try{
            Class<net.minecraftforge.eventbus.api.Event> type=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.common.GunFireEvent");
            var shooter=type.getMethod("getShooter");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,type,event->{
                try{if(shooter.invoke(event) instanceof ServerPlayer p)heard(p);}catch(ReflectiveOperationException ignored){}
            });
        }catch(ClassNotFoundException absent){/* TaCZ is optional. */}
        catch(ReflectiveOperationException incompatible){LogUtils.getLogger().warn("[GunGang] Bot hearing: TaCZ fire event unavailable",incompatible);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void lethal(net.minecraftforge.event.entity.living.LivingDeathEvent event){
        var a=agent(event.getEntity());
        if(a!=null&&a.gadgets.lifeSupport(a.bot,event.getSource()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        if(arena==null||agents.isEmpty())return;
        var victim=event.getEntity();
        // Callouts: every bot on the victim's side learns that a teammate is under fire.
        if(BotEntity.participant(victim)&&event.getSource().getEntity() instanceof LivingEntity attacker&&BotEntity.participant(attacker)){
            String side=side(victim);
            for(var a:agents)if(a.team.equals(side))a.allyHit(victim,attacker);
        }
        var a=agent(victim);if(a!=null)a.hurt(event);
    }
    /** Bots never hurt their own side, and nobody hurts bots outside a running match. A bot may still hurt itself (its own fire). */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void attack(LivingAttackEvent event){
        Entity attacker=event.getSource().getEntity();var victim=event.getEntity();
        boolean botAttack=attacker!=null&&identity(attacker.getUUID()),botVictim=owns(victim);
        if(!botVictim&&!botAttack)return;
        if(arena==null||!settings.enabled()||!MatchManager.botRunning()){event.setCanceled(true);return;}
        if(botAttack&&botVictim&&attacker.getUUID().equals(victim.getUUID()))return;
        // A bot's fake player (thrown items) carries the bot's UUID: its side is the bot's, not a lobby entry.
        String attackerSide=botAttack?team(attacker.getUUID()):attacker instanceof ServerPlayer p?LobbyManager.team(p.getUUID()):"";
        String victimSide=botVictim?side(victim):victim instanceof ServerPlayer p?LobbyManager.team(p.getUUID()):"";
        boolean teams=Set.of("ALPHA","BRAVO").contains(attackerSide)&&Set.of("ALPHA","BRAVO").contains(victimSide);
        if(botAttack&&victim instanceof ServerPlayer p&&!botVictim&&(!teams||attackerSide.equals(victimSide)||LobbyManager.respawning(p.getUUID())))event.setCanceled(true);
        if(botAttack&&botVictim&&(!teams||attackerSide.equals(victimSide)))event.setCanceled(true);
        if(botVictim&&!botAttack&&attacker instanceof ServerPlayer p&&(!teams||attackerSide.equals(victimSide)||p.isSpectator()))event.setCanceled(true);
    }

    // ---- commands ----
    private interface Work{void run()throws Exception;}
    private static int command(CommandSourceStack source,Work work){try{work.run();return 1;}catch(Exception error){source.sendFailure(Component.literal("봇: "+error.getMessage()));return 0;}}
    private static void status(CommandSourceStack source){
        var text=new StringBuilder(String.format(Locale.ROOT,
                "AI 봇: %s / 로비 %d/%d명 / %s\n팀=%s, 무기=%s, 보조무기=%s, 난이도=%s, 이동 배율=%.2f, thinkTicks=%d, pathTicks=%d, range=%d, reactionTicks=%d, aimError=%.1f, turnRate=%.0f, memorySeconds=%.1f, fov=%d\nAI 관리 시간 평균 %.3f ms, 최대 %.3f ms (엔티티·총기 전체 비용 제외)",
                settings.enabled()?"ON":"OFF",lobbyCount(),MAX,arena==null?"경기 밖":"경기 중 "+agents.size()+"명",
                settings.team(),settings.weapon().isEmpty()?"자동":settings.weapon(),settings.secondary().isEmpty()?"자동":settings.secondary(),settings.difficulty(),settings.movementSpeed(),
                settings.thinkTicks(),settings.pathTicks(),settings.range(),settings.reactionTicks(),settings.aimError(),settings.turnRate(),settings.memorySeconds(),settings.fov(),
                workSamples==0?0:workNanos/1e6/workSamples,maxWorkNanos/1e6));
        if(arena==null)for(int slot:lobby.keySet())text.append("\n").append(name(slot)).append(": 로비 대기 · ").append(lobbyTeam(slot)).append("팀");
        for(var a:agents)text.append("\n").append(a.status());
        if(!fault.isEmpty())text.append("\n오류: ").append(fault);
        source.sendSuccess(()->Component.literal(text.toString()),false);
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,Integer> slotArgument(){
        return Commands.argument("bot",IntegerArgumentType.integer(1,MAX));
    }
    static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        var commands=Commands.literal("bot").requires(s->s.hasPermission(2)).executes(c->{status(c.getSource());return 1;});
        commands.then(Commands.literal("status").executes(c->{status(c.getSource());return 1;}));
        commands.then(Commands.literal("debug")
                .then(slotArgument().executes(c->command(c.getSource(),()->BotDiagnostics.watch(c.getSource(),IntegerArgumentType.getInteger(c,"bot")-1))))
                .then(Commands.literal("off").executes(c->command(c.getSource(),()->BotDiagnostics.off(c.getSource()))))
                .then(Commands.literal("info").executes(c->command(c.getSource(),()->BotDiagnostics.info(c.getSource())))));
        commands.then(Commands.literal("name").then(slotArgument().then(Commands.argument("name",StringArgumentType.greedyString())
                .executes(c->command(c.getSource(),()->{
                    requireLobby();requireDatabase();int slot=IntegerArgumentType.getInteger(c,"bot")-1;
                    database.update(database.profile(slot).named(StringArgumentType.getString(c,"name")));
                    LobbyManager.botChanged();c.getSource().sendSuccess(()->Component.literal((slot+1)+"번 봇 이름: "+name(slot)),true);
                })))));
        commands.then(Commands.literal("skin").then(slotArgument().then(Commands.argument("texture",StringArgumentType.greedyString())
                .suggests((c,b)->{b.suggest("default");BotSkinFiles.names().forEach(b::suggest);return b.buildFuture();})
                .executes(c->command(c.getSource(),()->{
                    requireLobby();requireDatabase();int slot=IntegerArgumentType.getInteger(c,"bot")-1;String value=StringArgumentType.getString(c,"texture");
                    if(value.startsWith(BotSkinFiles.PREFIX))BotSkinFiles.read(value); // Reject a missing or malformed file before saving it.
                    database.update(database.profile(slot).skinned(value.equals("default")?BotDatabase.DEFAULT_SKIN:value));loadSkin(slot);
                    LobbyManager.botChanged();c.getSource().sendSuccess(()->Component.literal((slot+1)+"번 봇 스킨: "+skin(slot)),true);
                })))));
        commands.then(Commands.literal("reload").executes(c->command(c.getSource(),()->{
            requireLobby();database=new BotDatabase(ServerFiles.root().resolve("bot-db.json"));String warning=loadSkins();
            LobbyManager.botChanged();c.getSource().sendSuccess(()->Component.literal("봇 프로필과 전적을 불러왔습니다."+(warning.isEmpty()?"":" · 스킨 파일 오류:"+warning)),true);
        })));
        commands.then(Commands.literal("stats").executes(c->command(c.getSource(),()->stats(c.getSource(),-1)))
                .then(slotArgument().executes(c->command(c.getSource(),()->stats(c.getSource(),IntegerArgumentType.getInteger(c,"bot")-1)))));
        commands.then(Commands.literal("on").executes(c->command(c.getSource(),()->{
            requireDatabase();save(settings.enabled(true));fault="";LobbyManager.botChanged();
            if(arena!=null)try{activate();}catch(Exception error){fail(error);throw error;}status(c.getSource());
        })));
        commands.then(Commands.literal("off").executes(c->command(c.getSource(),()->{
            settings=settings.enabled(false);lobby.clear();removeAll();LobbyManager.botChanged();save(settings);status(c.getSource());
        })));
        commands.then(Commands.literal("preset").then(Commands.argument("name",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(List.of("low","normal","high","responsive"),b))
                .executes(c->command(c.getSource(),()->{save(settings.preset(StringArgumentType.getString(c,"name")));status(c.getSource());}))));
        commands.then(Commands.literal("team").then(Commands.argument("team",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(List.of("AUTO","ALPHA","BRAVO"),b))
                .executes(c->command(c.getSource(),()->{requireLobby();save(settings.team(StringArgumentType.getString(c,"team").toUpperCase(Locale.ROOT)));LobbyManager.botChanged();status(c.getSource());}))));
        commands.then(Commands.literal("weapon").then(Commands.argument("id",StringArgumentType.word())
                .suggests((c,b)->{b.suggest("auto");try{Loadouts.load().primary().forEach(w->b.suggest(w.id()));}catch(Exception ignored){}return b.buildFuture();})
                .executes(c->command(c.getSource(),()->{
                    requireLobby();String id=StringArgumentType.getString(c,"id");var candidate=settings.weapon(id.equals("auto")?"":id);
                    var previous=settings;try{settings=candidate;selectWeapon(Loadouts.load());}finally{settings=previous;}
                    save(candidate);status(c.getSource());
                }))));
        var set=Commands.literal("set");
        commands.then(Commands.literal("secondary").then(Commands.argument("id",StringArgumentType.word())
                .suggests((c,b)->{b.suggest("auto");b.suggest("none");try{Loadouts.load().secondary().stream().filter(BotManager::supportedSecondary).forEach(w->b.suggest(w.id()));}catch(Exception ignored){}return b.buildFuture();})
                .executes(c->command(c.getSource(),()->{
                    requireLobby();String id=StringArgumentType.getString(c,"id");var candidate=settings.secondary(id.equals("auto")?"":id);
                    var previous=settings;try{settings=candidate;selectSecondary(Loadouts.load());}finally{settings=previous;}
                    save(candidate);status(c.getSource());
                }))));
        commands.then(Commands.literal("difficulty").then(Commands.argument("name",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(List.of("easy","normal","hard","expert"),b))
                .executes(c->command(c.getSource(),()->{save(settings.difficulty(StringArgumentType.getString(c,"name")));status(c.getSource());}))));
        commands.then(waypoints());
        for(String key:List.of("thinkTicks","pathTicks","range","reactionTicks","aimError","movementSpeed","turnRate","memorySeconds","fov")){
            double min=switch(key){case "thinkTicks"->5;case "pathTicks"->10;case "range"->8;case "reactionTicks"->2;case "turnRate"->2;case "fov"->60;default->.5;};
            double max=switch(key){case "pathTicks"->200;case "range"->64;case "aimError"->15;case "movementSpeed"->2;case "turnRate"->90;case "memorySeconds"->30;case "fov"->360;default->100;};
            set.then(Commands.literal(key).then(Commands.argument("value",DoubleArgumentType.doubleArg(min,max)).executes(c->command(c.getSource(),()->{
                double value=DoubleArgumentType.getDouble(c,"value");if(!Set.of("aimError","movementSpeed","turnRate","memorySeconds").contains(key)&&value!=Math.rint(value))throw new IllegalArgumentException("정수를 입력하세요.");
                save(settings.set(key,value));for(var a:agents)a.nextThink=a.nextPath=0;status(c.getSource());
            }))));
        }
        commands.then(set);root.then(commands);
    }
    private static void stats(CommandSourceStack source,int only){
        requireDatabase();
        for(int slot=0;slot<MAX;slot++){
            if(only>=0&&slot!=only)continue;
            var r=database.profile(slot).statistics();var progress=ProgressionService.progress(r.experience());
            source.sendSystemMessage(Component.literal(String.format(Locale.ROOT,"%d번 %s · Lv. %d (경험치 %d) · 통산 %d경기 · %d승 · %d킬 / %d데스 · 딜량 %.1f · 플레이 %d초",
                    slot+1,name(slot),progress.level(),r.experience(),r.matches(),r.wins(),r.kills(),r.deaths(),r.damage(),r.playSeconds())));
        }
    }
    /**
     * {@code /gungang bot waypoint <map> add <route|cover|sniper|objective> [any|alpha|bravo]} at the caller's feet,
     * {@code remove} (nearest within 3 blocks), {@code list}, {@code show} (particles for the caller), {@code clear}.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> waypoints(){
        var map=Commands.argument("map",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(LobbyManager.mapIds(),b));
        var add=Commands.argument("type",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("route","cover","sniper","objective"),b))
                .executes(c->command(c.getSource(),()->addWaypoint(c.getSource(),StringArgumentType.getString(c,"map"),StringArgumentType.getString(c,"type"),"any")))
                .then(Commands.argument("team",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("any","alpha","bravo"),b))
                        .executes(c->command(c.getSource(),()->addWaypoint(c.getSource(),StringArgumentType.getString(c,"map"),StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"team")))));
        map.then(Commands.literal("add").then(add));
        map.then(Commands.literal("remove").executes(c->command(c.getSource(),()->{
            String id=StringArgumentType.getString(c,"map");var here=c.getSource().getPosition();var points=new ArrayList<>(BotWaypoints.get(id).points());
            var nearest=points.stream().min(Comparator.comparingDouble(p->p.position().distanceToSqr(here))).filter(p->p.position().distanceToSqr(here)<=9)
                    .orElseThrow(()->new IllegalArgumentException("3블록 안에 웨이포인트가 없습니다."));
            points.remove(nearest);BotWaypoints.save(id,new BotWaypoints.Data(points));
            c.getSource().sendSuccess(()->Component.literal("웨이포인트 삭제: "+nearest.type()+" ("+points.size()+"개 남음)"),true);
        })));
        map.then(Commands.literal("list").executes(c->command(c.getSource(),()->{
            var points=BotWaypoints.get(StringArgumentType.getString(c,"map")).points();
            var text=new StringBuilder("봇 웨이포인트 "+points.size()+"개");
            for(var p:points)text.append(String.format(Locale.ROOT,"\n%s %s (%.1f, %.1f, %.1f)",p.type(),p.team(),p.x(),p.y(),p.z()));
            c.getSource().sendSystemMessage(Component.literal(text.toString()));
        })));
        map.then(Commands.literal("show").executes(c->command(c.getSource(),()->{
            var player=c.getSource().getPlayerOrException();
            for(var p:BotWaypoints.get(StringArgumentType.getString(c,"map")).points()){
                var particle=switch(p.type()){case COVER->net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;case SNIPER->net.minecraft.core.particles.ParticleTypes.FLAME;
                    case OBJECTIVE->net.minecraft.core.particles.ParticleTypes.END_ROD;default->net.minecraft.core.particles.ParticleTypes.CLOUD;};
                player.serverLevel().sendParticles(player,particle,true,p.x(),p.y()+1,p.z(),12,.15,.5,.15,0);
            }
        })));
        map.then(Commands.literal("clear").executes(c->command(c.getSource(),()->{
            BotWaypoints.save(StringArgumentType.getString(c,"map"),new BotWaypoints.Data(List.of()));
            c.getSource().sendSuccess(()->Component.literal("봇 웨이포인트를 모두 지웠습니다."),true);
        })));
        return Commands.literal("waypoint").then(map);
    }
    private static void addWaypoint(CommandSourceStack source,String map,String type,String side)throws Exception{
        var position=source.getPosition();
        var point=new BotWaypoints.Point(BotWaypoints.Type.valueOf(type.toUpperCase(Locale.ROOT)),Math.floor(position.x)+.5,Math.floor(position.y),Math.floor(position.z)+.5,side.toUpperCase(Locale.ROOT));
        var points=new ArrayList<>(BotWaypoints.get(map).points());points.add(point);
        BotWaypoints.save(map,new BotWaypoints.Data(points));
        source.sendSuccess(()->Component.literal("웨이포인트 추가: "+point.type()+" "+point.team()+" ("+points.size()+"개)"),true);
    }
    private static void requireDatabase(){if(database==null)throw new IllegalStateException("bot-db.json을 확인하고 /gungang bot reload를 실행하세요.");}
    private static void requireLobby(){if(MatchManager.active())throw new IllegalStateException("팀·무기는 경기 종료 후 변경하세요. 성능 설정과 on/off는 경기 중에도 가능합니다.");}
}
