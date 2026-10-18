package dev.gungang.acoustics.server;

import java.util.List;

/** Dedicated arena settings; the opening grace period counts toward the time limit. */
public record FreeForAllRules(Integer timeLimitSeconds,Integer winningScore,List<ArenaMap.Spawn> spawns) {
    public FreeForAllRules {
        timeLimitSeconds=timeLimitSeconds==null?600:timeLimitSeconds;
        winningScore=winningScore==null?16:winningScore;
        spawns=spawns==null?List.of():List.copyOf(spawns);
        validateValues(timeLimitSeconds,winningScore,spawns);
    }
    private static void validateValues(int time,int goal,List<ArenaMap.Spawn> spawns){
        if(time<30||time>86400||goal<1||goal>10000||spawns.isEmpty()||spawns.size()>256)
            throw new IllegalArgumentException("Invalid freeForAll timeLimitSeconds / winningScore / spawns");
        spawns.forEach(ArenaMap.Spawn::validate);
    }
    public static ArenaMap example(){
        var base=ArenaMap.example();
        return new ArenaMap("free_for_all","개인전 예제","스폰 좌표를 편집하고 enabled를 true로 설정하세요.",false,600,
            base.gameMode(),base.dimension(),base.alphaSpawn(),base.bravoSpawn(),base.spectatorSpawn(),
            base.alphaArea(),base.bravoArea(),null,List.of(),List.of(),List.of(),null,"","clear","day",null,List.of(),null,null,
            new FreeForAllRules(null,null,List.of(base.alphaSpawn(),base.bravoSpawn(),new ArenaMap.Spawn(20.5,65,20.5,180,0),new ArenaMap.Spawn(20.5,65,-20.5,0,0))));
    }
    public void validate(){validateValues(timeLimitSeconds,winningScore,spawns);}
}
