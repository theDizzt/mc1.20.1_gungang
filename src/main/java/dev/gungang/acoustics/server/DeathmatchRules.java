package dev.gungang.acoustics.server;

/** Rules shared by the three non-Normandy arenas. */
public record DeathmatchRules(Integer timeLimitSeconds,Integer winningKills) {
    public DeathmatchRules {
        timeLimitSeconds=timeLimitSeconds==null?1200:timeLimitSeconds;
        winningKills=winningKills==null?100:winningKills;
        if(timeLimitSeconds<1||timeLimitSeconds>86400||winningKills<1||winningKills>10000)
            throw new IllegalArgumentException("Invalid team deathmatch time limit or winning kills");
    }
    public static DeathmatchRules defaults() { return new DeathmatchRules(1200,100); }
    public static boolean supported(ArenaMap map) { return map!=null&&map.normandy()==null; }
    public static boolean exclusive(ArenaMap map) { return supported(map)&&map.capture()==null&&map.bomb()==null; }
    public ArenaMap arena(ArenaMap source) {
        if(!supported(source)) throw new IllegalArgumentException("이 맵에서는 팀 데스매치를 선택할 수 없습니다.");
        // No capture logic, floor recoloring or legacy objective function hooks in deathmatch.
        return new ArenaMap(source.id(),source.name(),source.description(),source.enabled(),timeLimitSeconds,
                source.gameMode(),source.dimension(),source.alphaSpawn(),source.bravoSpawn(),source.spectatorSpawn(),
                source.alphaArea(),source.bravoArea(),null,java.util.List.of(),java.util.List.of(),java.util.List.of(),null,source.previewImage(),source.weather(),source.timeOfDay(),null,source.deathZones(),source.previewArea());
    }
    public String winner(int alpha,int bravo,boolean expired) {
        if(alpha>=winningKills)return "ALPHA";
        if(bravo>=winningKills)return "BRAVO";
        return expired?(alpha>bravo?"ALPHA":bravo>alpha?"BRAVO":"DRAW"):"";
    }
}
