package dev.gungang.acoustics.server;

import java.util.List;

public record LobbySnapshot(boolean enabled, boolean show, boolean administrator, String you,
                            String selectedMap, String phase, int seconds, List<MapView> maps, List<Member> members, TeamOptions options, String selectedMode) {
    public record MapView(String id, String name, String description, int seconds, String gameMode, boolean deathmatch,String previewImage,boolean bomb,boolean deathmatchOnly) {
        public MapView(String id,String name,String description,int seconds,String gameMode,boolean deathmatch,String previewImage,boolean bomb){this(id,name,description,seconds,gameMode,deathmatch,previewImage,bomb,false);}
        public MapView(String id,String name,String description,int seconds,String gameMode,boolean deathmatch,String previewImage){this(id,name,description,seconds,gameMode,deathmatch,previewImage,false);}
    }
    public record Member(String id, String name, String team, boolean ready) { }
}
