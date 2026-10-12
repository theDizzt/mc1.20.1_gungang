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
    private static boolean contested;
    private static ZonedDateTime started;
    private static boolean running;
    private static int ticks;
    private static final CaptureFloor floor = new CaptureFloor();
    private static final CapturePresence presence = new CapturePresence();
    public static void prepare(MinecraftServer s, ArenaMap arena, DeathmatchRules teamDeathmatch) {
        deathmatch=teamDeathmatch;
        server=s; map=arena; ledger=new MatchLedger(); capture=new CaptureGame(); ticks=0; running=false;
        normandy=arena.normandy()==null?null:new NormandyGame(arena.normandy());
        presence.clear(); contested=false;
        started=ZonedDateTime.now();
        for (var p:s.getPlayerList().getPlayers()) ledger.add(p.getUUID(),p.getGameProfile().getName(),LobbyManager.team(p.getUUID()));
        floor.prepare(s,arena);
        NormandyBeacon.get(s).activate(s,arena,0);
        send(snapshot(false,"",LobbyManager.preparationSeconds(arena)));
        LogUtils.getLogger().info("[GunGang] MATCH PREPARE date={} map={} ({}) dimension={} timeLimitSeconds={}",started,map.name(),map.id(),map.dimension(),map.timeLimitSeconds());
    }
    public static void start() { running=true; started=ZonedDateTime.now(); LogUtils.getLogger().info("[GunGang] MATCH START date={} map={} ({})",started,map.name(),map.id()); }
    public static void tick(int preparationSeconds) {
        if(map==null)return;
        if(running) {
            ticks++;
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
        if(deathmatch!=null) point=new MatchSnapshot.Capture("",0,false,0,deathmatch.winningKills(),"DEATHMATCH",0);
        return new MatchSnapshot(!result,result,map.name(),running?"RUNNING":"PREPARING",seconds,!running||map.timeLimitSeconds()>0,winner,alphaScore(),bravoScore(),ledger.rows(),point,LobbyManager.options(),LobbyManager.respawns());
    }
    private static int alphaScore() { return deathmatch!=null?ledger.kills("ALPHA"):normandy==null?capture.alphaScore():4-normandy.stage(); }
    private static int bravoScore() { return deathmatch!=null?ledger.kills("BRAVO"):normandy==null?capture.bravoScore():normandy.stage(); }
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
    private static void send(MatchSnapshot value) { var packet=new GungangNetwork.Match(ServerFiles.GSON.toJson(value)); for(var p:server.getPlayerList().getPlayers())GungangNetwork.send(p,packet); }
    public static void end(String winner) {
        if(map==null)return;
        var result=snapshot(true,winner,0); var ended=ZonedDateTime.now();
        ProgressionService.finish(server,result);
        LogUtils.getLogger().info("[GunGang] MATCH END date={} started={} map={} ({}) dimension={} winner={} score={}:{} playTime={}",ended,started,map.name(),map.id(),map.dimension(),winner,alphaScore(),bravoScore(),format(ticks/20));
        try {
            Path file=MatchReport.save(ServerFiles.root().resolve("result"),map,result,started,ended);
            LogUtils.getLogger().info("[GunGang] Result saved: {}",file.toAbsolutePath());
        } catch(Exception error) { LogUtils.getLogger().error("[GunGang] Could not save match result",error); }
        floor.reset();NormandyBeacon.get(server).restore(server);presence.clear(); send(result); map=null; running=false;
    }
    public static String format(int seconds) { return MatchReport.time(seconds); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event) {
        if(!running||map==null||event.isCanceled())return;
        if(event.getEntity() instanceof ServerPlayer victim&&event.getSource().getEntity() instanceof ServerPlayer attacker)
            ledger.damage(attacker.getUUID(),victim.getUUID(),Math.min(victim.getHealth(),event.getAmount()),server.getTickCount());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(!running||map==null||event.isCanceled())return;
        if(event.getEntity() instanceof ServerPlayer victim) {
            if(LobbyManager.respawning(victim.getUUID())) {
                victim.setHealth(victim.getMaxHealth()); event.setCanceled(true); return;
            }
            var killer=event.getSource().getEntity() instanceof ServerPlayer player?player:null;
            ledger.death(victim.getUUID(),killer==null?null:killer.getUUID(),server.getTickCount(),deathmatch!=null);
            if(LobbyManager.beginRespawn(victim,killer)) event.setCanceled(true);
            if(deathmatch!=null) {
                String winner=deathmatch.winner(alphaScore(),bravoScore(),false);
                if(!winner.isEmpty())LobbyManager.finish(winner);
            }
        }
    }
    public static void participant(ServerPlayer player) {
        if (map!=null) ledger.add(player.getUUID(),player.getGameProfile().getName(),LobbyManager.team(player.getUUID()));
    }
    private MatchManager() {}
}
