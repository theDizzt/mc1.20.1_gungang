package dev.gungang.acoustics.server;

import java.util.*;

/** Server-thread scheduling: aging prevents spawn priority from starving other bots. */
final class BotPathSchedule {
    private record Waiting(int since,int last) { }
    private static final Map<UUID,Waiting> waiting=new HashMap<>();
    private static BotAgent current;
    private static boolean denied,served;
    static List<BotAgent> order(List<BotAgent> agents,int now){
        var ids=new HashSet<UUID>();for(var a:agents)ids.add(a.id);
        waiting.entrySet().removeIf(e->!ids.contains(e.getKey())||now-e.getValue().last()>100);
        var result=new ArrayList<>(agents);
        if(!result.isEmpty())Collections.rotate(result,-Math.floorMod(now,result.size()));
        // Stable sort preserves round-robin order for equal priorities.
        result.sort(Comparator.comparingLong((BotAgent a)->priority(a,now)).reversed());
        return result;
    }
    private static long priority(BotAgent a,int now){
        if(a.bot==null||!a.bot.isAlive()||a.bot.isRemoved())return 0;
        var wait=waiting.get(a.id);
        return (wait==null?0:Math.max(0L,(long)now-wait.since()))+(a.movement.leavingSpawn()?20:0);
    }
    static void begin(BotAgent agent){current=agent;denied=false;served=false;}
    static void deferred(){if(current!=null)denied=true;}
    static void serviced(){if(current!=null)served=true;}
    static void end(int now,boolean running){
        if(current==null)return;
        current.movement.metrics.tick(current,now,running,denied,served);
        if(current.bot==null||current.bot.isRemoved()||!current.bot.isAlive())waiting.remove(current.id);
        else if(denied){
            var old=waiting.get(current.id);
            waiting.put(current.id,new Waiting(served||old==null?now:old.since(),now));
        }else if(served)waiting.remove(current.id);
        current=null;
    }
    static void clear(){waiting.clear();current=null;denied=false;served=false;}
    private BotPathSchedule() { }
}
