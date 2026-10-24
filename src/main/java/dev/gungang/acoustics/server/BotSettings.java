package dev.gungang.acoustics.server;

/**
 * Bounded server work; one bot only. Intervals are game ticks (20 ticks = one second).
 * Skill: {@code reactionTicks} before the first shot at a new target, {@code aimError} degrees of aim spread,
 * {@code turnRate} degrees of view rotation per tick, {@code memorySeconds} an unseen enemy is remembered,
 * {@code fov} degrees of the vision cone. {@code difficulty} names the preset those came from (or "custom").
 */
public record BotSettings(boolean enabled, String team, String weapon, int thinkTicks, int pathTicks,
                          int range, int reactionTicks, double aimError,String secondary,Double movementSpeed,
                          Double turnRate,Double memorySeconds,Integer fov,String difficulty) {
    public BotSettings(boolean enabled,String team,String weapon,int thinkTicks,int pathTicks,int range,int reactionTicks,double aimError){
        this(enabled,team,weapon,thinkTicks,pathTicks,range,reactionTicks,aimError,"",1.0,null,null,null,null);
    }
    public static BotSettings defaults(){return new BotSettings(false,"AUTO","",5,10,48,6,3).difficulty("normal");}
    /** Long-range visual awareness is separate from local utility searches. */
    public int visionRange(){return Math.max(48,range);}
    public BotSettings {
        secondary=secondary==null?"":secondary;
        // 1.0 = a sprinting player with the same weapon.
        movementSpeed=movementSpeed==null?1.0:movementSpeed;
        turnRate=turnRate==null?14.0:turnRate;
        memorySeconds=memorySeconds==null?3.0:memorySeconds;
        fov=fov==null?110:fov;
        difficulty=difficulty==null||difficulty.isBlank()?"custom":difficulty;
        if(!secondary.isEmpty()&&!secondary.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("Invalid secondary weapon id");
        if(!Double.isFinite(movementSpeed)||movementSpeed<.5||movementSpeed>2)throw new IllegalArgumentException("movementSpeed: 0.5~2");
        if(!java.util.Set.of("AUTO","ALPHA","BRAVO").contains(team))throw new IllegalArgumentException("team: AUTO / ALPHA / BRAVO");
        if(weapon==null||(!weapon.isEmpty()&&!weapon.matches("[a-z0-9_-]{1,48}")))throw new IllegalArgumentException("Invalid weapon id");
        if(thinkTicks<5||thinkTicks>100||pathTicks<10||pathTicks>200||range<8||range>64||reactionTicks<2||reactionTicks>100
                ||!Double.isFinite(aimError)||aimError<0.5||aimError>15)throw new IllegalArgumentException("Bot setting out of range");
        if(!Double.isFinite(turnRate)||turnRate<2||turnRate>90||!Double.isFinite(memorySeconds)||memorySeconds<.5||memorySeconds>30||fov<60||fov>360)
            throw new IllegalArgumentException("turnRate 2~90, memorySeconds 0.5~30, fov 60~360");
        if(!difficulty.matches("[a-z]{1,16}"))throw new IllegalArgumentException("Invalid difficulty label");
    }
    private BotSettings with(int think,int path,int range,int reaction,double aim,Double speed,Double turn,Double memory,Integer view,String label){
        return new BotSettings(enabled,team,weapon,think,path,range,reaction,aim,secondary,speed,turn,memory,view,label);
    }
    public BotSettings enabled(boolean value){return new BotSettings(value,team,weapon,thinkTicks,pathTicks,range,reactionTicks,aimError,secondary,movementSpeed,turnRate,memorySeconds,fov,difficulty);}
    public BotSettings team(String value){return new BotSettings(enabled,value,weapon,thinkTicks,pathTicks,range,reactionTicks,aimError,secondary,movementSpeed,turnRate,memorySeconds,fov,difficulty);}
    public BotSettings weapon(String value){return new BotSettings(enabled,team,value,thinkTicks,pathTicks,range,reactionTicks,aimError,secondary,movementSpeed,turnRate,memorySeconds,fov,difficulty);}
    public BotSettings secondary(String value){return new BotSettings(enabled,team,weapon,thinkTicks,pathTicks,range,reactionTicks,aimError,value,movementSpeed,turnRate,memorySeconds,fov,difficulty);}
    /** Changing a skill value by hand leaves the named difficulty. */
    public BotSettings set(String key,double value){
        boolean skill=java.util.Set.of("reactionTicks","aimError","turnRate","memorySeconds","fov").contains(key);
        return with(key.equals("thinkTicks")?(int)value:thinkTicks,key.equals("pathTicks")?(int)value:pathTicks,
                key.equals("range")?(int)value:range,key.equals("reactionTicks")?(int)value:reactionTicks,key.equals("aimError")?value:aimError,
                key.equals("movementSpeed")?value:movementSpeed,key.equals("turnRate")?value:turnRate,key.equals("memorySeconds")?value:memorySeconds,
                key.equals("fov")?(int)value:fov,skill?"custom":difficulty);
    }
    /** Server cost presets; skill stays as it is. */
    public BotSettings preset(String name){return switch(name){
        case "low" -> with(20,40,24,reactionTicks,aimError,movementSpeed,turnRate,memorySeconds,fov,difficulty);
        case "normal" -> with(5,10,48,reactionTicks,aimError,movementSpeed,turnRate,memorySeconds,fov,difficulty);
        case "high" -> with(5,10,48,reactionTicks,aimError,movementSpeed,turnRate,memorySeconds,fov,difficulty);
        case "responsive" -> with(5,10,48,4,aimError,1.1,turnRate,memorySeconds,fov,difficulty);
        default -> throw new IllegalArgumentException("preset: low / normal / high / responsive");
    };}
    /**
     * Skill presets. Starting points for tuning, not measured results: reaction (base, ±30% jitter), aim spread,
     * view turn speed, memory of an unseen enemy and vision cone. Hit rate also depends on weapon and map.
     */
    public BotSettings difficulty(String name){return switch(name){
        case "easy" -> with(thinkTicks,pathTicks,range,9,6,movementSpeed,8.0,2.0,100,"easy");      // ~450 ms
        case "normal" -> with(thinkTicks,pathTicks,range,6,3.5,movementSpeed,14.0,3.0,110,"normal"); // ~300 ms
        case "hard" -> with(thinkTicks,pathTicks,range,4,2,movementSpeed,22.0,4.0,120,"hard");      // ~130-230 ms
        case "expert" -> with(thinkTicks,pathTicks,range,2,1,movementSpeed,35.0,6.0,130,"expert");  // ~100 ms
        default -> throw new IllegalArgumentException("difficulty: easy / normal / hard / expert");
    };}
    public String chooseTeam(long alpha,long bravo){return team.equals("AUTO")?(alpha<bravo?"ALPHA":"BRAVO"):team;}
}
