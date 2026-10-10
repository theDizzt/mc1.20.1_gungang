package dev.gungang.acoustics.server;

import java.util.List;

public record MatchSnapshot(boolean active, boolean result, String map, String phase, int seconds, boolean countdown,
                            String winner, int alphaScore, int bravoScore, List<Row> players) {
    public record Row(String id, String name, String team, int kills, int deaths, int assists, double damage, int playSeconds) { }
}
