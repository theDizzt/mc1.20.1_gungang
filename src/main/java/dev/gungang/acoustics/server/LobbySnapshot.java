package dev.gungang.acoustics.server;

import java.util.List;

public record LobbySnapshot(boolean enabled, boolean show, boolean administrator, String you,
                            String selectedMap, String phase, int seconds, List<MapView> maps, List<Member> members) {
    public record MapView(String id, String name, String description, int seconds, String gameMode) { }
    public record Member(String id, String name, String team, boolean ready) { }
}
