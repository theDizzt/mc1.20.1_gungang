package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;

/** Diagnostics for the current life; no path searches or world scans. Times use server ticks. */
final class BotMetrics {
    private int spawnStart=-1,spawnEnd=-1,waitStart=-1,lastTick=-1,lastDenied=-1,maxWait;
    private long waited,searches,retries;
    private Set<BlockPos> previousTargets=Set.of();
    private final ArrayDeque<Integer> stalls=new ArrayDeque<>();
    void reset(){spawnStart=spawnEnd=waitStart=lastTick=lastDenied=-1;maxWait=0;waited=searches=retries=0;previousTargets=Set.of();stalls.clear();}
    void search(Set<BlockPos> targets){
        searches++;
        if(!targets.isEmpty()&&previousTargets.equals(targets))retries++;
        previousTargets=Set.copyOf(targets);
    }
    void stalled(int now){
        trim(now);
        if(stalls.isEmpty()||stalls.peekLast()!=now)stalls.addLast(now);
    }
    private void trim(int now){while(!stalls.isEmpty()&&now-stalls.peekFirst()>=1200)stalls.removeFirst();}
    void tick(BotAgent agent,int now,boolean running,boolean denied,boolean served){
        trim(now);
        if(!running||agent.bot==null||!agent.bot.isAlive()||agent.bot.isRemoved()){
            waitStart=-1;lastTick=now;return;
        }
        if(spawnStart<0)spawnStart=now;
        if(spawnEnd<0&&BotMovement.clearOfSpawn(BotManager.arena(),agent.team,agent.bot.position()))spawnEnd=now;
        if(waitStart>=0){
            int until=Math.min(now,lastDenied+100);
            waited+=Math.max(0,until-Math.max(lastTick,waitStart));
            maxWait=Math.max(maxWait,Math.max(0,until-waitStart));
            if(served||now-lastDenied>100)waitStart=-1;
        }
        if(denied){if(waitStart<0)waitStart=now;lastDenied=now;}
        lastTick=now;
    }
    String summary(int now){
        trim(now);
        String spawn=spawnStart<0?"미측정":seconds((spawnEnd<0?now:spawnEnd)-spawnStart)+(spawnEnd<0?" 진행":"");
        return "탈출 "+spawn+" · 경로 대기 "+seconds(waited)+" · 재탐색 "+retries+"회 · 정체 "+stalls.size()+"회/최근 60초";
    }
    String details(int now){
        return "이 생명 기준: "+summary(now)+"\n경로 탐색 "+searches+"회 · 연속 동일 목적지 재탐색 "+retries
                +"회 · 현재 예산 대기 "+seconds(waitStart<0?0:Math.max(0,now-waitStart))+" · 최장 대기 "+seconds(maxWait)
                +"\n시간은 서버 틱 기준이며 리스폰 시 초기화됩니다.";
    }
    private static String seconds(long ticks){return String.format(Locale.ROOT,"%.2f초",Math.max(0,ticks)/20.0);}
}
