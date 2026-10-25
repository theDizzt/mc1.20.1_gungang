package dev.gungang.acoustics.server;

import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.GungangAcoustics;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID)
public final class MatchManager {
    private static MinecraftServer server;
    private static ArenaMap map;
    private static MatchLedger ledger;
    private static CaptureGame capture;
    private static NormandyGame normandy;
    private static DeathmatchRules deathmatch;
    private static FreeForAllMatch freeForAll;
    private static PropHuntMatch propHunt;
    /** The running prop hunt, or else the /gungang debug prop session, so every prop hook serves both. */
    public static PropHuntMatch propHunt(){return propHunt!=null?propHunt:PropHuntMatch.testMatch();}
    /** Only a real prop hunt match (no debug session). */
    public static PropHuntMatch realPropHunt(){return propHunt;}
    public static FreeForAllMatch freeForAll(){return freeForAll;}
    private static BombMatch bomb;
    private static EscortMatch escort;
    public static EscortMatch escort(){return escort;}
    public static boolean isEscortCart(EscortCart cart){return escort!=null&&escort.cart==cart;}
    public static BombMatch bomb(){return bomb;}
    /**
     * While a match prepares, the defending team that roams freely and may pass its own spawn gate
     * (escort BRAVO, Normandy ALPHA, bomb defenders); "" shuts every gate, null leaves gates as in play.
     */
    public static String preparationGate(){
        if(map==null||propHunt!=null||freeForAll!=null)return null;
        if(bomb!=null)return bomb.game.phase()==BombGame.Phase.PREPARING?bomb.game.defender():null;
        if(running)return null;
        return escort!=null?"BRAVO":normandy!=null?"ALPHA":"";
    }
    public static boolean active(){return map!=null;}
    public static void deathZone(ServerPlayer player){
        if(map==null||!java.util.Set.of("ALPHA","BRAVO").contains(LobbyManager.team(player.getUUID()))||player.isSpectator()||!player.isAlive())return;
        if(freeForAll!=null&&freeForAll.opening()){freeForAll.openingDeath(player);return;}
        if(propHunt!=null){propHunt.eliminate(player,null);return;}
        boolean died;
        if(bomb!=null){
            if(bomb.dead(player.getUUID())||bomb.game.phase()==BombGame.Phase.ROUND_END)return;
            bomb.eliminate(player);died=true;
            for(var p:server.getPlayerList().getPlayers())p.sendSystemMessage(net.minecraft.network.chat.Component.literal(player.getGameProfile().getName()+" 님이 데스존에서 자살했습니다."));
        }else died=LobbyManager.beginRespawn(player,null,true);
        if(died&&freeForAll!=null)freeForAll.death(player,null);
        if(died)ledger.death(player.getUUID(),player.getUUID(),server.getTickCount(),deathmatch!=null);
    }
    private static boolean contested;
    private static ZonedDateTime started;
    private static boolean running;
    private static int ticks;
    private static final CaptureFloor floor = new CaptureFloor();
    private static final CapturePresence presence = new CapturePresence();
    public static void prepare(MinecraftServer s, ArenaMap arena, DeathmatchRules teamDeathmatch) {
        TeamPoints.clear(s);
        PropHuntMatch.endTest();
        BombDrops.clear(s);
        var escortRoute=arena.escort()==null?null:EscortMatch.validate(s,arena);
        // Before EscortMatch records its own marker flags, so its close restores to the hidden state first.
        EscortTracks.hideMarkers(s);
        // Every mode starts with escort spawn gates off; only an EscortMatch turns its stage on.
        EscortSpawnGateBlock.deactivate(s);
        GimmickEpoch.reset(s);
        MapEnvironment.begin(s,arena);
        deathmatch=teamDeathmatch;
        if(arena.propHunt()!=null)try {propHunt=new PropHuntMatch(s,arena,PropHuntMatch.validate(s,arena,Loadouts.load()));}catch(java.io.IOException e){throw new IllegalStateException(e);}
        freeForAll=arena.freeForAll()==null?null:new FreeForAllMatch(s,arena);
        if(freeForAll!=null)for(var p:s.getPlayerList().getPlayers())if(java.util.Set.of("ALPHA","BRAVO").contains(LobbyManager.team(p.getUUID())))freeForAll.add(p);
        server=s; map=arena; ledger=new MatchLedger(); capture=new CaptureGame(); ticks=0; running=false;
        normandy=arena.normandy()==null?null:new NormandyGame(arena.normandy());
        bomb=arena.bomb()==null?null:new BombMatch(s,arena);
        escort=escortRoute==null?null:new EscortMatch(s,arena,escortRoute);
        presence.clear(); contested=false;
        started=ZonedDateTime.now();
        for (var p:s.getPlayerList().getPlayers()) ledger.add(p.getUUID(),p.getGameProfile().getName(),freeForAll!=null&&freeForAll.participant(p.getUUID())?p.getUUID().toString():LobbyManager.team(p.getUUID()));
        if(deathmatch!=null)BotManager.prepare(s,arena);else BotManager.close();
        floor.prepare(s,arena);
        NormandyBeacon.get(s).activate(s,arena,0);
        send(snapshot(false,"",LobbyManager.preparationSeconds(arena)));
        LogUtils.getLogger().info("[GunGang] MATCH PREPARE date={} map={} ({}) dimension={} timeLimitSeconds={}",started,map.name(),map.id(),map.dimension(),map.timeLimitSeconds());
    }
    public static void start() { TeamPoints.clear(server);if(freeForAll!=null)freeForAll.start(); BombDrops.clear(server);GimmickEpoch.reset(server);if(!running)started=ZonedDateTime.now();running=true; LogUtils.getLogger().info("[GunGang] MATCH START date={} map={} ({})",started,map.name(),map.id()); }
    public static void tick(int preparationSeconds) {
        if(map==null)return;
        if(deathmatch!=null){BotManager.tick(running);if(map==null)return;if(running)for(var id:BotManager.activeIds())ledger.played(id);}
        if(propHunt!=null){if(running){if(propHunt.game.phase()==PropHuntGame.Phase.HIDING||propHunt.game.phase()==PropHuntGame.Phase.HUNTING)ticks++;propHunt.tick();}if(map!=null&&server.getTickCount()%4==0)send(snapshot(false,"",preparationSeconds));return;}
        if(escort!=null){
            if(running){ticks++;for(var p:server.getPlayerList().getPlayers())if(!LobbyManager.team(p.getUUID()).equals("SPECTATOR"))ledger.played(p.getUUID());}
            escort.tick(running);
            if(map!=null&&server.getTickCount()%4==0)send(snapshot(false,"",preparationSeconds));
            return;
        }
        if(bomb!=null){
            if(running){ticks++;for(var p:server.getPlayerList().getPlayers())if(!LobbyManager.team(p.getUUID()).equals("SPECTATOR"))ledger.played(p.getUUID());}
            bomb.tick();
            if(map!=null&&server.getTickCount()%4==0)send(snapshot(false,"",preparationSeconds));
            return;
        }
        if(running) {
            ticks++;
            if(freeForAll!=null){freeForAll.tick();String winner=freeForAll.winner(ticks>=map.timeLimitSeconds()*20);if(!winner.isEmpty()){LobbyManager.finish(winner);return;}}
            if(deathmatch!=null) {
                String winner=deathmatch.winner(alphaScore(),bravoScore(),ticks>=map.timeLimitSeconds()*20);
                if(!winner.isEmpty()) {LobbyManager.finish(winner);return;}
            }
            for(var p:server.getPlayerList().getPlayers()) if(!LobbyManager.team(p.getUUID()).equals("SPECTATOR")) ledger.played(p.getUUID());
            var c=map.capture();
            if(normandy!=null) {
                boolean attack=false,defend=false;
                var point=normandy.point(); var center=point.center();
                for(var p:server.getPlayerList().getPlayers()) if(p.isAlive()&&!p.isSpectator()
                        &&p.level().dimension().location().toString().equals(map.dimension())
                        &&p.distanceToSqr(center.x(),center.y(),center.z())<=point.radius()*point.radius()) {
                    attack |= LobbyManager.team(p.getUUID()).equals("BRAVO");
                    defend |= LobbyManager.team(p.getUUID()).equals("ALPHA");
                }
                contested=attack&&defend;
                if(normandy.tick(attack,defend)) {
                    for(var p:server.getPlayerList().getPlayers()) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            point.name()+" 거점 점령 완료"+(normandy.complete()?"":" · 다음 거점: "+normandy.point().name())));
                    if(normandy.complete()) { LobbyManager.finish("BRAVO"); return; }
                    NormandyBeacon.get(server).activate(server,map,normandy.stage());
                }
                if(ticks%20==0) markObjective();
            }
            if(c!=null) {
                var a=c.area(); var box=new AABB(a.minX(),a.minY(),a.minZ(),a.maxX(),a.maxY(),a.maxZ());
                boolean alpha=false,bravo=false;
                Set<UUID> eligible=new HashSet<>();
                for(var p:server.getPlayerList().getPlayers()) if(p.isAlive()&&!p.isSpectator()&&p.level().dimension().location().toString().equals(map.dimension())) {
                    String team=LobbyManager.team(p.getUUID());
                    if(!team.equals("ALPHA")&&!team.equals("BRAVO"))continue;
                    eligible.add(p.getUUID());
                    if(presence.update(p.getUUID(),p.getBoundingBox(),box)) {
                        alpha |= team.equals("ALPHA"); bravo |= team.equals("BRAVO");
                    }
                }
                presence.retain(eligible);
                capture.tick(alpha,bravo,c.captureStepTicks(),c.scoreIntervalTicks());
                contested=alpha&&bravo;
                floor.color(capture.scoringOwner());
                String winner=capture.winner(c.winningScore());
                if(!winner.isEmpty()) { LobbyManager.finish(winner); return; }
            }
            if(map.timeLimitSeconds()>0&&ticks>=map.timeLimitSeconds()*20) { LobbyManager.finish(normandy==null?capture.leader():"ALPHA"); return; }
        }
        if(server.getTickCount()%4==0) send(snapshot(false,"",preparationSeconds));
    }
    private static MatchSnapshot snapshot(boolean result,String winner,int preparation) {
        int seconds=result?ticks/20:!running?preparation:map.timeLimitSeconds()==0?ticks/20:Math.max(0,map.timeLimitSeconds()-(ticks/20));
        var point=map.capture()==null?null:new MatchSnapshot.Capture(map.capture().name(),capture.progress(),contested,capture.scoringOwner(),map.capture().winningScore());
        if(normandy!=null) point=new MatchSnapshot.Capture(normandy.point().name(),-normandy.percent(),contested,-1,4,"NORMANDY",normandy.stage());
        if(propHunt!=null){point=new MatchSnapshot.Capture("숨바꼭질",0,false,0,0,"PROP_HUNT",0);if(!result)seconds=(propHunt.game.remaining()+19)/20;}
        if(freeForAll!=null)point=new MatchSnapshot.Capture("",0,false,0,map.freeForAll().winningScore(),"FREE_FOR_ALL",0);
        if(deathmatch!=null) point=new MatchSnapshot.Capture("",0,false,0,deathmatch.winningKills(),"DEATHMATCH",0);
        if(escort!=null){point=new MatchSnapshot.Capture("호위",0,false,0,escort.game.checkpoints.size(),"ESCORT",escort.game.completed);if(!result&&running)seconds=(escort.game.remainingTicks+19)/20;}
        if(bomb!=null){point=new MatchSnapshot.Capture("",0,false,0,map.bomb().winsRequired(),"BOMB",bomb.game.round());if(!result)seconds=(bomb.game.remainingTicks()+19)/20;}
        return new MatchSnapshot(!result,result,map.name(),bomb!=null?bomb.game.phase().name():running?"RUNNING":"PREPARING",seconds,!running||map.timeLimitSeconds()>0,winner,alphaScore(),bravoScore(),propHunt!=null?propHunt.rows(ledger.rows()):freeForAll==null?ledger.rows():freeForAll.rows(ledger.rows()),point,LobbyManager.options(),LobbyManager.respawns(),ticks/20,map.weather(),map.dimension(),bomb==null?null:bomb.view(),map.timeOfDay(),escort==null?null:escort.view());
    }
    private static int alphaScore() { return escort!=null?escort.game.completed+(escort.game.winner.equals("ALPHA")?1:0):bomb!=null?bomb.game.alphaScore():deathmatch!=null?ledger.kills("ALPHA"):normandy==null?capture.alphaScore():4-normandy.stage(); }
    private static int bravoScore() { return escort!=null?(escort.game.winner.equals("BRAVO")?1:0):bomb!=null?bomb.game.bravoScore():deathmatch!=null?ledger.kills("BRAVO"):normandy==null?capture.bravoScore():normandy.stage(); }
    public static ArenaMap.Spawn attackSpawn() { return normandy==null?null:normandy.point().attackSpawn(); }
    private static void markObjective() {
        var p=normandy.point(); var c=p.center();
        var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(map.dimension())));
        if(level==null)return;
        for(int i=0;i<24;i++) {
            double angle=i*Math.PI/12;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,c.x()+Math.cos(angle)*p.radius(),c.y()+1.2,c.z()+Math.sin(angle)*p.radius(),1,0,0,0,0);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,c.x(),c.y()+2,c.z(),12,0.1,2,0.1,0);
    }
    private static void send(MatchSnapshot value) {
        if(value.bomb()==null){var packet=new GungangNetwork.Match(ServerFiles.GSON.toJson(value)); for(var p:server.getPlayerList().getPlayers())GungangNetwork.send(p,packet);return;}
        // Minimap positions are team-only, so each team (and everyone else) gets its own copy.
        var packets=new HashMap<String,GungangNetwork.Match>();
        for(var p:server.getPlayerList().getPlayers()){
            String team=LobbyManager.team(p.getUUID());if(!team.equals("ALPHA")&&!team.equals("BRAVO"))team="";
            GungangNetwork.send(p,packets.computeIfAbsent(team,t->new GungangNetwork.Match(ServerFiles.GSON.toJson(value.withBomb(value.bomb().forTeam(t))))));
        }
    }
    public static void end(String winner) {
        TeamPoints.clear(server);
        BombDrops.clear(server);
        if(map==null)return;
        MapEnvironment.restore();
        GimmickEpoch.reset(server);
        var result=snapshot(true,winner,0); var ended=ZonedDateTime.now();
        BotManager.close();
        BotManager.finish(result);
        ProgressionService.finish(server,result);
        LogUtils.getLogger().info("[GunGang] MATCH END date={} started={} map={} ({}) dimension={} winner={} score={}:{} playTime={}",ended,started,map.name(),map.id(),map.dimension(),winner,alphaScore(),bravoScore(),format(ticks/20));
        try {
            Path file=MatchReport.save(ServerFiles.root().resolve("result"),map,result,started,ended);
            LogUtils.getLogger().info("[GunGang] Result saved: {}",file.toAbsolutePath());
        } catch(Exception error) { LogUtils.getLogger().error("[GunGang] Could not save match result",error); }
        if(propHunt!=null)propHunt.close();propHunt=null;
        if(freeForAll!=null)freeForAll.close();freeForAll=null;
        floor.reset();NormandyBeacon.get(server).restore(server);presence.clear(); send(result);if(bomb!=null)bomb.close();bomb=null;if(escort!=null)escort.close();escort=null;EscortTracks.showMarkers(); map=null; running=false;
    }
    public static String format(int seconds) { return MatchReport.time(seconds); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event) {
        if(!running||map==null||event.isCanceled())return;
        var victim=event.getEntity();var attacker=DamageCredit.attacker(victim,event.getSource());
        if(BotManager.owns(attacker)&&(victim instanceof ServerPlayer||BotManager.owns(victim))&&ledger.enemies(attacker.getUUID(),victim.getUUID()))
            BotManager.damageDealt(attacker,Math.min(victim.getHealth(),event.getAmount()));
        if((victim instanceof ServerPlayer||BotManager.owns(victim))&&(attacker instanceof ServerPlayer||BotManager.owns(attacker)))
            ledger.damage(attacker.getUUID(),victim.getUUID(),Math.min(victim.getHealth(),event.getAmount()),server.getTickCount());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(!running||map==null||event.isCanceled())return;
        if(BotManager.owns(event.getEntity())){
            event.setCanceled(true);var killer=event.getSource().getEntity();
            botDeath(event.getEntity().getUUID(),killer==null?null:killer.getUUID());return;
        }
        if(event.getEntity() instanceof ServerPlayer victim) {
            if(freeForAll!=null&&!freeForAll.participant(victim.getUUID()))return;
            if(freeForAll!=null&&freeForAll.opening()){freeForAll.openingDeath(victim);event.setCanceled(true);return;}
            if(bomb!=null){
                if(!java.util.Set.of("ALPHA","BRAVO").contains(LobbyManager.team(victim.getUUID())))return;
                if(bomb.dead(victim.getUUID())||!bomb.activePlay()){victim.setHealth(victim.getMaxHealth());event.setCanceled(true);return;}
                var killer=event.getSource().getEntity() instanceof ServerPlayer p?p:null;
                ledger.death(victim.getUUID(),killer==null?null:killer.getUUID(),server.getTickCount(),false);
                var message=victim.getCombatTracker().getDeathMessage();bomb.eliminate(victim);event.setCanceled(true);
                for(var p:server.getPlayerList().getPlayers())p.sendSystemMessage(message);
                return;
            }
            if(LobbyManager.respawning(victim.getUUID())) {
                victim.setHealth(victim.getMaxHealth()); event.setCanceled(true); return;
            }
            var killer=event.getSource().getEntity() instanceof ServerPlayer player?player:null;
            var source=event.getSource().getEntity();
            var killerId=source!=null&&BotManager.identity(source.getUUID())?source.getUUID():killer==null?(freeForAll!=null?victim.getUUID():null):killer.getUUID();
            ledger.death(victim.getUUID(),killerId,server.getTickCount(),deathmatch!=null);
            if(freeForAll!=null)freeForAll.death(victim,killer);
            if(LobbyManager.beginRespawn(victim,killer)) event.setCanceled(true);
            if(freeForAll!=null){String winner=freeForAll.winner(false);if(!winner.isEmpty())LobbyManager.finish(winner);}
            if(deathmatch!=null) {
                String winner=deathmatch.winner(alphaScore(),bravoScore(),false);
                if(!winner.isEmpty())LobbyManager.finish(winner);
            }
        }
    }
    public static void participant(ServerPlayer player) {
        if (map!=null) ledger.add(player.getUUID(),player.getGameProfile().getName(),freeForAll!=null&&freeForAll.participant(player.getUUID())?player.getUUID().toString():LobbyManager.team(player.getUUID()));
    }
    static boolean botRunning(){return running&&map!=null&&deathmatch!=null;}
    static void botParticipant(UUID bot,String name,String team){if(map!=null&&deathmatch!=null)ledger.add(bot,name,team);}
    static void botDeath(UUID bot,UUID killer){
        if(!botRunning())return;
        ledger.death(bot,killer,server.getTickCount(),true);BotManager.died(bot,killer);
        String winner=deathmatch.winner(alphaScore(),bravoScore(),false);if(!winner.isEmpty())LobbyManager.finish(winner);
    }
    private MatchManager() {}
}
