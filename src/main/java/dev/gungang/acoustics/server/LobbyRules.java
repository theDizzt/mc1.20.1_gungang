package dev.gungang.acoustics.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class LobbyRules {
    public record Readiness(String team, boolean ready) { }
    public static boolean allReady(List<Readiness> players) {
        return players.stream().anyMatch(p -> p.team.equals("ALPHA") || p.team.equals("BRAVO"))
                && players.stream().filter(p -> !p.team.equals("SPECTATOR"))
                .allMatch(p -> (p.team.equals("ALPHA") || p.team.equals("BRAVO")) && p.ready);
    }
    /** Internal lobby key (never shown) that rolls a random map and mode instead of naming a map; 00 is a real map id. */
    public static final String RANDOM_MAP = "random";
    /** Modes the lobby can offer for a map, matching the mode buttons (CONTROL is hidden on bomb maps). */
    public static List<String> modes(boolean normandy, boolean capture, boolean bomb) {
        if (normandy) return List.of("NORMANDY");
        if (!capture && !bomb) return List.of("DEATHMATCH");
        return List.of(bomb ? "BOMB" : "CONTROL", "DEATHMATCH");
    }
    /** Random element, avoiding {@code current} when anything else is available; null for an empty list. */
    public static <T> T pick(List<T> options, T current, RandomGenerator random) {
        if (options.isEmpty()) return null;
        var others = new ArrayList<T>();
        for (T option : options) if (!Objects.equals(option, current)) others.add(option);
        var pool = others.isEmpty() ? options : others;
        return pool.get(random.nextInt(pool.size()));
    }
    private LobbyRules() { }
}
