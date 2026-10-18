package dev.gungang.acoustics.server;

import java.util.*;

/** A monotonic sequence resolves equal scores even when kills occur in the same tick. */
public final class FreeForAllScores {
    private record Score(int points,long reached) {}
    private final Map<UUID,Score> scores=new LinkedHashMap<>();
    private long sequence;
    public void add(UUID id){scores.computeIfAbsent(id,k->new Score(0,sequence++));}
    public int points(UUID id){return scores.containsKey(id)?scores.get(id).points():0;}
    public void death(UUID victim,UUID killer){
        if(!scores.containsKey(victim))return;
        UUID target=killer!=null&&!killer.equals(victim)&&scores.containsKey(killer)?killer:victim;
        scores.put(target,new Score(points(target)+(target.equals(victim)?-1:1),sequence++));
    }
    public List<UUID> ranking(){return scores.keySet().stream().sorted(Comparator.<UUID>comparingInt(this::points).reversed().thenComparingLong(id->scores.get(id).reached())).toList();}
    public UUID winner(int goal,boolean expired){var rank=ranking();return rank.isEmpty()?null:expired||points(rank.get(0))>=goal?rank.get(0):null;}
}
