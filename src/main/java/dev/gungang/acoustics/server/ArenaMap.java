package dev.gungang.acoustics.server;

/** Config data only: safe to validate without loading client or world classes. */
public record ArenaMap(String id, String name, String description, boolean enabled, int timeLimitSeconds,
                       String gameMode, String dimension, Spawn alphaSpawn, Spawn bravoSpawn, Spawn spectatorSpawn,
                       Area alphaArea, Area bravoArea, Capture capture, java.util.List<String> onPrepare,
                       java.util.List<String> onStart, java.util.List<String> onEnd, NormandyRules normandy,
                       @com.google.gson.annotations.SerializedName(value="previewImage",alternate={"preview"}) String previewImage,
                       String weather,String timeOfDay,BombRules bomb,java.util.List<DeathZone> deathZones,MapPreviewArea previewArea,EscortRules escort,FreeForAllRules freeForAll,PropHuntRules propHunt,String releaseDate,
                       /** Prop hunt seek time on this map, hiding minute excluded; null keeps the 10-minute default. */ Integer propHuntMinutes) {
    public ArenaMap(String id, String name, String description, boolean enabled, int timeLimitSeconds,
                       String gameMode, String dimension, Spawn alphaSpawn, Spawn bravoSpawn, Spawn spectatorSpawn,
                       Area alphaArea, Area bravoArea, Capture capture, java.util.List<String> onPrepare,
                       java.util.List<String> onStart, java.util.List<String> onEnd, NormandyRules normandy,
                       String previewImage,
                       String weather,String timeOfDay,BombRules bomb,java.util.List<DeathZone> deathZones,MapPreviewArea previewArea,EscortRules escort,FreeForAllRules freeForAll,PropHuntRules propHunt,String releaseDate) {this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,weather,timeOfDay,bomb,deathZones,previewArea,escort,freeForAll,propHunt,releaseDate,null);}
    public ArenaMap(String id, String name, String description, boolean enabled, int timeLimitSeconds,
                       String gameMode, String dimension, Spawn alphaSpawn, Spawn bravoSpawn, Spawn spectatorSpawn,
                       Area alphaArea, Area bravoArea, Capture capture, java.util.List<String> onPrepare,
                       java.util.List<String> onStart, java.util.List<String> onEnd, NormandyRules normandy,
                       String previewImage,
                       String weather,String timeOfDay,BombRules bomb,java.util.List<DeathZone> deathZones,MapPreviewArea previewArea,EscortRules escort,FreeForAllRules freeForAll,PropHuntRules propHunt) {this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,weather,timeOfDay,bomb,deathZones,previewArea,escort,freeForAll,propHunt,null);}
    public ArenaMap(String id, String name, String description, boolean enabled, int timeLimitSeconds,
                       String gameMode, String dimension, Spawn alphaSpawn, Spawn bravoSpawn, Spawn spectatorSpawn,
                       Area alphaArea, Area bravoArea, Capture capture, java.util.List<String> onPrepare,
                       java.util.List<String> onStart, java.util.List<String> onEnd, NormandyRules normandy,
                       String previewImage,
                       String weather,String timeOfDay,BombRules bomb,java.util.List<DeathZone> deathZones,MapPreviewArea previewArea,EscortRules escort,FreeForAllRules freeForAll) {this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,weather,timeOfDay,bomb,deathZones,previewArea,escort,freeForAll,null);}

 public ArenaMap(String id, String name, String description, boolean enabled, int timeLimitSeconds,
                       String gameMode, String dimension, Spawn alphaSpawn, Spawn bravoSpawn, Spawn spectatorSpawn,
                       Area alphaArea, Area bravoArea, Capture capture, java.util.List<String> onPrepare,
                       java.util.List<String> onStart, java.util.List<String> onEnd, NormandyRules normandy,
                       String previewImage,
                       String weather,String timeOfDay,BombRules bomb,java.util.List<DeathZone> deathZones,MapPreviewArea previewArea,EscortRules escort) {this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,weather,timeOfDay,bomb,deathZones,previewArea,escort,null);}

    public ArenaMap(String id,String name,String description,boolean enabled,int timeLimitSeconds,String gameMode,String dimension,
                    Spawn alphaSpawn,Spawn bravoSpawn,Spawn spectatorSpawn,Area alphaArea,Area bravoArea,Capture capture,
                    java.util.List<String> onPrepare,java.util.List<String> onStart,java.util.List<String> onEnd,NormandyRules normandy,String previewImage,String weather,String timeOfDay,BombRules bomb,java.util.List<DeathZone> deathZones,MapPreviewArea previewArea){
        this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,weather,timeOfDay,bomb,deathZones,previewArea,null);
    }
    public ArenaMap(String id,String name,String description,boolean enabled,int timeLimitSeconds,String gameMode,String dimension,
                    Spawn alphaSpawn,Spawn bravoSpawn,Spawn spectatorSpawn,Area alphaArea,Area bravoArea,Capture capture,
                    java.util.List<String> onPrepare,java.util.List<String> onStart,java.util.List<String> onEnd,NormandyRules normandy,String previewImage,String weather,String timeOfDay,BombRules bomb,java.util.List<DeathZone> deathZones){
        this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,weather,timeOfDay,bomb,deathZones,null);
    }
    public ArenaMap {
        if(propHunt!=null)timeLimitSeconds=propHuntMinutes==null?600:propHuntMinutes*60;
 if(freeForAll!=null)timeLimitSeconds=freeForAll.timeLimitSeconds();
        if(escort!=null&&timeLimitSeconds==0)timeLimitSeconds=240;
        deathZones=deathZones==null?java.util.List.of():java.util.List.copyOf(deathZones);
        if(bomb!=null){
            alphaSpawn=bomb.attackSpawn();bravoSpawn=bomb.defenseSpawn();alphaArea=bomb.attackArea();bravoArea=bomb.defenseArea();
            if(timeLimitSeconds==0)timeLimitSeconds=210;
        }
        previewImage=previewImage==null?"":previewImage.strip();
        weather=weather==null?"clear":weather.strip().toLowerCase(java.util.Locale.ROOT);
        timeOfDay=timeOfDay==null?"day":timeOfDay.strip().toLowerCase(java.util.Locale.ROOT);
        releaseDate=ArenaMap.releaseDate(releaseDate,id);
        if(propHuntMinutes!=null&&(propHuntMinutes<2||propHuntMinutes>60))throw new IllegalArgumentException("propHuntMinutes must be 2-60");
    }
    public ArenaMap(String id,String name,String description,boolean enabled,int timeLimitSeconds,String gameMode,String dimension,
                    Spawn alphaSpawn,Spawn bravoSpawn,Spawn spectatorSpawn,Area alphaArea,Area bravoArea,Capture capture,
                    java.util.List<String> onPrepare,java.util.List<String> onStart,java.util.List<String> onEnd,NormandyRules normandy,String previewImage,String weather,String timeOfDay,BombRules bomb){
        this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,weather,timeOfDay,bomb,java.util.List.of());
    }
    public ArenaMap(String id,String name,String description,boolean enabled,int timeLimitSeconds,String gameMode,String dimension,
                    Spawn alphaSpawn,Spawn bravoSpawn,Spawn spectatorSpawn,Area alphaArea,Area bravoArea,Capture capture,
                    java.util.List<String> onPrepare,java.util.List<String> onStart,java.util.List<String> onEnd,NormandyRules normandy,String previewImage,String weather,String timeOfDay){
        this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,weather,timeOfDay,null);
    }
    public ArenaMap(String id,String name,String description,boolean enabled,int timeLimitSeconds,String gameMode,String dimension,
                    Spawn alphaSpawn,Spawn bravoSpawn,Spawn spectatorSpawn,Area alphaArea,Area bravoArea,Capture capture,
                    java.util.List<String> onPrepare,java.util.List<String> onStart,java.util.List<String> onEnd,NormandyRules normandy,String previewImage) {
        this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,
                alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,previewImage,"clear","day");
    }
    public ArenaMap(String id,String name,String description,boolean enabled,int timeLimitSeconds,String gameMode,String dimension,
                    Spawn alphaSpawn,Spawn bravoSpawn,Spawn spectatorSpawn,Area alphaArea,Area bravoArea,Capture capture,
                    java.util.List<String> onPrepare,java.util.List<String> onStart,java.util.List<String> onEnd,NormandyRules normandy) {
        this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,
                alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,normandy,"");
    }
    public ArenaMap(String id,String name,String description,boolean enabled,int timeLimitSeconds,String gameMode,String dimension,
                    Spawn alphaSpawn,Spawn bravoSpawn,Spawn spectatorSpawn,Area alphaArea,Area bravoArea,Capture capture,
                    java.util.List<String> onPrepare,java.util.List<String> onStart,java.util.List<String> onEnd) {
        this(id,name,description,enabled,timeLimitSeconds,gameMode,dimension,alphaSpawn,bravoSpawn,spectatorSpawn,
                alphaArea,bravoArea,capture,onPrepare,onStart,onEnd,null);
    }
    /** Optional release date shared by map and weapon configs: blank, or yyyy-MM-dd. */
    static String releaseDate(String value,String id) {
        value=value==null?"":value.strip();
        if(!value.isEmpty())try{java.time.LocalDate.parse(value);}catch(java.time.DateTimeException failure){throw new IllegalArgumentException("Invalid releaseDate (yyyy-MM-dd): "+id);}
        return value;
    }
    public record Area(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
        public void validate(Spawn spawn) {
            double[] coordinates = {minX, minY, minZ, maxX, maxY, maxZ};
            for (double value : coordinates) if (!Double.isFinite(value) || Math.abs(value) > 29_999_000) throw new IllegalArgumentException("Invalid spawn area");
            if (maxX - minX < 1 || maxY - minY < 2 || maxZ - minZ < 1 || !contains(spawn.x, spawn.y, spawn.z))
                throw new IllegalArgumentException("Spawn must be inside a nonempty team area");
        }
        /** A team spawn room: preparation keeps the whole player box inside, so a ceiling below head height pins players to the spawn. */
        public void validateRoom(Spawn spawn) {
            validate(spawn);
            if (spawn.y + 1.8 > maxY) throw new IllegalArgumentException("Spawn area maxY must be at least spawn y + 1.8 (player height)");
        }
    }
    public record Capture(String name, Area area, int winningScore, int captureStepTicks, int scoreIntervalTicks) {
        public void validate() {
            if (name == null || name.isBlank() || name.length() > 80 || area == null || winningScore < 1 || winningScore > 10000
                    || captureStepTicks < 1 || captureStepTicks > 200 || scoreIntervalTicks < 1 || scoreIntervalTicks > 1200)
                throw new IllegalArgumentException("Invalid capture rules");
            area.validate(new Spawn((area.minX + area.maxX) / 2, (area.minY + area.maxY) / 2, (area.minZ + area.maxZ) / 2, 0, 0));
        }
    }
    public record Spawn(double x, double y, double z, float yaw, float pitch) {
        public void validate() {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 29_999_000 || Math.abs(z) > 29_999_000 || y < -2048 || y > 2048
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90)
                throw new IllegalArgumentException("Invalid spawn coordinates");
        }
    }
    public ArenaMap validate() {
        if(propHunt!=null&&(freeForAll!=null||escort!=null||bomb!=null||capture!=null||normandy!=null))throw new IllegalArgumentException("Prop hunt requires a dedicated map");
        if(freeForAll!=null){freeForAll.validate();if(escort!=null||bomb!=null||capture!=null||normandy!=null)throw new IllegalArgumentException("Free-for-all requires a dedicated map");}
 if(escort!=null){escort.validate();if(capture!=null||normandy!=null||bomb!=null||timeLimitSeconds<1)throw new IllegalArgumentException("Escort requires its own map without other objective rules");}
        if(previewArea!=null)previewArea.validate();
        if(deathZones.size()>128)throw new IllegalArgumentException("At most 128 death zones per map");
        deathZones.forEach(DeathZone::validate);
        if(!java.util.Set.of("clear","fog","rain","snow").contains(weather)||!java.util.Set.of("day","dusk","night","dawn").contains(timeOfDay))
            throw new IllegalArgumentException("Invalid map weather/timeOfDay: "+id);
        if(!previewImage.isEmpty()&&!MapPreviewFiles.validName(previewImage))throw new IllegalArgumentException("Invalid preview image filename: "+previewImage);
        if (id == null || !id.matches("[a-z0-9_-]{1,48}") || name == null || name.isBlank() || name.length() > 80
                || description == null || description.length() > 1000 || timeLimitSeconds < 0 || timeLimitSeconds > 86400
                || !java.util.Set.of("survival", "adventure", "creative").contains(gameMode == null ? "" : gameMode)
                || dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || alphaSpawn == null || bravoSpawn == null || spectatorSpawn == null || alphaArea == null || bravoArea == null)
            throw new IllegalArgumentException("Invalid map metadata: " + id);
        alphaSpawn.validate(); bravoSpawn.validate(); spectatorSpawn.validate();
        alphaArea.validateRoom(alphaSpawn); bravoArea.validateRoom(bravoSpawn);
        if (capture != null) capture.validate();
        if(bomb!=null){bomb.validate();if(capture!=null||normandy!=null||timeLimitSeconds<1)throw new IllegalArgumentException("Bomb mode cannot have capture/Normandy rules");}
        if (normandy != null) {
            normandy.validate();
            if (capture != null || timeLimitSeconds == 0) throw new IllegalArgumentException("Normandy requires a time limit and no capture rules");
        }
        for (var hooks : java.util.Arrays.asList(onPrepare, onStart, onEnd)) if (hooks != null) {
            if (hooks.size() > 16) throw new IllegalArgumentException("Too many map functions");
            for (String function : hooks) if (function == null || !function.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid function id");
        }
        return this;
    }
    public static ArenaMap example() {
        return new ArenaMap("example", "Example Arena", "Edit spawn positions and set enabled to true before use.", false,
                600, "adventure", "minecraft:overworld", new Spawn(0.5, 65, 0.5, 0, 0),
                new Spawn(40.5, 65, 0.5, 180, 0), new Spawn(20.5, 80, 0.5, 0, 30),
                new Area(-5, 64, -5, 5, 72, 5), new Area(35, 64, -5, 45, 72, 5),
                new Capture("중앙 거점", new Area(17,64,-3,24,70,4),100,2,30), java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
}
