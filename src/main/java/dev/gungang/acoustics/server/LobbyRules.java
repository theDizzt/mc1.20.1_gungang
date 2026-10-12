package dev.gungang.acoustics.server;

import java.util.List;

public final class LobbyRules {
    public record Readiness(String team, boolean ready) { }
    public static boolean allReady(List<Readiness> players) {
        return players.stream().anyMatch(p -> p.team.equals("ALPHA") || p.team.equals("BRAVO"))
                && players.stream().filter(p -> !p.team.equals("SPECTATOR"))
                .allMatch(p -> (p.team.equals("ALPHA") || p.team.equals("BRAVO")) && p.ready);
    }
    private LobbyRules() { }
}
