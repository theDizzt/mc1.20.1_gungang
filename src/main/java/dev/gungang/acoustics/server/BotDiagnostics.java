package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Opt-in, operator-only diagnostics. No entity names or global particles are modified. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class BotDiagnostics {
    private static final class Watch {
        final int slot;int entity=-1;
        final ArrayDeque<Vec3> trail=new ArrayDeque<>();
        Watch(int slot){this.slot=slot;}
    }
    private static final Map<UUID,Watch> WATCHERS=new HashMap<>();
    private static final DustParticleOptions ROUTE=dust(.2f,1,.2f),TRAIL=dust(.2f,.6f,1),GOAL=dust(1,.85f,.1f),FAILURE=dust(1,.15f,.15f);
    private static DustParticleOptions dust(float r,float g,float b){return new DustParticleOptions(new Vector3f(r,g,b),1.2f);}
    static void watch(CommandSourceStack source,int slot)throws Exception {
        var player=source.getPlayerOrException();
        if(!source.hasPermission(2))throw new IllegalArgumentException("관리자 권한이 필요합니다.");
        if(BotManager.agent(slot)==null)throw new IllegalArgumentException("경기에 참가 중인 봇 번호를 지정하세요.");
        WATCHERS.put(player.getUUID(),new Watch(slot));
        source.sendSuccess(()->Component.literal((slot+1)+"번 봇 진단 ON · 초록=현재 경로 / 파랑=최근 12초 이동 / 노랑=목적지 / 빨강=최근 실패 지점\n상세: /gungang bot debug info · 종료: /gungang bot debug off"),false);
        info(source);
    }
    static void off(CommandSourceStack source)throws Exception {
        var player=source.getPlayerOrException();WATCHERS.remove(player.getUUID());player.displayClientMessage(Component.empty(),true);
        source.sendSuccess(()->Component.literal("봇 진단 OFF"),false);
    }
    static void info(CommandSourceStack source)throws Exception {
        var watch=WATCHERS.get(source.getPlayerOrException().getUUID());
        if(watch==null)throw new IllegalArgumentException("먼저 /gungang bot debug <번호>로 선택하세요.");
        var agent=BotManager.agent(watch.slot);
        source.sendSuccess(()->Component.literal(agent==null?"봇이 경기에 없습니다.":details(agent)),false);
    }
    static String details(BotAgent agent) {
        if(agent.bot==null)return agent.status();
        var bot=agent.bot;var movement=agent.movement;var path=bot.getNavigation().getPath();
        String failure=movement.failureReason();
        if(movement.failurePoint()!=null)failure+=" · "+position(movement.failurePoint())+" · "+Math.max(0,BotManager.server().getTickCount()-movement.failureAt())/20+"초 전";
        return agent.status()+"\n위치="+position(bot.position())+" · 현재 경로 목표="+position(movement.diagnosticGoal(bot))
                +" · "+movement.navigationState(bot)+"\n경로="+(path==null?"없음":path.getNextNodeIndex()+"/"+path.getNodeCount()+" · 도달 가능="+path.canReach())
                +"\n최근 이동 계획="+position(movement.goal())
                +"\n최근 경로 실패="+failure;
    }
    private static String position(Vec3 p){return p==null?"없음":String.format(Locale.ROOT,"%.1f, %.1f, %.1f",p.x,p.y,p.z);}
    static void clear(){
        var server=BotManager.server();
        if(server!=null)for(var id:WATCHERS.keySet()){var p=server.getPlayerList().getPlayer(id);if(p!=null)p.displayClientMessage(Component.empty(),true);}
        WATCHERS.clear();
    }
    static boolean watching(UUID id){return WATCHERS.containsKey(id);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        var server=BotManager.server();
        if(event.phase!=TickEvent.Phase.END||server==null||WATCHERS.isEmpty()||server.getTickCount()%10!=0)return;
        var iterator=WATCHERS.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player==null||!player.hasPermissions(2)){if(player!=null)player.displayClientMessage(Component.empty(),true);iterator.remove();continue;}
            var watch=entry.getValue();var agent=BotManager.agent(watch.slot);
            if(agent==null){player.displayClientMessage(Component.literal("봇 진단 종료: 경기의 봇이 제거됨"),true);iterator.remove();continue;}
            var bot=agent.bot;
            if(bot==null){watch.trail.clear();player.displayClientMessage(Component.literal((watch.slot+1)+"번 봇 · 리스폰 대기"),true);continue;}
            if(watch.entity!=bot.getId()){watch.entity=bot.getId();watch.trail.clear();}
            watch.trail.addLast(bot.position());while(watch.trail.size()>24)watch.trail.removeFirst();
            if(player.level()!=bot.level()||player.distanceToSqr(bot)>128*128){player.displayClientMessage(Component.literal((watch.slot+1)+"번 봇 · 표시 범위 밖 (같은 차원·128블록 이내)"),true);continue;}
            var movement=agent.movement;var goal=movement.diagnosticGoal(bot);var path=bot.getNavigation().getPath();
            String action=movement.leavingSpawn()?"스폰 탈출":agent.tactics.action().label;
            String failure=movement.failureReason();if(failure.length()>10)failure=failure.substring(0,10)+"…";
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,"봇 %d · %s · %s · %s · 실패: %s",
                    watch.slot+1,action,goal==null?"목표 없음":String.format(Locale.ROOT,"%.1fm",bot.position().distanceTo(goal)),movement.navigationState(bot),failure)),true);
            for(var p:watch.trail)particle(player,TRAIL,p.add(0,.15,0));
            if(path!=null&&!path.isDone())for(int i=path.getNextNodeIndex(),count=0;i<path.getNodeCount()&&count<32;i++,count++)
                particle(player,ROUTE,Vec3.atBottomCenterOf(path.getNodePos(i)).add(0,.3,0));
            if(goal!=null)for(int i=0;i<4;i++)particle(player,GOAL,goal.add(0,.3+i*.4,0));
            if(movement.failurePoint()!=null&&server.getTickCount()-movement.failureAt()<200)particle(player,FAILURE,movement.failurePoint().add(0,.8,0));
        }
    }
    private static void particle(ServerPlayer player,DustParticleOptions type,Vec3 p) {
        if(player.position().distanceToSqr(p)<=128*128)player.serverLevel().sendParticles(player,type,true,p.x,p.y,p.z,1,0,0,0,0);
    }
    private BotDiagnostics() { }
}
