package dev.gungang.acoustics.server;

import java.util.List;

/** Sequential assault rules; BRAVO attacks, ALPHA defends. */
public record NormandyRules(int captureTicks, int decayDelayTicks, int decayStepTicks,
                            ArenaMap.Spawn attackLaunch, List<Point> points) {
    public record Point(String name, ArenaMap.Spawn center, double radius, ArenaMap.Spawn attackSpawn) { }
    public void validate() {
        if(captureTicks<1||captureTicks>72000||decayDelayTicks<0||decayDelayTicks>72000||decayStepTicks<1||decayStepTicks>1200
                ||attackLaunch==null||points==null||points.size()!=4) throw new IllegalArgumentException("Invalid Normandy rules");
        attackLaunch.validate();
        for(var p:points) {
            if(p==null||p.name()==null||p.name().isBlank()||p.name().length()>20||p.center()==null||p.attackSpawn()==null
                    ||!Double.isFinite(p.radius())||p.radius()<1||p.radius()>32) throw new IllegalArgumentException("Invalid Normandy point");
            p.center().validate(); p.attackSpawn().validate();
        }
    }
    private static ArenaMap.Spawn at(double x,double y,double z) { return new ArenaMap.Spawn(x,y,z,0,0); }
    public static NormandyRules defaults() {
        return new NormandyRules(400,400,10,new ArenaMap.Spawn(53,23,169,-90,0),List.of(
                new Point("A",at(17,4,222),5,at(60,22,163)),
                new Point("B",at(6,16,282),5,at(-30,5,197)),
                new Point("C",at(-25,18,333),5,at(-5,24,259)),
                new Point("D",at(40,12,330),5,at(23,22,258))));
    }
    public static ArenaMap example() {
        return new ArenaMap("normandy","노르망디","BRAVO 공격 / ALPHA 방어 · A → B → C → D",false,900,"adventure","minecraft:overworld",
                new ArenaMap.Spawn(14,15,385,180,0),new ArenaMap.Spawn(77,14,169,90,0),at(17,40,222),
                new ArenaMap.Area(9,9,377,20,22,390),new ArenaMap.Area(72,12,164,82,21,175),null,List.of(),List.of(),List.of(),defaults());
    }
}
