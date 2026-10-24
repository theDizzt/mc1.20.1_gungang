package dev.gungang.acoustics.server;

import java.util.List;

public record LobbySnapshot(boolean enabled, boolean show, boolean administrator, String you,
                            String selectedMap, String phase, int seconds, List<MapView> maps, List<Member> members, TeamOptions options, String selectedMode,
                            boolean botEnabled, boolean botAdded, int botCount, int botMax) {
    public LobbySnapshot(boolean enabled,boolean show,boolean administrator,String you,String selectedMap,String phase,int seconds,
                         List<MapView> maps,List<Member> members,TeamOptions options,String selectedMode,boolean botEnabled,boolean botAdded){
        this(enabled,show,administrator,you,selectedMap,phase,seconds,maps,members,options,selectedMode,botEnabled,botAdded,botAdded?1:0,4);
    }
    public LobbySnapshot(boolean enabled,boolean show,boolean administrator,String you,String selectedMap,String phase,int seconds,
                         List<MapView> maps,List<Member> members,TeamOptions options,String selectedMode){
        this(enabled,show,administrator,you,selectedMap,phase,seconds,maps,members,options,selectedMode,false,false,0,4);
    }
    public record MapView(String id, String name, String description, int seconds, String gameMode, boolean deathmatch,String previewImage,boolean bomb,boolean deathmatchOnly,String weather,String timeOfDay,String releaseDate) {
        public MapView(String id,String name,String description,int seconds,String gameMode,boolean deathmatch,String previewImage,boolean bomb,boolean deathmatchOnly,String weather,String timeOfDay){this(id,name,description,seconds,gameMode,deathmatch,previewImage,bomb,deathmatchOnly,weather,timeOfDay,"");}
        public MapView(String id,String name,String description,int seconds,String gameMode,boolean deathmatch,String previewImage,boolean bomb,boolean deathmatchOnly){this(id,name,description,seconds,gameMode,deathmatch,previewImage,bomb,deathmatchOnly,"clear","day");}
        public MapView(String id,String name,String description,int seconds,String gameMode,boolean deathmatch,String previewImage,boolean bomb){this(id,name,description,seconds,gameMode,deathmatch,previewImage,bomb,false);}
        public MapView(String id,String name,String description,int seconds,String gameMode,boolean deathmatch,String previewImage){this(id,name,description,seconds,gameMode,deathmatch,previewImage,false);}
    }
    /** {@code skin} is set only for bots (their profile skin); players' faces come from their own skins. */
    public record Member(String id, String name, String team, boolean ready,String skin) {
        public boolean bot(){return skin!=null&&!skin.isEmpty();}
        public Member(String id,String name,String team,boolean ready){this(id,name,team,ready,"");}
    }
}
