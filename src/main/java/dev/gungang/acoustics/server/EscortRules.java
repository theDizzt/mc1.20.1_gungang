package dev.gungang.acoustics.server;

import java.util.List;

/**
 * Map-owned escort settings. Speeds are blocks per second; durations are seconds.
 * The optional alpha/bravo spawn and area replace the map's team bases in escort only, so team deathmatch keeps its own.
 */
public record EscortRules(ArenaMap.Area scanArea, Double linkDistance, Double speed,
        Double extraPlayerMultiplier, Double maxMultiplier, Double radius,
        Integer checkpointStopSeconds, Integer checkpointBonusSeconds,
        Integer rollbackDelaySeconds, Double rollbackSpeed, List<Integer> rollbackSections,
        Integer healIntervalTicks, Float healAmount, List<Respawn> respawns,
        ArenaMap.Spawn alphaSpawn, ArenaMap.Spawn bravoSpawn, ArenaMap.Area alphaArea, ArenaMap.Area bravoArea) {
    public EscortRules(ArenaMap.Area scanArea, Double linkDistance, Double speed,
            Double extraPlayerMultiplier, Double maxMultiplier, Double radius,
            Integer checkpointStopSeconds, Integer checkpointBonusSeconds,
            Integer rollbackDelaySeconds, Double rollbackSpeed, List<Integer> rollbackSections,
            Integer healIntervalTicks, Float healAmount, List<Respawn> respawns) {
        this(scanArea,linkDistance,speed,extraPlayerMultiplier,maxMultiplier,radius,checkpointStopSeconds,checkpointBonusSeconds,
                rollbackDelaySeconds,rollbackSpeed,rollbackSections,healIntervalTicks,healAmount,respawns,null,null,null,null);
    }
    public EscortRules {
        linkDistance=linkDistance==null?16:linkDistance;
        speed=speed==null?1.0:speed;
        extraPlayerMultiplier=extraPlayerMultiplier==null?.25:extraPlayerMultiplier;
        maxMultiplier=maxMultiplier==null?2.0:maxMultiplier;
        radius=radius==null?5.0:radius;
        checkpointStopSeconds=checkpointStopSeconds==null?5:checkpointStopSeconds;
        checkpointBonusSeconds=checkpointBonusSeconds==null?180:checkpointBonusSeconds;
        rollbackDelaySeconds=rollbackDelaySeconds==null?10:rollbackDelaySeconds;
        rollbackSpeed=rollbackSpeed==null?.5:rollbackSpeed;
        rollbackSections=rollbackSections==null?List.of():List.copyOf(rollbackSections);
        healIntervalTicks=healIntervalTicks==null?40:healIntervalTicks;
        healAmount=healAmount==null?1f:healAmount;
        respawns=respawns==null?List.of():List.copyOf(respawns);
    }
    /** Spawns from this checkpoint on; an area marks that spawn's base for loadout changes. */
    public record Respawn(int checkpoint, ArenaMap.Spawn alpha, ArenaMap.Spawn bravo, ArenaMap.Area alphaArea, ArenaMap.Area bravoArea) {
        public Respawn(int checkpoint, ArenaMap.Spawn alpha, ArenaMap.Spawn bravo) {this(checkpoint,alpha,bravo,null,null);}
    }
    public static ArenaMap example(){
        var base=ArenaMap.example();
        var rules=new EscortRules(new ArenaMap.Area(-5,64,5,85,75,35),null,null,null,null,null,null,null,null,null,List.of(0,1),null,null,List.of(new Respawn(1,new ArenaMap.Spawn(24.5,65,.5,0,0),new ArenaMap.Spawn(70.5,65,.5,180,0))));
        return new ArenaMap("escort_example","금광 호위","ALPHA는 수레를 호위하고 BRAVO는 저지합니다. 좌표와 경로 블록을 설치한 뒤 활성화하세요.",false,240,"adventure","minecraft:overworld",base.alphaSpawn(),base.bravoSpawn(),base.spectatorSpawn(),base.alphaArea(),base.bravoArea(),null,List.of(),List.of(),List.of(),null,"","clear","day",null,List.of(),null,rules);
    }
    public void validate() {
        if(scanArea==null)throw new IllegalArgumentException("escort.scanArea is required");
        if(scanArea.maxX()-scanArea.minX()>1024||scanArea.maxZ()-scanArea.minZ()>1024||scanArea.maxY()-scanArea.minY()>256)throw new IllegalArgumentException("Escort scan bounds exceed 1024 x 256 x 1024");
        double volume=(scanArea.maxX()-scanArea.minX()+1)*(scanArea.maxY()-scanArea.minY()+1)*(scanArea.maxZ()-scanArea.minZ()+1);
        if(!Double.isFinite(volume)||volume<1||volume>2_000_000||scanArea.maxX()<scanArea.minX()||scanArea.maxY()<scanArea.minY()||scanArea.maxZ()<scanArea.minZ())throw new IllegalArgumentException("Invalid escort scan area (max 2 million blocks)");
        new ArenaMap.Spawn(scanArea.minX(),scanArea.minY(),scanArea.minZ(),0,0).validate();
        new ArenaMap.Spawn(scanArea.maxX(),scanArea.maxY(),scanArea.maxZ(),0,0).validate();
        for(double n:new double[]{linkDistance,speed,extraPlayerMultiplier,maxMultiplier,radius,rollbackSpeed,healAmount})
            if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException("Invalid escort number");
        if(linkDistance<1||linkDistance>64||speed<=0||speed>10||maxMultiplier<1||maxMultiplier>5||radius<1||radius>32||rollbackSpeed>10||checkpointStopSeconds<0||checkpointStopSeconds>60||checkpointBonusSeconds<0||checkpointBonusSeconds>3600||rollbackDelaySeconds<1||rollbackDelaySeconds>3600||healIntervalTicks<1||healIntervalTicks>1200||healAmount>100)throw new IllegalArgumentException("Escort setting out of range");
        if(rollbackSections.stream().anyMatch(i->i==null||i<0||i>3))throw new IllegalArgumentException("rollbackSections must contain section indices 0..3");
        var seen=new java.util.HashSet<Integer>();
        for(var r:respawns){if(r==null||r.checkpoint()<1||r.checkpoint()>3||!seen.add(r.checkpoint()))throw new IllegalArgumentException("Invalid escort respawn checkpoint");base(r.alpha(),r.alphaArea());base(r.bravo(),r.bravoArea());}
        base(alphaSpawn,alphaArea);base(bravoSpawn,bravoArea);
        if((alphaSpawn==null)!=(alphaArea==null)||(bravoSpawn==null)!=(bravoArea==null))throw new IllegalArgumentException("Escort start spawn and area must be set together");
    }
    private static void base(ArenaMap.Spawn spawn,ArenaMap.Area area){
        if(spawn!=null)spawn.validate();
        if(area!=null){if(spawn==null)throw new IllegalArgumentException("Escort base area needs a spawn");area.validateRoom(spawn);}
    }
}
