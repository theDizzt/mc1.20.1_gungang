package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Isolated, opt-in integration checks; excludes all test players and code from release jars. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class BotSmokeChecks {
    private static int gateBorn=-1,gateLeft=-1;
    @SubscribeEvent public static void projectileSpawn(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(active&&!event.loadedFromDisk()&&event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile p
                &&p.getOwner()!=null&&BotManager.identity(p.getOwner().getUUID())){
            sawShot=true;
            var first=BotManager.agent(0);if(first!=null&&first.usingSecondary)secondaryShot=true;
        }
    }
    @SubscribeEvent public static void meleeHit(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        if(active&&event.getEntity()==player&&event.getSource().getEntity() instanceof BotEntity bot
                &&bot.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem)secondaryMelee=true;
    }
    private static MinecraftServer server;
    private static ServerPlayer player;
    private static int tick;
    private static boolean active;
    private static boolean sawShot,moved,secondaryShot,secondaryMelee;
    private static DeployableEntity mine;
    private static BlockPos healPos;
    private static String autoPrimary,autoSecondary;
    private static ArenaMap arena;
    private static boolean parkourLanded;
    private static net.minecraft.world.phys.Vec3 parkourLanding;
    private static void parkourSetup(int gap,boolean raised)throws Exception{
        var level=server.overworld();var b=bot();
        for(int x=5000;x<=5006;x++)for(int z=0;z<=2;z++)for(int y=119;y<=125;y++)
            level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        for(int z=0;z<=2;z++){
            level.setBlock(new BlockPos(5000,119,z),Blocks.STONE.defaultBlockState(),3);
            for(int x=5001+gap;x<=5006;x++)level.setBlock(new BlockPos(x,raised?120:119,z),Blocks.STONE.defaultBlockState(),3);
        }
        b.setPos(5000.5,120,1.5);b.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);b.setOnGround(true);
        var landing=new net.minecraft.world.phys.Vec3(5001.5+gap,raised?121:120,1.5);
        parkourLanding=landing;
        check(BotParkour.plan(b,arena,landing)!=null,"Safe gap has a jump trajectory");
        check(BotParkour.plan(b,arena,landing.add(3,0,0))==null,"Reject oversized jump");
        var roof=new BlockPos(5001,122,1);level.setBlock(roof,Blocks.STONE.defaultBlockState(),3);
        check(BotParkour.plan(b,arena,landing)==null,"Reject low ceiling in jump arc");level.removeBlock(roof,false);
        var mapJson=ServerFiles.GSON.toJsonTree(arena).getAsJsonObject();
        mapJson.add("deathZones",ServerFiles.GSON.toJsonTree(java.util.List.of(new DeathZone(5001,120,0,5002,124,3))));
        check(BotParkour.plan(b,ServerFiles.GSON.fromJson(mapJson,ArenaMap.class),landing)==null,"Reject death zone along jump arc");
        var floor=BlockPos.containing(landing).below();level.setBlock(floor,Blocks.MAGMA_BLOCK.defaultBlockState(),3);
        check(BotParkour.plan(b,arena,landing)==null,"Reject harmful landing block");level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        parkourLanded=false;
        // Exercise the normal travel fallback, not just the jump helper.
        BotMovement.newTick();BotManager.agent(0).movement.reset();
        BotManager.agent(0).movement.travel(b,arena,"ALPHA",landing,server.getTickCount());
        if(!b.parkour.active()){
            b.getNavigation().stop();
            BotManager.agent(0).movement.travel(b,arena,"ALPHA",landing,server.getTickCount()+10);
        }
        check(b.parkour.active(),"Normal travel starts safe gap jump");
        if(gap==3)check(b.isSprinting(),"Three-block gap activates dash");
    }
    private static java.lang.reflect.Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static BotEntity bot(){var agent=BotManager.agent(0);return agent==null?null:agent.bot;}
    private static Loadouts.Weapon weapon(){return BotManager.agent(0).weapon;}
    private static Loadouts.Weapon secondary(){return BotManager.agent(0).secondary;}
    private static MatchLedger ledger()throws Exception{return (MatchLedger)field(MatchManager.class,"ledger").get(null);}
    private static void command(String command){check(server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gungang bot "+command)==1,"Command failed: "+command);}
    private static void action(String name,String value,String target)throws Exception{
        ((Map<?,?>)field(LobbyManager.class,"LAST_ACTION").get(null)).clear();
        LobbyManager.action(player,new GungangNetwork.Action(name,value,target));
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        server=event.getServer();server.execute(()->{try{setup();active=true;}catch(Throwable error){finish(error);}});
    }
    @SuppressWarnings("unchecked") private static void setup()throws Exception{
        var level=server.overworld();level.setDayTime(1000);
        wall(false);
        for(int x=-8;x<=32;x++)for(int z=-8;z<=8;z++)level.setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),3);
        // The test world persists between runs: remove the gated spawn room a previous run built.
        for(int x=16;x<=25;x++)for(int z=-4;z<=4;z++)for(int y=101;y<=104;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BotSmokeHuman");
        // Forge FakePlayer is permanently invulnerable; use a real ServerPlayer with a no-op connection.
        player=new ServerPlayer(server,level,profile);
        player.connection=net.minecraftforge.common.util.FakePlayerFactory.get(level,profile).connection;
        field(ServerPlayer.class,"spawnInvulnerableTime").setInt(player,0);
        player.setPos(.5,101,.5);player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);level.addNewPlayer(player);
        ((List<ServerPlayer>)field(net.minecraft.server.players.PlayerList.class,"players").get(server.getPlayerList())).add(player);
        ((Map<UUID,ServerPlayer>)field(net.minecraft.server.players.PlayerList.class,"playersByUUID").get(server.getPlayerList())).put(player.getUUID(),player);
        var type=Class.forName(LobbyManager.class.getName()+"$Entry");var ctor=type.getDeclaredConstructor(ServerPlayer.class);ctor.setAccessible(true);
        var entry=ctor.newInstance(player);field(type,"team").set(entry,"ALPHA");
        ((Map<UUID,Object>)field(LobbyManager.class,"PLAYERS").get(null)).put(player.getUUID(),entry);
        boolean tacz=net.minecraftforge.fml.ModList.get().isLoaded("tacz");
        if(tacz){
            var weapon=new Loadouts.Weapon("bot_smoke","{id:\"tacz:modern_kinetic_gun\",Count:1b,tag:{GunId:\"tacz:ak47\",GunCurrentAmmoCount:1,HasBulletInBarrel:1b,GunFireMode:\"AUTO\"}}",
                    "Bot test AK47","",new Loadouts.Specs("","","","",""),"");
            java.nio.file.Files.writeString(ServerFiles.root().resolve("server/weapons/bot_smoke.json"),ServerFiles.GSON.toJson(weapon));
            var sidearm=new Loadouts.Weapon("bot_sidearm","{id:\"tacz:modern_kinetic_gun\",Count:1b,tag:{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:1,HasBulletInBarrel:1b,GunFireMode:\"SEMI\"}}",
                    "Bot test sidearm","",new Loadouts.Specs("","","","",""),"");
            java.nio.file.Files.createDirectories(ServerFiles.root().resolve("server/sub_weapons"));
            java.nio.file.Files.writeString(ServerFiles.root().resolve("server/sub_weapons/bot_sidearm.json"),ServerFiles.GSON.toJson(sidearm));
        }else{
            var sword=new Loadouts.Weapon("bot_sword","{id:\"minecraft:iron_sword\",Count:1b}","Bot sword","",new Loadouts.Specs("","","","",""),"");
            java.nio.file.Files.createDirectories(ServerFiles.root().resolve("server/sub_weapons"));
            java.nio.file.Files.writeString(ServerFiles.root().resolve("server/sub_weapons/bot_sword.json"),ServerFiles.GSON.toJson(sword));
        }
        command("off");check(!BotManager.settings().enabled(),"Default off command");command("preset low");command("set range 32");
        command("set thinkTicks 5");command("set reactionTicks 4");command("set aimError 0.5");command("team AUTO");
        command("name 1 Training Bot");command("skin 1 minecraft:textures/entity/player/wide/steve.png");command("reload");
        check(BotManager.name().equals("Training Bot"),"Bot profile commands persist and reload");
        command("waypoint bot_smoke_map clear");command("waypoint bot_smoke_map add cover alpha");command("waypoint bot_smoke_map add route");
        check(BotWaypoints.get("bot_smoke_map").points().size()==2&&BotWaypoints.of("bot_smoke_map",BotWaypoints.Type.COVER,"BRAVO").isEmpty()
                &&BotWaypoints.of("bot_smoke_map",BotWaypoints.Type.ROUTE,"BRAVO").size()==1,"Waypoint commands store typed, team-filtered points");
        BotWaypoints.forget();check(BotWaypoints.get("bot_smoke_map").points().size()==2,"Waypoints persist to file");
        command("waypoint bot_smoke_map clear");command("difficulty hard");check(BotManager.settings().difficulty().equals("hard")&&BotManager.settings().turnRate()==22,"Difficulty preset applies");
        command("weapon "+(tacz?"bot_smoke":"bow"));command("secondary "+(tacz?"bot_sidearm":"bot_sword"));
        command("set movementSpeed 1.35");command("on");check(bot()==null,"No bot outside TDM");
        check(ServerFiles.GSON.fromJson(java.nio.file.Files.readString(ServerFiles.root().resolve("server/bot.json")),BotSettings.class).enabled(),"Settings persisted");
        check(!BotManager.added(),"Master on does not automatically reserve a bot");
        field(LobbyManager.class,"enabled").setBoolean(null,true);
        field(LobbyManager.class,"selectedMode").set(null,"DEATHMATCH");
        field(LobbyManager.class,"loadouts").set(null,Loadouts.load());
        action("botAdd","","");check(!BotManager.added(),"Non-admin lobby request rejected");
        server.getPlayerList().op(profile);
        field(LobbyManager.class,"selectedMode").set(null,"CONTROL");
        action("botAdd","","");check(!BotManager.added(),"Other modes cannot add bots");
        field(LobbyManager.class,"selectedMode").set(null,"DEATHMATCH");
        command("off");action("botAdd","","");check(!BotManager.added(),"Stale add packet cannot bypass master OFF");
        command("on");field(type,"ready").setBoolean(entry,true);
        action("botAdd","","");check(BotManager.added()&&bot()==null,"Lobby reserves bot without spawning it");
        check(!field(type,"ready").getBoolean(entry),"Adding bot resets readiness without starting game");
        action("team","ALPHA",BotManager.ID.toString());check(BotManager.lobbyTeam().equals("ALPHA"),"Admin can assign bot team in lobby");
        action("team","SPECTATOR",BotManager.ID.toString());check(BotManager.lobbyTeam().equals("ALPHA"),"Bot cannot be spectator");
        action("botRemove","","");check(!BotManager.added()&&BotManager.settings().enabled(),"Remove does not turn off master switch");
        command("team AUTO");action("botAdd","","");action("botAdd","","");check(BotManager.lobbyCount()==2,"A second add reserves a second bot");
        action("botAdd","","");action("botAdd","","");action("botAdd","","");check(BotManager.lobbyCount()==BotManager.MAX,"Bots are capped at "+BotManager.MAX);
        check(!BotManager.team(BotDatabase.id(0)).equals(BotManager.team(BotDatabase.id(1)))||!BotManager.team(BotDatabase.id(1)).equals(BotManager.team(BotDatabase.id(2))),"Auto team spreads bots");
        action("team","ALPHA",BotDatabase.id(2).toString());check(BotManager.team(BotDatabase.id(2)).equals("ALPHA"),"Each bot keeps its own lobby team");
        for(int i=1;i<BotManager.MAX;i++)action("botRemove","",BotDatabase.id(i).toString());
        check(BotManager.lobbyCount()==1&&BotManager.lobbyIds().get(0).equals(BotManager.ID),"Removing a chosen bot keeps the others");
        var json=ServerFiles.GSON.toJsonTree(ArenaMap.example()).getAsJsonObject();
        json.add("alphaSpawn",ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(.5,101,.5,0,0)));
        json.add("bravoSpawn",ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(20.5,101,.5,180,0)));
        arena=new DeathmatchRules(120,100).arena(ServerFiles.GSON.fromJson(json,ArenaMap.class));
        MatchManager.prepare(server,arena,new DeathmatchRules(120,100));
        check(bot()!=null&&BotManager.team().equals("BRAVO"),"One bot joins smaller team");
        check(bot().getCustomName().getString().equals(BotManager.name())&&bot().skin().equals(BotManager.skin()),"Entity uses saved identity");
        check(ledger().rows().stream().anyMatch(r->r.id().equals(BotManager.ID.toString())&&r.name().equals(BotManager.name())&&r.skin().equals(BotManager.skin())),"Tab snapshot includes bot identity");
        check(!StationBlocks.BRAVO_GATE.get().allows(bot())&&!StationBlocks.ALPHA_GATE.get().allows(bot()),"Preparation keeps both bot gates closed");
        movementGeometry();
        float hp=bot().getHealth();bot().hurt(level.damageSources().playerAttack(player),10);check(bot().getHealth()==hp,"Preparation invulnerable");
        field(LobbyManager.class,"activeMap").set(null,arena);field(LobbyManager.class,"phase").set(null,"RUNNING");
        TeamPoints.refresh(server);TeamPoints.STATE.mark(player.getUUID(),"minecraft:overworld",1,101,1);
        MatchManager.start();
        check(TeamPoints.STATE.view("ALPHA",0).points().isEmpty(),"Match start clears preparation points");
        check(StationBlocks.BRAVO_GATE.get().allows(bot())&&!StationBlocks.ALPHA_GATE.get().allows(bot()),"Running bot spawn gates respect teams");
        // LobbyManager drives the normal match tick once per server tick.
        wall(true);
        teamPointChecks();
    }
    @SuppressWarnings("unchecked") private static void teamPointChecks()throws Exception {
        var entry=((Map<?,?>)field(LobbyManager.class,"PLAYERS").get(null)).get(player.getUUID());
        var team=field(entry.getClass(),"team");float yaw=player.getYRot(),pitch=player.getXRot();var agent=BotManager.agent(bot());
        try {
            player.setYRot(-90);player.setXRot(0);TeamPoints.request(player);
            var red=TeamPoints.STATE.view("ALPHA",0);
            check(red.points().size()==1&&Math.abs(red.points().get(0).x()-9.95)<.1,"Point request uses the server's first wall hit");
            check(TeamPoints.STATE.view("BRAVO",0).points().isEmpty(),"Enemy team never receives point coordinates");
            check(TeamPoints.latest("BRAVO","minecraft:overworld",0)==null,"Enemy bots cannot read point");
            team.set(entry,"BRAVO");TeamPoints.refresh(server);
            check(TeamPoints.STATE.view("ALPHA",0).points().isEmpty()&&TeamPoints.STATE.view("BRAVO",0).points().isEmpty(),"Changing teams discards the old point");
            TeamPoints.clear(server);TeamPoints.request(player);
            var point=TeamPoints.latest("BRAVO","minecraft:overworld",0);check(point!=null,"Friendly bot receives team point");
            BotMovement.newTick();agent.nextThink=agent.nextPath=server.getTickCount();agent.tick(true);
            check(agent.tactics.action()==BotTactics.Action.RALLY,"Idle friendly bot chooses to investigate team point");
            TeamPoints.clear(server);player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);TeamPoints.request(player);
            check(TeamPoints.STATE.view("BRAVO",0).points().isEmpty(),"Spectator cannot issue point requests");
            System.out.println("TEAM_POINT_SERVER_PASS");
            var source=player.createCommandSourceStack().withPermission(2);
            check(server.getCommands().performPrefixedCommand(source,"gungang bot debug 1")==1&&BotDiagnostics.watching(player.getUUID()),"Operator can select a bot for private diagnostics");
            check(BotDiagnostics.details(agent).contains("최근 경로 실패="),"Diagnostic snapshot includes navigation failure details");
            check(server.getCommands().performPrefixedCommand(source,"gungang bot debug info")==1,"Diagnostic info command works");
            check(server.getCommands().performPrefixedCommand(source,"gungang bot debug off")==1&&!BotDiagnostics.watching(player.getUUID()),"Diagnostic off removes tracking");
            check(server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(0),"gungang bot debug 1")==0,"Non-operators cannot enable diagnostics");
            BotDiagnostics.watch(source,0);BotDiagnostics.clear();
            check(!BotDiagnostics.watching(player.getUUID()),"Match cleanup clears diagnostic watchers");
            System.out.println("BOT_DIAGNOSTICS_PASS");
        }finally {
            team.set(entry,"ALPHA");player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);player.setYRot(yaw);player.setXRot(pitch);
            TeamPoints.clear(server);agent.actions.reset();agent.tactics.reset(server.getTickCount());agent.perception.reset();agent.movement.reset();agent.movement.prepareSpawnExit();bot().getNavigation().stop();
            agent.nextThink=agent.nextPath=server.getTickCount();BotMovement.newTick();
        }
    }
    private static void wall(boolean present){for(int z=-8;z<=8;z++)for(int y=101;y<=105;y++)server.overworld().setBlock(new BlockPos(10,y,z),present?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
    private static void spawnCorridorGeometry()throws Exception {
        var b=bot();var level=server.overworld();var saved=b.position();
        try {
            for(int x=5998;x<=6010;x++)for(int z=-2;z<=10;z++)for(int y=239;y<=243;y++)
                level.setBlock(new BlockPos(x,y,z),y==239&&((x>=6000&&x<=6006&&z>=0&&z<=2)||(x>=6004&&x<=6006&&z>=0&&z<=6))?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
            var json=ServerFiles.GSON.toJsonTree(arena).getAsJsonObject();
            json.add("alphaSpawn",ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(6000.5,240,1.5,0,0)));
            json.add("bravoSpawn",ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(6040.5,240,1.5,0,0)));
            json.add("alphaArea",ServerFiles.GSON.toJsonTree(new ArenaMap.Area(5999,239,-1,6025,244,20)));
            var map=ServerFiles.GSON.fromJson(json,ArenaMap.class);b.setPos(6000.5,240,1.5);b.setOnGround(true);
            var movement=new BotMovement();BotMovement.newTick();
            var point=movement.localPatrol(b,map,"ALPHA",server.getTickCount(),b.position(),new net.minecraft.world.phys.Vec3(6040.5,240,1.5));
            check(point!=null&&BotMovement.nearSpawn(map,"ALPHA",point),"A winding spawn corridor needs reachable intermediate points inside its room");
            var path=BotMovement.path(b,BlockPos.containing(point),0);
            check(path!=null&&path.canReach(),"Short corridor step is reachable without teleporting");
            check(!BotMovement.clearOfSpawn(map,"ALPHA",new net.minecraft.world.phys.Vec3(6015,240,1.5)),"Do not camp just outside a door");
            var forward=new net.minecraft.world.phys.Vec3(6030,240,1.5);var rear=new net.minecraft.world.phys.Vec3(6000,240,1.5);
            check(!BotMovement.forwardAlly(map,"ALPHA",forward,rear),"An idle rear teammate must not pull the leader back to spawn");
            check(BotMovement.forwardAlly(map,"ALPHA",rear,forward),"An advancing teammate remains a regroup target");
            System.out.println("BOT_WINDING_SPAWN_AND_FORWARD_REGROUP_PASS");
        } finally { b.setPos(saved);b.getNavigation().stop();BotMovement.newTick(); }
    }
    private static void movementGeometry()throws Exception {
        spawnCorridorGeometry();
        BotMovement.newTick();
        var b=bot();var level=server.overworld();var original=b.position();
        var from=new net.minecraft.world.phys.Vec3(24.5,101,.5);
        b.setPos(from);var movement=new BotMovement();
        for(int z=-2;z<=2;z++)for(int y=101;y<=103;y++)level.setBlock(new BlockPos(23,y,z),Blocks.STONE.defaultBlockState(),3);
        var scout=movement.scoutCover(b,arena,"ALPHA");
        check(scout!=null&&scout.spot().distanceToSqr(from)<1,"Scout holds cover against the opposing approach before seeing enemies");
        b.setPos(.5,101,.5);
        check(movement.scoutCover(b,arena,"ALPHA")==null,"Scout never camps at its own spawn");
        b.setPos(from);
        for(int z=-2;z<=2;z++)for(int y=101;y<=103;y++)level.setBlock(new BlockPos(23,y,z),Blocks.AIR.defaultBlockState(),3);
        System.out.println("BOT_SCOUT_COVER_PASS");
        check(BotMovement.nearSpawn(arena,"ALPHA",new net.minecraft.world.phys.Vec3(.5,101,.5)),"Own spawn is excluded from idle patrol and regroup");
        check(!BotMovement.nearSpawn(arena,"ALPHA",from),"Forward positions remain eligible");
        BotMovement.newTick();b.setOnGround(true);
        var local=movement.localPatrol(b,arena,"ALPHA",server.getTickCount(),new net.minecraft.world.phys.Vec3(.5,101,.5),new net.minecraft.world.phys.Vec3(20.5,101,.5));
        check(local!=null&&!BotMovement.nearSpawn(arena,"ALPHA",local),"Local fallback selects a reachable point away from spawn");
        var localPath=BotMovement.path(b,BlockPos.containing(local),0);
        check(localPath!=null&&localPath.canReach(),"Fallback never submits an unreachable global destination");
        BotMovement.newTick();
        System.out.println("BOT_PATROL_SPAWN_EXCLUSION_PASS");
        check(BotMovement.safeSegment(b,arena,from,from.add(0,0,2)),"Clear supported strafe is safe");
        var obstacle=new BlockPos(24,101,1);level.setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);
        check(!BotMovement.safeSegment(b,arena,from,from.add(0,0,2)),"Strafe rejects wall along route");
        level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),3);
        var floor=new BlockPos(24,100,2);level.setBlock(floor,Blocks.AIR.defaultBlockState(),3);
        check(!BotMovement.safeSegment(b,arena,from,from.add(0,0,2)),"Strafe rejects unsupported endpoint");
        level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        for(int y=101;y<=103;y++)level.setBlock(new BlockPos(23,y,0),Blocks.STONE.defaultBlockState(),3);
        var enemy=new net.minecraft.world.phys.Vec3(20.5,101,.5);
        check(movement.hidden(b,from,java.util.List.of(enemy.add(0,1.6,0))),"Cover blocks head and torso");
        var peek=movement.peekSpot(b,arena,from,enemy);
        check(peek!=null&&BotMovement.safeSegment(b,arena,from,peek)&&BotMovement.safeSegment(b,arena,peek,from),"Peek has safe outward and return routes");
        check(!movement.hidden(b,peek,java.util.List.of(enemy.add(0,1.6,0))),"Peek exposes firing angle");
        var threats=java.util.List.of(enemy.add(0,1.6,0));
        var quality=movement.coverQuality(b,arena,from,threats);
        check(quality.firingEdge()&&quality.exits()>0,"Good cover has both a firing edge and a protected retreat");
        for(int z=-3;z<=3;z++)for(int y=101;y<=103;y++)level.setBlock(new BlockPos(26,y,z),Blocks.STONE.defaultBlockState(),3);
        check(movement.coverQuality(b,arena,from,threats).exits()==0,"Blocked rear lanes penalize dead-end cover");
        for(int z=-3;z<=3;z++)for(int y=101;y<=103;y++)level.setBlock(new BlockPos(26,y,z),Blocks.AIR.defaultBlockState(),3);
        var agentMemory=BotManager.agent(b).movement;
        int memoryNow=server.getTickCount();agentMemory.hit(b,10,memoryNow);
        check(BotManager.routeCost(b,24,101,0)>0,"Damage memory adds a path cost at the hit location");
        agentMemory.reset();
        check(BotManager.routeCost(b,24,101,0)>0,"Damage memory survives movement reset and respawn");
        b.setOnGround(true);
        var rememberedPath=BotMovement.path(b,new BlockPos(30,101,0),0);
        check(rememberedPath!=null&&rememberedPath.canReach(),"Remembered danger remains traversable when escape needs it");
        b.setPos(18.5,101,.5);b.getNavigation().stop();BotMovement.newTick();
        var detour=BotMovement.path(b,new BlockPos(30,101,0),0);
        check(detour!=null&&detour.canReach(),"A route exists around the remembered firefight");
        int lateral=0;
        for(int i=0;i<detour.getNodeCount();i++)lateral=Math.max(lateral,Math.abs(detour.getNode(i).z));
        check(lateral>=4,"Learned path cost bends the route away from the firefight, offset="+lateral);
        b.setPos(from);b.getNavigation().stop();
        agentMemory.routeMemory.clear();BotMovement.newTick();
        check(BotManager.routeCost(b,24,101,0)==0,"Clearing match memory removes the learned cost");
        System.out.println("BOT_COVER_QUALITY_AND_ROUTE_MEMORY_PASS");
        coverCycle(b,movement,from,enemy,peek);
        // Start exposed: the route computed while choosing cover must be the route used to walk there.
        var coverMap=BotWaypoints.get(arena.id());
        BotWaypoints.save(arena.id(),new BotWaypoints.Data(List.of(new BotWaypoints.Point(BotWaypoints.Type.COVER,from.x,from.y,from.z,"BRAVO"))));
        b.setPos(peek);b.setOnGround(true);BotMovement.newTick();
        var coverActions=new BotActions();var coverCtx=coverActions.ctx();
        coverCtx.bot=b;coverCtx.arena=arena;coverCtx.team="BRAVO";coverCtx.movement=new BotMovement();
        coverCtx.perception=new BotPerception();coverCtx.perception.hit(b,player,100,0);
        coverCtx.perception.contact(player.getUUID()).position=enemy;
        coverCtx.settings=BotManager.settings();coverCtx.aim=new BotAim();coverCtx.now=100;coverCtx.pathTurn=true;coverCtx.ammo=1;
        var coverStatus=coverActions.run(BotTactics.Action.COVER);
        check(coverStatus==BotTree.Status.RUNNING&&!b.getNavigation().isDone(),"Exposed bot starts moving on chosen cover route: status="+coverStatus+" budget="+BotMovement.remainingPaths()+" from="+b.position()+" cover="+from);
        check(BotMovement.remainingPaths()==BotMovement.PATHS_PER_TICK-1,"Choosing and entering cover uses a single path search");
        var coverPath=b.getNavigation().getPath();BotMovement.newTick();coverCtx.now++;
        coverActions.run(BotTactics.Action.COVER);
        check(b.getNavigation().getPath()==coverPath&&BotMovement.remainingPaths()==BotMovement.PATHS_PER_TICK,"Active cover path is reused even on a path update tick");
        b.getNavigation().stop();b.setPos(from);
        BotWaypoints.save(arena.id(),coverMap);
        for(int y=101;y<=103;y++)level.setBlock(new BlockPos(23,y,0),Blocks.AIR.defaultBlockState(),3);
        regressions(b,from);
        b.setPos(original);
        System.out.println("BOT_MOVEMENT_GEOMETRY_PASS");
    }
    @SuppressWarnings("unchecked") private static void regressions(BotEntity b,net.minecraft.world.phys.Vec3 from)throws Exception {
        var level=server.overworld();var agent=BotManager.agent(b);var playerPos=player.position();float hp=b.getHealth();boolean onGround=b.onGround();
        var roof=new BlockPos(24,104,0);var roofBefore=level.getBlockState(roof);
        var packPos=new BlockPos(28,100,3);var packBefore=level.getBlockState(packPos);
        var hazards=(Map<UUID,BotPerception.Hazard>)field(BotPerception.class,"hazards").get(agent.perception);
        UUID hazardId=UUID.randomUUID();
        try {
            b.setOnGround(true); // setup runs before the freshly spawned entity's first physics tick.
            float head=b.getYHeadRot();b.setYHeadRot(90);player.setPos(from.add(-40,0,0));
            agent.perception.reset();agent.perception.update(b,agent.team,BotManager.settings(),100);
            check(agent.perception.visible()>0&&agent.eligible(player),"Clear 40-block enemy is seen with legacy 32-block local range");
            wall(true);agent.perception.update(b,agent.team,BotManager.settings(),105);
            check(agent.perception.visible()==0,"Distant enemy behind a wall is not seen");wall(false);b.setYHeadRot(head);
            player.setPos(from.add(BotManager.settings().visionRange()+8,0,0));
            agent.perception.reset();
            agent.hurt(new net.minecraftforge.event.entity.living.LivingHurtEvent(b,level.damageSources().playerAttack(player),5));
            check(agent.perception.underFire(server.getTickCount())&&agent.perception.recentDamage()==5,"Distant enemy damage triggers defensive perception");
            check(agent.perception.contact(player.getUUID())!=null&&!agent.eligible(player),"Distant attacker is remembered without becoming an eligible firing target");
            player.setPos(playerPos);agent.perception.reset();

            var failedMovement=new BotMovement();var unreachable=from.add(0,30,0);
            BotMovement.newTick();
            check(failedMovement.travel(b,arena,agent.team,unreachable,100),"Unreachable chase is abandoned");
            BotMovement.newTick();int budget=BotMovement.remainingPaths();
            check(failedMovement.travel(b,arena,agent.team,unreachable.add(.5,0,0),101),"Nearby failed destination is also deferred");
            check(BotMovement.remainingPaths()==budget,"Deferred chase consumes no path search");
            var rejected=BotMovement.class.getDeclaredMethod("rejected",net.minecraft.world.phys.Vec3.class,net.minecraft.world.phys.Vec3.class,int.class);rejected.setAccessible(true);
            check(!(boolean)rejected.invoke(failedMovement,from.add(9,0,0),unreachable,101),"Different approach can retry immediately");
            check(!(boolean)rejected.invoke(failedMovement,from,unreachable,300),"Failed route expires after ten seconds");
            failedMovement.reset();BotMovement.newTick();
            for(int i=0;i<BotMovement.PATHS_PER_TICK;i++)BotMovement.path(b,BlockPos.containing(from),0);
            check(!failedMovement.travel(b,arena,agent.team,unreachable,400),"Budget exhaustion defers a chase");
            check(!(boolean)rejected.invoke(failedMovement,from,unreachable,401),"Budget exhaustion does not blacklist the route");
            BotMovement.newTick();b.getNavigation().stop();
            System.out.println("BOT_DAMAGE_AND_ROUTE_RETRY_PASS");
            meleeMovementRegressions(b,from);
            perceptionRegressions(b,from);
            hazardBenchmark(b,from);
            level.setBlock(roof,Blocks.STONE.defaultBlockState(),3);
            check(BotMovement.ground(b,arena,from).equals(BlockPos.containing(from)),"Same-floor candidate wins over roof");
            check(BotMovement.ground(b,arena,from.add(0,4,0)).equals(roof.above()),"Explicit upper-floor goal stays upstairs");
            level.setBlock(roof,roofBefore,3);
            hazards.put(hazardId,new BotPerception.Hazard(hazardId,from.add(0,0,1.5),.6,false));
            check(!BotMovement.safeSegment(b,arena,from,from.add(0,0,3)),"Known trap blocks short strafe/peek segment");
            hazards.remove(hazardId);

            var availability=new BotHealth();BotMovement.newTick();
            check(!availability.available(b,100,8),"No nearby health pack means healing is not offered");
            level.setBlock(packPos,StationBlocks.LARGE.get().defaultBlockState(),3);b.setHealth(25);
            check(availability.available(b,140,8)&&BotMovement.remainingPaths()==BotMovement.PATHS_PER_TICK,"Pack availability refreshes without path searches");
            level.removeBlock(packPos,false);
            check(!availability.available(b,141,8),"Removed pack is not offered from cached availability");
            level.setBlock(packPos,StationBlocks.LARGE.get().defaultBlockState(),3);
            var actions=new BotActions();var ctx=actions.ctx();var health=new BotHealth();
            ctx.bot=b;ctx.movement=new BotMovement();ctx.health=health;ctx.settings=BotManager.settings();ctx.now=100;
            BotMovement.newTick();
            for(int i=0;i<BotMovement.PATHS_PER_TICK;i++)BotMovement.path(b,BlockPos.containing(from),0);
            check(BotMovement.spent(),"Exhaust shared path budget");
            check(actions.run(BotTactics.Action.HEAL)==BotTree.Status.RUNNING,"Budget exhaustion defers healing rather than failing");
            BotMovement.newTick();ctx.now=101;
            var resumed=actions.run(BotTactics.Action.HEAL);
            check(resumed==BotTree.Status.RUNNING&&health.seeking(),"Deferred healing resumes next tick: status="+resumed+" pos="+b.position()+" next="+field(BotHealth.class,"nextSearch").get(health)+" rejected="+field(BotHealth.class,"retryAfter").get(health));
            var path=b.getNavigation().getPath();int remaining=BotMovement.remainingPaths();ctx.now=102;
            check(actions.run(BotTactics.Action.HEAL)==BotTree.Status.RUNNING,"Healing remains active");
            check(path==b.getNavigation().getPath()&&remaining==BotMovement.remainingPaths(),"Healing keeps existing path without recomputation");

            // Same-team player blocks actual shot direction, even if the intended enemy is elsewhere.
            field(Class.forName(LobbyManager.class.getName()+"$Entry"),"team").set(
                    ((Map<?,?>)field(LobbyManager.class,"PLAYERS").get(null)).get(player.getUUID()),"BRAVO");
            b.setPos(from);player.setPos(from.add(0,0,4));
            check(!BotShot.clear(b,0,0,12,BotWeaponData.UNKNOWN),"Actual firing ray rejects ally");
            check(BotShot.clear(b,-90,0,5,BotWeaponData.UNKNOWN),"Ally outside actual firing ray does not block");
            if(net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
                var data=BotWeaponData.read(b.getMainHandItem());
                check(data!=BotWeaponData.UNKNOWN&&data.speed()>0&&data.damage()>0&&data.rpm()>0,"TaCZ metadata resolves speed and combat stats");
                System.out.println("BOT_WEAPON_METADATA_PASS speed="+data.speed()+" damage="+data.damage()+" rpm="+data.rpm());
            }
            System.out.println("BOT_COMPOUND_REGRESSION_PASS floors hazards healing_budget healing_path firing_direction");
        }finally {
            hazards.remove(hazardId);level.setBlock(roof,roofBefore,3);level.setBlock(packPos,packBefore,3);
            field(Class.forName(LobbyManager.class.getName()+"$Entry"),"team").set(
                    ((Map<?,?>)field(LobbyManager.class,"PLAYERS").get(null)).get(player.getUUID()),"ALPHA");
            player.setPos(playerPos);b.setHealth(hp);b.setOnGround(onGround);b.getNavigation().stop();BotMovement.newTick();
        }
    }
    private static void meleeMovementRegressions(BotEntity b,net.minecraft.world.phys.Vec3 from)throws Exception {
        var original=player.position();var actions=new BotActions();var ctx=actions.ctx();
        ctx.bot=b;ctx.arena=arena;ctx.team=BotManager.agent(b).team;ctx.target=player;
        ctx.movement=new BotMovement();ctx.melee=true;ctx.pathTurn=true;ctx.now=100;
        try {
            player.setPos(from.add(-6,0,0));BotMovement.newTick();
            for(int i=0;i<BotMovement.PATHS_PER_TICK;i++)BotMovement.path(b,BlockPos.containing(from),0);
            actions.run(BotTactics.Action.ENGAGE);
            check(BotMovement.remainingPaths()==0&&b.getNavigation().isDone(),"Melee pursuit waits when shared path budget is exhausted");
            BotMovement.newTick();ctx.now=101;actions.run(BotTactics.Action.ENGAGE);
            check(BotMovement.remainingPaths()<BotMovement.PATHS_PER_TICK&&!b.getNavigation().isDone(),"Melee pursuit resumes using the shared path budget");
            player.setPos(from.add(-1,0,0));ctx.now=102;BotMovement.newTick();actions.run(BotTactics.Action.ENGAGE);
            check(b.getNavigation().isDone()&&ctx.movement.goal()==null&&BotMovement.remainingPaths()==BotMovement.PATHS_PER_TICK,"Melee arrival stops without selecting a patrol route");
            player.setPos(from.add(0,30,0));ctx.now=103;BotMovement.newTick();actions.run(BotTactics.Action.PUSH);
            check(b.getNavigation().isDone(),"Unreachable melee target abandons its path");
            ctx.now=104;BotMovement.newTick();actions.run(BotTactics.Action.PUSH);
            check(BotMovement.remainingPaths()==BotMovement.PATHS_PER_TICK,"Melee pursuit does not retry a recently failed destination");
            player.setPos(from.add(-6,0,0));ctx.now=105;BotMovement.newTick();actions.run(BotTactics.Action.PUSH);
            check(!b.getNavigation().isDone(),"Melee pursuit resumes when target leaves the failed location");
            System.out.println("BOT_MELEE_PATH_BUDGET_PASS");
        }finally {player.setPos(original);ctx.movement.halt(b);BotMovement.newTick();}
    }
    private static void perceptionRegressions(BotEntity b,net.minecraft.world.phys.Vec3 from)throws Exception {
        var perception=new BotPerception();var original=player.position();var level=server.overworld();
        var near=GadgetItems.ORDNANCE.get().create(level);var far=GadgetItems.ORDNANCE.get().create(level);
        try {
            player.setPos(from.add(12,0,0));
            perception.allyHit(b,b,player,100);
            var contact=perception.contact(player.getUUID());var reported=contact.position;
            check(perception.allyThreat().equals(reported)&&contact.cue==BotPerception.Cue.ALLY,"Support uses the uncertain contact report");
            check(Math.abs(reported.x-player.getX())<=3&&Math.abs(reported.z-player.getZ())<=3,"Callout error remains bounded");
            player.setPos(from.add(18,0,0));
            check(perception.allyThreat().equals(reported),"Callout does not follow unseen enemy movement");
            contact.visible=true;contact.cue=BotPerception.Cue.SEEN;contact.position=from.add(10,0,0);contact.updated=100;
            perception.allyHit(b,b,player,101);
            check(perception.allyThreat().equals(contact.position)&&contact.cue==BotPerception.Cue.SEEN,"Fresher sighting wins over a callout");
            perception.reset();
            var scan=BotPerception.class.getDeclaredMethod("hazards",BotEntity.class,String.class,int.class);scan.setAccessible(true);
            scan.invoke(perception,b,"BRAVO",100);
            near.setPos(from.add(2,0,0));near.setup(null,true);level.addFreshEntity(near);
            // Beyond even the largest supported immediate blast radius, but inside the wide scan.
            far.setPos(from.add(80,0,0));trackTestChunk(level,far.blockPosition());far.setup(null,true);level.addFreshEntity(far);
            scan.invoke(perception,b,"BRAVO",105);
            check(perception.hazardCount()==1&&perception.danger(from)!=null,"Nearby strike is discovered between wide scans");
            scan.invoke(perception,b,"BRAVO",120);
            check(perception.hazardCount()==2,"Distant strike is discovered on the next wide scan: count="+perception.hazardCount()+" bot="+b.position()+" far="+far.position()+" loaded="+(level.getEntity(far.getUUID())!=null));
            near.discard();far.discard();scan.invoke(perception,b,"BRAVO",125);
            check(perception.hazardCount()==0,"Removed hazards are forgotten between wide scans");
            perception.reset();scan.invoke(perception,b,"BRAVO",126);
            check(field(BotPerception.class,"nextWideHazardScan").getInt(perception)==146,"Reset immediately permits a fresh wide scan");
            var savedOrbital=ExtraGadgetConfig.orbital;var large=GadgetItems.ORDNANCE.get().create(level);
            try {
                ExtraGadgetConfig.orbital=new ExtraGadgetConfig.Orbital(64,savedOrbital.centerDamage(),savedOrbital.edgeDamage(),savedOrbital.delaySeconds(),savedOrbital.range(),savedOrbital.cooldownSeconds());
                large.setPos(from.add(60,0,0));trackTestChunk(level,large.blockPosition());large.setup(null,true);level.addFreshEntity(large);
                scan.invoke(perception,b,"BRAVO",130);
                check(perception.danger(from)!=null,"Maximum-radius strike covering the bot is discovered without waiting for a wide scan");
            }finally {ExtraGadgetConfig.orbital=savedOrbital;if(large!=null)large.discard();}
            System.out.println("BOT_CALLOUT_AND_HAZARD_CADENCE_PASS");
        }finally {player.setPos(original);if(near!=null)near.discard();if(far!=null)far.discard();}
    }
    /** Setup executes before the first chunk tick; loading terrain alone does not expose entities to queries. */
    @SuppressWarnings("unchecked") private static void trackTestChunk(net.minecraft.server.level.ServerLevel level,BlockPos pos)throws Exception {
        level.getChunk(pos);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<Entity>)field(net.minecraft.server.level.ServerLevel.class,"entityManager").get(level);
        manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(pos),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
    }
    /** Synthetic, warmed comparison; reports scan cost, not total server tick time. */
    private static void hazardBenchmark(BotEntity bot,net.minecraft.world.phys.Vec3 origin)throws Exception {
        var level=server.overworld();var fixtures=new java.util.ArrayList<OrdnanceEntity>();
        var scan=BotPerception.class.getDeclaredMethod("hazards",BotEntity.class,String.class,int.class);scan.setAccessible(true);
        var report=new StringBuilder("# Bot hazard scan benchmark\n\n")
                .append("Synthetic stationary scene, four independent perception states at one location. Seven paired samples after warmup; 200 decisions per state per sample.\n")
                .append("Baseline forces a wide scan on every decision; optimized uses one wide scan per four decisions (thinkTicks=5). Includes reflection and hazard maintenance, excludes entity ticks, AI movement and networking. Median microseconds per bot decision; not a TPS benchmark.\n\n")
                .append("| Hazard entities | Always wide (us) | Scheduled (us) | Reduction |\n| --- | ---: | ---: | ---: |\n");
        try {
            for(int count:new int[]{0,64,256}) {
                while(fixtures.size()<count) {
                    int i=fixtures.size();double angle=i*2.399963229728653;
                    double radius=i<8?8:50+i%30;
                    var entity=GadgetItems.ORDNANCE.get().create(level);
                    entity.setPos(origin.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
                    level.getChunk(entity.blockPosition());entity.setup(null,true);level.addFreshEntity(entity);fixtures.add(entity);
                }
                // Warm both paths, then alternate measurement order to reduce order bias.
                hazardSample(scan,bot,20);hazardSample(scan,bot,5);
                double[] baseline=new double[7],scheduled=new double[7];
                for(int round=0;round<7;round++) {
                    if(round%2==0){baseline[round]=hazardSample(scan,bot,20);scheduled[round]=hazardSample(scan,bot,5);}
                    else {scheduled[round]=hazardSample(scan,bot,5);baseline[round]=hazardSample(scan,bot,20);}
                }
                java.util.Arrays.sort(baseline);java.util.Arrays.sort(scheduled);
                report.append(String.format(java.util.Locale.ROOT,"| %d | %.3f | %.3f | %.1f%% |%n",count,baseline[3],scheduled[3],100*(1-scheduled[3]/baseline[3])));
            }
            java.nio.file.Files.writeString(java.nio.file.Path.of("hazard-benchmark.md"),report);
            System.out.println("BOT_HAZARD_BENCHMARK_PASS\n"+report);
        }finally {for(var entity:fixtures)entity.discard();}
    }
    private static double hazardSample(java.lang.reflect.Method scan,BotEntity bot,int step)throws Exception {
        var states=new BotPerception[]{new BotPerception(),new BotPerception(),new BotPerception(),new BotPerception()};
        long start=System.nanoTime();
        for(int i=0;i<200;i++)for(var state:states)scan.invoke(state,bot,"BRAVO",i*step);
        return (System.nanoTime()-start)/800.0/1000;
    }
    private static void coverCycle(BotEntity b,BotMovement movement,net.minecraft.world.phys.Vec3 cover,
            net.minecraft.world.phys.Vec3 enemy,net.minecraft.world.phys.Vec3 peek)throws Exception {
        b.getRandom().setSeed(0); // Reproducible sampled cover candidates, independent of earlier navigation tests.
        BotMovement.newTick();
        var saved=BotWaypoints.get(arena.id());var playerPos=player.position();float hp=b.getHealth();
        try {
            BotWaypoints.save(arena.id(),new BotWaypoints.Data(java.util.List.of(new BotWaypoints.Point(BotWaypoints.Type.COVER,cover.x,cover.y,cover.z,"BRAVO"))));
            player.setPos(enemy);
            var perception=new BotPerception();perception.hit(b,player,0,0);
            var contact=perception.contact(player.getUUID());contact.position=enemy;
            var actions=new BotActions();var ctx=actions.ctx();
            ctx.bot=b;ctx.arena=arena;ctx.team="BRAVO";ctx.settings=BotManager.settings();ctx.pathTurn=true;
            ctx.perception=perception;ctx.movement=movement;ctx.aim=new BotAim();ctx.aim.reset(b);ctx.ammo=.25f;
            ctx.now=31;contact.updated=31;
            check(actions.run(BotTactics.Action.COVER)==BotTree.Status.RUNNING&&ctx.reloadRequested&&!ctx.allowFire,"Hidden bot requests tactical reload before peeking");
            ctx.now=40;ctx.reloading=true;ctx.reloadRequested=false;ctx.ammo=1;
            actions.run(BotTactics.Action.COVER);check(!ctx.allowFire,"Reloading bot stays hidden");
            ctx.now=55;ctx.reloading=false;actions.run(BotTactics.Action.COVER);
            b.setPos(peek);ctx.target=player;ctx.now=56;
            actions.run(BotTactics.Action.COVER);check(ctx.allowFire,"Bot can fire only after reaching peek position");
            b.setHealth(hp-1);ctx.now=57;
            actions.run(BotTactics.Action.COVER);check(!ctx.allowFire,"Taking damage interrupts exposure and returns to cover");
            b.setPos(cover);ctx.target=null;ctx.now=58;
            check(actions.run(BotTactics.Action.COVER)==BotTree.Status.RUNNING&&!ctx.allowFire,"Returned bot waits hidden before another burst");
            System.out.println("BOT_COVER_CYCLE_PASS");
        }finally {BotWaypoints.save(arena.id(),saved);player.setPos(playerPos);b.setHealth(hp);movement.halt(b);}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(!active||event.phase!=TickEvent.Phase.END)return;
        try{
            tick++;player.doTick();
            if(tick>560&&tick<960&&bot()!=null) {
                if(gateBorn<0)gateBorn=tick;
                if(gateLeft<0&&bot().getX()<16)gateLeft=tick;
            }
            if(tick>1700&&bot()!=null&&bot().onGround()&&parkourLanding!=null
                    &&bot().getX()>=parkourLanding.x-.4&&bot().getY()>=parkourLanding.y)parkourLanded=true;
            if(bot()!=null){
                moved|=bot().position().distanceToSqr(new net.minecraft.world.phys.Vec3(20.5,101,.5))>1;
            }
            if(tick==35){check(!sawShot,"Never shoot unseen player through wall; bot="+bot().position()+" player="+player.position()+" visible="+bot().hasLineOfSight(player));wall(false);}
            if(tick==180&&!net.minecraftforge.fml.ModList.get().isLoaded("tacz"))player.setPos(bot().getX()+2,bot().getY(),bot().getZ());
            if(tick==220){
                command("status");System.out.println("BOT_SMOKE_STATE bot="+(bot()==null?"null":bot().position())+" human="+player.position()+" mode="+player.gameMode.getGameModeForPlayer()+" team="+LobbyManager.team(player.getUUID())+" hp="+player.getHealth());
                check(bot()!=null,"Bot remains alive");check(sawShot,"Real projectile fired");check(moved,"Navigation moved bot");
                check(player.getHealth()<1000,"Projectile hit real player damage pipeline");
                if(net.minecraftforge.fml.ModList.get().isLoaded("tacz")){
                    check(secondaryShot,"Empty primary switches to secondary and fires real projectile");
                    var api=Class.forName("com.tacz.guns.api.item.IGun");var stack=bot().getMainHandItem();var gun=api.getMethod("getIGunOrNull",net.minecraft.world.item.ItemStack.class).invoke(null,stack);
                    int reserve=(Integer)api.getMethod("getDummyAmmoAmount",net.minecraft.world.item.ItemStack.class).invoke(gun,stack);
                    check(reserve<10000,"TaCZ reloaded from reserve after one-round starting magazine");
                }else check(secondaryMelee,"Nearby enemy takes real secondary sword damage");
                // Same-team attacks must be blocked without corrupting team kill scores.
                var entry=((Map<?,?>)field(LobbyManager.class,"PLAYERS").get(null)).get(player.getUUID());field(entry.getClass(),"team").set(entry,"BRAVO");
                float hp=bot().getHealth();bot().hurt(server.overworld().damageSources().playerAttack(player),999);check(bot().getHealth()==hp,"Friendly fire blocked");
                field(entry.getClass(),"team").set(entry,"ALPHA");
                bot().invulnerableTime=0;bot().hurt(server.overworld().damageSources().playerAttack(player),10000);
                check(bot()==null&&ledger().kills("ALPHA")==1,"Bot death credits human and despawns");
            }
            if(tick==330){
                check(bot()!=null&&bot().getHealth()==bot().getMaxHealth(),"Bot respawns with full health");
                check(((Loadouts.Weapon)weapon()).id().equals(net.minecraftforge.fml.ModList.get().isLoaded("tacz")?"bot_smoke":"bow"),"Explicit primary stays fixed after death");
                check(((Loadouts.Weapon)secondary()).id().equals(net.minecraftforge.fml.ModList.get().isLoaded("tacz")?"bot_sidearm":"bot_sword"),"Explicit secondary stays fixed after death");
                check(ledger().rows().stream().filter(r->r.id().equals(BotManager.ID.toString())).count()==1,"Respawn never duplicates score row");
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                healPos=new BlockPos(24,100,0);
                server.overworld().setBlock(healPos,StationBlocks.LARGE.get().defaultBlockState(),3);
                bot().setHealth(25);
                check(bot().getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()>.23,"Movement multiplier applied");
            }
            // A seen enemy mine becomes ground the bot paths around (placed away from the health pack it is heading to).
            if(tick==335){
                mine=GadgetItems.DEPLOYABLE.get().create(server.overworld());
                mine.setPos(Math.floor(bot().getX())-3.5,101,Math.floor(bot().getZ())+.5);mine.setup(player,false,false);server.overworld().addFreshEntity(mine);
            }
            if(tick==345){
                check(BotManager.avoid(bot(),net.minecraft.util.Mth.floor(mine.getX()),101,net.minecraft.util.Mth.floor(mine.getZ())),"Seen enemy mine is avoided by pathfinding");
                mine.discard();
            }
            if(tick==450){
                check(bot()!=null&&bot().getHealth()>25,"Wounded bot navigates to and consumes map health pack");
                var pack=(StationEntity)server.overworld().getBlockEntity(healPos);
                check(pack.remaining()>0,"Bot pickup starts shared health pack cooldown");
                float hp=bot().getHealth();pack.interact(bot());check(bot().getHealth()==hp,"Cooling pack cannot heal bot again");
                player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
                player.invulnerableTime=0;player.hurt(server.overworld().damageSources().mobAttack(bot()),10000);
                check(ledger().kills("BRAVO")==1,"Bot kill credits team score");check(LobbyManager.respawning(player.getUUID()),"Human enters normal respawn");
                var before=bot();command("off");check(bot()==null&&before.isRemoved(),"Off removes bot immediately");
                check(!BotManager.added(),"Off also clears lobby reservation");
                command("on");check(bot()==null,"On only enables lobby usage after off");
                action("botAdd","","");check(bot()==null&&!BotManager.added(),"Lobby add blocked during match");
                command("status");LobbyManager.finish("DRAW");check(bot()==null,"Match end cleans bot");
                var saved=new BotDatabase(ServerFiles.root().resolve("bot-db.json")).profile();
                check(saved.statistics().matches()>0&&saved.statistics().deaths()>0,"Actual match saved bot career");
                check(saved.statistics().experience()>0&&BotManager.experience(BotManager.ID)==saved.statistics().experience(),"Bot earns XP for its level like a player");
                action("botAdd","","");check(BotManager.added(),"Bot can be re-added in returned lobby");
                MatchManager.prepare(server,ArenaMap.example(),null);check(bot()==null,"Non-TDM never spawns bot");MatchManager.end("ABORTED");
                // A fresh match uses auto slots and changes ineffective choices only during respawn.
                var spare=new Loadouts.Weapon("respawn_bow","{id:\"minecraft:bow\",Count:1b}","Respawn bow","",new Loadouts.Specs("","","","",""),"");
                java.nio.file.Files.writeString(ServerFiles.root().resolve("server/weapons/respawn_bow.json"),ServerFiles.GSON.toJson(spare));
                java.nio.file.Files.writeString(ServerFiles.root().resolve("server/sub_weapons/respawn_bow.json"),ServerFiles.GSON.toJson(spare));
                var secondBow=new Loadouts.Weapon("respawn_bow2","{id:\"minecraft:bow\",Count:1b}","Respawn bow 2","",new Loadouts.Specs("","","","",""),"");
                java.nio.file.Files.writeString(ServerFiles.root().resolve("server/sub_weapons/respawn_bow2.json"),ServerFiles.GSON.toJson(secondBow));
                var other=new Loadouts.Weapon("respawn_sword","{id:\"minecraft:iron_sword\",Count:1b}","Respawn sword","",new Loadouts.Specs("","","","",""),"");
                java.nio.file.Files.writeString(ServerFiles.root().resolve("server/sub_weapons/respawn_sword.json"),ServerFiles.GSON.toJson(other));
                command("weapon auto");command("secondary auto");
                player.setHealth(1000);player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                MatchManager.prepare(server,arena,new DeathmatchRules(120,100));
                field(LobbyManager.class,"activeMap").set(null,arena);field(LobbyManager.class,"phase").set(null,"RUNNING");MatchManager.start();
                autoPrimary=((Loadouts.Weapon)weapon()).id();
                autoSecondary=((Loadouts.Weapon)secondary()).id();
                MatchManager.botDeath(BotManager.ID,player.getUUID());
                check(bot()==null,"Automatic choice waits for respawn");
            }
            if(tick==560){
                check(bot()!=null,"Auto-equipped bot respawns");
                check(!((Loadouts.Weapon)weapon()).id().equals(autoPrimary),"Failed life chooses alternative primary");
                check(!((Loadouts.Weapon)secondary()).id().equals(autoSecondary),"Failed life chooses alternative secondary");
                check(bot().getMainHandItem().is(net.minecraft.world.item.Items.BOW)||BotGun.supports(bot().getMainHandItem()),"Respawn equips supported primary");
                // Map hazards act on the bot like on players: a gas barrel blast hurts it, acid poisons it.
                var barrel=bot().blockPosition().offset(2,0,0);
                server.overworld().setBlock(barrel,GimmickBlocks.GAS.get().defaultBlockState(),3);
                float before=bot().getHealth();bot().invulnerableTime=0;GimmickEntity.hit(server.overworld(),barrel,10000);
                check(bot().getHealth()<before,"Gas barrel explosion damages the bot");
                server.overworld().removeBlock(barrel,false);server.overworld().removeBlock(barrel.above(),false);
                AcidBlock.poison(server.overworld(),bot());check(bot().hasEffect(net.minecraft.world.effect.MobEffects.POISON),"Acid poisons the bot");
                // Spawn room: closed stone box around the BRAVO spawn whose only opening is a BRAVO gate in the west wall.
                command("set pathTicks 200");
                MatchManager.botDeath(BotManager.ID,player.getUUID());
                var level=server.overworld();
                for(int x=16;x<=25;x++)for(int z=-4;z<=4;z++)for(int y=101;y<=104;y++){
                    boolean shell=x==16||x==25||z==-4||z==4||y==104;
                    level.setBlock(new BlockPos(x,y,z),shell?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                }
                for(int y=101;y<=102;y++)level.setBlock(new BlockPos(16,y,0),StationBlocks.BRAVO_GATE.get().defaultBlockState(),3);
            }
            if(tick==760){
                check(bot()!=null,"Bot respawns inside a gated spawn room");
                check(gateLeft>=gateBorn&&gateBorn>=0&&gateLeft-gateBorn<=60,"Spawn exit completes within 60 ticks despite a 200-tick patrol interval: "+gateBorn+" -> "+gateLeft+" position="+bot().position()+" goal="+BotManager.agent(bot()).movement.goal());
                System.out.println("BOT_FAST_SPAWN_EXIT_PASS ticks="+(gateLeft-gateBorn));
                // Also check what happens AFTER crossing the gate, with no visible enemy to hold the bot there.
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                bot().removeAllEffects();bot().setHealth(bot().getMaxHealth());
                var agent=BotManager.agent(bot());agent.target=null;agent.perception.reset();agent.actions.reset();agent.tactics.reset(server.getTickCount());
                agent.nextThink=agent.nextPath=server.getTickCount();
            }
            if(tick==960){
                System.out.println("BOT_SMOKE_GATE bot="+(bot()==null?"null":bot().position()));
                check(bot()!=null&&bot().getX()<16,"Bot finds its own spawn gate and leaves the spawn room");
                check(!BotMovement.nearSpawn(arena,"BRAVO",bot().position()),"Idle bot continues away from the gate instead of circling spawn: "+bot().position());
                System.out.println("BOT_POST_EXIT_PATROL_PASS");
                MatchManager.end("ABORTED");
                // Two bots on opposite sides: they must find and fight each other with no player involved.
                command("set pathTicks 40");
                var level=server.overworld();
                for(int x=16;x<=25;x++)for(int z=-4;z<=4;z++)for(int y=101;y<=104;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                field(LobbyManager.class,"phase").set(null,"LOBBY");
                BotManager.clearLobby();var loadouts=Loadouts.load();for(int i=0;i<BotManager.MAX;i++)BotManager.lobbyAdd(loadouts);
                for(int i=0;i<BotManager.MAX;i++)BotManager.lobbyTeam(BotDatabase.id(i),i%2==0?"ALPHA":"BRAVO");
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                MatchManager.prepare(server,arena,new DeathmatchRules(120,100));
                field(LobbyManager.class,"activeMap").set(null,arena);field(LobbyManager.class,"phase").set(null,"RUNNING");MatchManager.start();
                check(BotManager.agent(3)!=null&&BotManager.agent(3).bot!=null,"Four bots join one match");
                var thinks=new java.util.HashSet<Integer>();for(int i=0;i<BotManager.MAX;i++)thinks.add(BotManager.agent(i).nextThink%BotManager.settings().thinkTicks());
                check(thinks.size()>1,"Bots think on staggered ticks");
                check(!BotManager.name(0).equals(BotManager.name(1)),"Each bot has its own profile name");
            }
            if(tick==1400){
                double dealt=ledger().rows().stream().filter(r->BotManager.identity(java.util.UUID.fromString(r.id()))).mapToDouble(MatchSnapshot.Row::damage).sum();
                System.out.println("BOT_SMOKE_DUEL damage="+dealt+" maxAiMs="+field(BotManager.class,"maxWorkNanos").getLong(null)/1e6);
                check(dealt>0,"Opposing bots fight each other");
                MatchManager.end("ABORTED");
                // Far apart spawns, far from both the player and world spawn: local navigation must advance.
                var level=server.overworld();
                for(int x=4096;x<=4432;x++)for(int z=-8;z<=8;z++)level.setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),3);
                var json=ServerFiles.GSON.toJsonTree(arena).getAsJsonObject();
                json.add("alphaSpawn",ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(4104.5,101,.5,0,0)));
                json.add("bravoSpawn",ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(4424.5,101,.5,180,0)));
                json.add("alphaArea",ServerFiles.GSON.toJsonTree(new ArenaMap.Area(4103,100,-1,4106,103,2)));
                json.add("bravoArea",ServerFiles.GSON.toJsonTree(new ArenaMap.Area(4423,100,-1,4426,103,2)));
                arena=ServerFiles.GSON.fromJson(json,ArenaMap.class);
                MatchManager.prepare(server,arena,new DeathmatchRules(120,100));
                field(LobbyManager.class,"activeMap").set(null,arena);field(LobbyManager.class,"phase").set(null,"RUNNING");MatchManager.start();
            }
            if(tick==1700){
                for(int i=0;i<BotManager.MAX;i++){
                    var remote=BotManager.agent(i).bot;
                    double startX=i%2==0?4104.5:4424.5;
                    check(remote!=null&&remote.tickCount>200,"Remote bot receives entity ticks");
                    check(Math.abs(remote.getX()-startX)>16,"Remote bot advances from widely separated spawn: "+remote.position());
                }
                System.out.println("BOT_REMOTE_SPAWN_PASS");
                parkourSetup(2,false);
            }
            if(tick==1720){
                check(parkourLanded,"Physics lands across a two-block gap");
                parkourSetup(1,true);
            }
            if(tick==1740){
                check(parkourLanded&&bot().getY()>=121,"Physics lands on a raised platform");
                parkourSetup(3,false);
            }
            if(tick==1760){
                check(parkourLanded,"Dash lands across a three-block gap");
                check(!bot().isSprinting(),"Landing restores sprint state");
                parkourSetup(3,true);
            }
            if(tick==1780){
                check(parkourLanded,"Dash lands across three-block gap on one-block raised platform");
                check(!bot().isSprinting(),"Raised landing restores sprint state");
                System.out.println("BOT_PARKOUR_PASS gaps raised_platform dash_three_block_gap dash_raised_platform ceiling oversized_jump");
                TeamPoints.refresh(server);TeamPoints.STATE.mark(player.getUUID(),"minecraft:overworld",1,101,1);
                MatchManager.end("ABORTED");check(TeamPoints.STATE.view("ALPHA",0).points().isEmpty(),"Match end clears points");
                TeamPoints.STATE.roster(Map.of(player.getUUID(),"ALPHA"));TeamPoints.STATE.mark(player.getUUID(),"minecraft:overworld",1,101,1);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gungang reset");
                check(TeamPoints.STATE.view("ALPHA",0).points().isEmpty(),"Gungang reset clears points");finish(null);
            }
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error){
        active=false;
        if(error!=null){error.printStackTrace();System.out.println("GUNGANG_BOT_SMOKE_FAIL");}
        else System.out.println("GUNGANG_BOT_SMOKE_PASS");
        BotManager.close();server.halt(false);
    }
}
