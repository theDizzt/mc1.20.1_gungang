package dev.gungang.acoustics.server;

import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.GungangAcoustics;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.BossEvent;
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
    private static ServerBossEvent boss;
    private static ZonedDateTime started;
    private static boolean running;
    private static int ticks;
    private static final CaptureFloor floor = new CaptureFloor();
    private static final CapturePresence presence = new CapturePresence();
    public static void prepare(MinecraftServer s, ArenaMap arena) {
        server=s; map=arena; ledger=new MatchLedger(); capture=new CaptureGame(); ticks=0; running=false;
        presence.clear();
        started=ZonedDateTime.now();
        for (var p:s.getPlayerList().getPlayers()) ledger.add(p.getUUID(),p.getGameProfile().getName(),LobbyManager.team(p.getUUID()));
        boss=new ServerBossEvent(Component.literal(map.name()),BossEvent.BossBarColor.WHITE,BossEvent.BossBarOverlay.PROGRESS);
        boss.setProgress(0);
        floor.prepare(s,arena);
        for (var p:s.getPlayerList().getPlayers()) boss.addPlayer(p);
        LogUtils.getLogger().info("[GunGang] MATCH PREPARE date={} map={} ({}) dimension={} timeLimitSeconds={}",started,map.name(),map.id(),map.dimension(),map.timeLimitSeconds());
    }
    public static void start() { running=true; started=ZonedDateTime.now(); LogUtils.getLogger().info("[GunGang] MATCH START date={} map={} ({})",started,map.name(),map.id()); }
    public static void tick(int preparationSeconds) {
        if(map==null)return;
        for(var p:server.getPlayerList().getPlayers()) boss.addPlayer(p);
        if(running) {
            ticks++;
            for(var p:server.getPlayerList().getPlayers()) if(!LobbyManager.team(p.getUUID()).equals("SPECTATOR")) ledger.played(p.getUUID());
            var c=map.capture();
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
                boss.setProgress(Math.abs(capture.progress())/100f);
                int displaySide=capture.progress()==0?0:alpha&&!bravo?1:bravo&&!alpha?-1:capture.progress()>0?1:-1;
                boss.setColor(displaySide>0?BossEvent.BossBarColor.RED:displaySide<0?BossEvent.BossBarColor.BLUE:BossEvent.BossBarColor.WHITE);
                String status=alpha&&bravo?"경합 중":capture.progress()==0?"중립":Math.abs(capture.progress())==100?"확보":"점령 중";
                ChatFormatting middle=displaySide>0?ChatFormatting.RED:displaySide<0?ChatFormatting.BLUE:ChatFormatting.WHITE;
                boss.setName(Component.empty()
                        .append(Component.literal("Team ALPHA "+capture.alphaScore()).withStyle(ChatFormatting.RED))
                        .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(c.name()+" "+status).withStyle(middle))
                        .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(capture.bravoScore()+ " Team Bravo").withStyle(ChatFormatting.BLUE)));
                floor.color(capture.scoringOwner());
                String winner=capture.winner(c.winningScore());
                if(!winner.isEmpty()) { LobbyManager.finish(winner); return; }
            }
            if(map.timeLimitSeconds()>0&&ticks>=map.timeLimitSeconds()*20) { LobbyManager.finish(capture.leader()); return; }
        }
        if(server.getTickCount()%20==0) send(snapshot(false,"",preparationSeconds));
    }
    private static MatchSnapshot snapshot(boolean result,String winner,int preparation) {
        int seconds=result?ticks/20:!running?preparation:map.timeLimitSeconds()==0?ticks/20:Math.max(0,map.timeLimitSeconds()-(ticks/20));
        return new MatchSnapshot(!result,result,map.name(),running?"RUNNING":"PREPARING",seconds,!running||map.timeLimitSeconds()>0,winner,capture.alphaScore(),capture.bravoScore(),ledger.rows());
    }
    private static void send(MatchSnapshot value) { var packet=new GungangNetwork.Match(ServerFiles.GSON.toJson(value)); for(var p:server.getPlayerList().getPlayers())GungangNetwork.send(p,packet); }
    public static void end(String winner) {
        if(map==null)return;
        var result=snapshot(true,winner,0); var ended=ZonedDateTime.now();
        LogUtils.getLogger().info("[GunGang] MATCH END date={} started={} map={} ({}) dimension={} winner={} score={}:{} playTime={}",ended,started,map.name(),map.id(),map.dimension(),winner,capture.alphaScore(),capture.bravoScore(),format(ticks/20));
        try {
            Path file=MatchReport.save(ServerFiles.root().resolve("result"),map,result,started,ended);
            LogUtils.getLogger().info("[GunGang] Result saved: {}",file.toAbsolutePath());
        } catch(Exception error) { LogUtils.getLogger().error("[GunGang] Could not save match result",error); }
        floor.reset(); presence.clear(); boss.removeAllPlayers(); send(result); map=null; running=false;
    }
    public static String format(int seconds) { return MatchReport.time(seconds); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event) {
        if(!running||map==null||event.isCanceled())return;
        if(event.getEntity() instanceof ServerPlayer victim&&event.getSource().getEntity() instanceof ServerPlayer attacker)
            ledger.damage(attacker.getUUID(),victim.getUUID(),Math.min(victim.getHealth(),event.getAmount()),server.getTickCount());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(!running||map==null||event.isCanceled())return;
        if(event.getEntity() instanceof ServerPlayer victim) ledger.death(victim.getUUID(),event.getSource().getEntity() instanceof ServerPlayer killer?killer.getUUID():null,server.getTickCount());
    }
    private MatchManager() {}
}
