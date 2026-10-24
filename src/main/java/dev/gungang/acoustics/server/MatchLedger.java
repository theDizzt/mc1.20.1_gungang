package dev.gungang.acoustics.server;

import java.util.*;

/** Match statistics include only damage between opposing participants. Caller caps damage by remaining health. */
public final class MatchLedger {
    private static final class Score {
        String name, team;
        int kills, deaths, assists, ticks;
        double damage;
        Score(String name, String team) { this.name = name; this.team = team; }
    }
    private final Map<UUID, Score> players = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, Integer>> hits = new HashMap<>();
    private final Map<String,Integer> teamKills = new HashMap<>();
    public int kills(String team) { return teamKills.getOrDefault(team,0); }
    public void add(UUID id, String name, String team) {
        var score=players.computeIfAbsent(id,ignored->new Score(name,team));
        score.name=name; score.team=team;
    }
    public void played(UUID id) { var score = players.get(id); if (score != null && !score.team.equals("SPECTATOR")) score.ticks++; }
    public boolean enemies(UUID attacker, UUID victim) {
        var a = players.get(attacker); var v = players.get(victim);
        return a != null && v != null && !attacker.equals(victim) && !a.team.equals("SPECTATOR") && !v.team.equals("SPECTATOR") && !a.team.equals(v.team);
    }
    public void damage(UUID attacker, UUID victim, double amount, int tick) {
        if (!enemies(attacker,victim) || !Double.isFinite(amount) || amount <= 0) return;
        players.get(attacker).damage += amount;
        hits.computeIfAbsent(victim, ignored -> new HashMap<>()).put(attacker,tick);
    }
    public void death(UUID victim, UUID killer, int tick) {
        death(victim,killer,tick,false);
    }
    public void death(UUID victim, UUID killer, int tick,boolean penalizeTeamKill) {
        var score = players.get(victim);
        if (score == null || score.team.equals("SPECTATOR")) return;
        score.deaths++;
        var contributors = hits.remove(victim);
        var attacker=players.get(killer);
        if(penalizeTeamKill&&killer!=null&&!killer.equals(victim)&&attacker!=null&&attacker.team.equals(score.team)
                &&(score.team.equals("ALPHA")||score.team.equals("BRAVO"))) {
            teamKills.merge(score.team.equals("ALPHA")?"BRAVO":"ALPHA",1,Integer::sum);
            return;
        }
        if (killer == null && contributors != null) killer = contributors.entrySet().stream()
                .filter(e -> tick - e.getValue() <= 200).max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (killer == null || !enemies(killer,victim)) return;
        players.get(killer).kills++;
        teamKills.merge(players.get(killer).team,1,Integer::sum);
        if (contributors != null) for (var entry : contributors.entrySet())
            if (!entry.getKey().equals(killer) && tick - entry.getValue() <= 200 && enemies(entry.getKey(),victim)) players.get(entry.getKey()).assists++;
    }
    public List<MatchSnapshot.Row> rows() {
        return players.entrySet().stream().map(e -> {
            var s=e.getValue(); return new MatchSnapshot.Row(e.getKey().toString(),s.name,s.team,s.kills,s.deaths,s.assists,s.damage,s.ticks/20,s.kills,0,0,0,BotManager.identity(e.getKey())?BotManager.skin(e.getKey()):"");
        }).sorted(Comparator.comparing(MatchSnapshot.Row::team).thenComparing(Comparator.comparingInt(MatchSnapshot.Row::kills).reversed()).thenComparing(MatchSnapshot.Row::name)).toList();
    }
}
