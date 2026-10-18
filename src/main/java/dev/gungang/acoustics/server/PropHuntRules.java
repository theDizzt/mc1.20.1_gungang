package dev.gungang.acoustics.server;

/** Presence of propHunt in a map selects this dedicated mode; any other map can also be played as prop hunt from the lobby. */
public record PropHuntRules(String shotgun) {
    public PropHuntRules {shotgun=shotgun==null?"":shotgun.strip();}
    /** A map's team spawns, falling back to bomb attack/defense spawns, are the hiders' start and the seekers' waiting room. */
    public static boolean supported(ArenaMap map) {
        return map!=null&&(map.propHunt()!=null||(map.alphaSpawn()!=null||map.bomb()!=null&&map.bomb().attackSpawn()!=null)&&(map.bravoSpawn()!=null||map.bomb()!=null&&map.bomb().defenseSpawn()!=null));
    }
    public static ArenaMap arena(ArenaMap source) {
        if(source.propHunt()!=null)return source;
        if(!supported(source))throw new IllegalArgumentException("이 맵에서는 숨바꼭질을 선택할 수 없습니다.");
        var bomb=source.bomb();
        var alpha=source.alphaSpawn()!=null?source.alphaSpawn():bomb.attackSpawn();var bravo=source.bravoSpawn()!=null?source.bravoSpawn():bomb.defenseSpawn();
        var alphaArea=source.alphaSpawn()!=null||bomb==null?source.alphaArea():bomb.attackArea();var bravoArea=source.bravoSpawn()!=null||bomb==null?source.bravoArea():bomb.defenseArea();
        // No objectives, floor recoloring or map function hooks, as in team deathmatch.
        return new ArenaMap(source.id(),source.name(),source.description(),source.enabled(),600,source.gameMode(),source.dimension(),alpha,bravo,source.spectatorSpawn(),
                alphaArea,bravoArea,null,java.util.List.of(),java.util.List.of(),java.util.List.of(),null,source.previewImage(),source.weather(),source.timeOfDay(),null,source.deathZones(),source.previewArea(),null,null,new PropHuntRules(""),source.releaseDate(),source.propHuntMinutes());
    }
    public static ArenaMap example(){
        var m=ArenaMap.example();
        return new ArenaMap("prop_hunt","숨바꼭질","스폰과 술래 대기실 좌표를 수정하고 enabled를 true로 바꾸세요.",false,600,"adventure",m.dimension(),m.alphaSpawn(),m.bravoSpawn(),m.spectatorSpawn(),m.alphaArea(),m.bravoArea(),null,java.util.List.of(),java.util.List.of(),java.util.List.of(),null,"","clear","day",null,java.util.List.of(),null,null,null,new PropHuntRules(""));
    }
}
