package dev.gungang.acoustics.server;
import java.nio.file.*;
import java.io.IOException;

public final class GadgetConfig {
    public record Mine(double damage,double radius,double triggerRadius,int maxPlaced,double disarmSeconds,double range,boolean showHealthBar) { }
    public record Recovery(double heal,double durationSeconds) { }
    public record Adrenaline(double healthCostPercent,double durationSeconds,double movementBonusPercent,double attackBonusPercent,double damageBonusPercent) {
        public Adrenaline(double cost,double duration,double movement,double attack) {this(cost,duration,movement,attack,20);}
    }
    public record Barrier(double health,double range,int maxPlaced) {
        public Barrier(double health,double range){this(health,range,1);}
    }
    public record BearTrap(double rootSeconds,double recallSeconds,int maxPlaced,double range,double slowSeconds,int slowAmplifier) {
        public BearTrap(double root,double recall,int max,double range){this(root,recall,max,range,2,0);}
    }
    public record HealthPack(double heal,double cooldownSeconds) { }
    public static HealthPack smallHealthPack=new HealthPack(35,10),largeHealthPack=new HealthPack(70,15);
    public static Mine mine=new Mine(100,3,1.2,3,5,5,false);
    public static Recovery recovery=new Recovery(50,5);
    public static Adrenaline adrenaline=new Adrenaline(50,7,20,20);
    public static Barrier barrier=new Barrier(200,5);
    public static BearTrap bearTrap=new BearTrap(3,3,1,5);
    public static void load() throws IOException {
        load(java.util.Map.of(),true);
    }
    static void validate(String id,com.google.gson.JsonObject candidate)throws IOException {
        load(java.util.Map.of(id,candidate),false);
    }
    private static void load(java.util.Map<String,com.google.gson.JsonObject> overrides,boolean apply)throws IOException {
        ExtraGadgetConfig.load(overrides,apply);
        Turrets.load(overrides,apply);
        GimmickConfig.load(overrides,apply);
        Mine m=read("landmine",Mine.class,new Mine(100,3,1.2,3,5,5,false),overrides,apply);
        Recovery r=read("recovery_potion",Recovery.class,new Recovery(50,5),overrides,apply);
        Adrenaline a=read("adrenaline",Adrenaline.class,new Adrenaline(50,7,20,20),overrides,apply);
        Barrier b=read("barrier",Barrier.class,new Barrier(200,5),overrides,apply);
        BearTrap t=read("bear_trap",BearTrap.class,new BearTrap(3,3,1,5),overrides,apply);
        bounded(t.rootSeconds(),.05,60);bounded(t.slowSeconds(),0,60);bounded(t.slowAmplifier(),0,9);bounded(t.recallSeconds(),.05,60);bounded(t.maxPlaced(),1,32);bounded(t.range(),1,8);
        HealthPack small=read("small_health_pack",HealthPack.class,new HealthPack(35,10),overrides,apply);
        HealthPack large=read("large_health_pack",HealthPack.class,new HealthPack(70,15),overrides,apply);
        for(var pack:java.util.List.of(small,large)){bounded(pack.heal(),0,10000);bounded(pack.cooldownSeconds(),0,86400);}
        bounded(m.damage(),0,10000);bounded(m.radius(),0.1,32);bounded(m.triggerRadius(),0.1,16);bounded(m.maxPlaced(),1,32);bounded(m.disarmSeconds(),0.1,60);bounded(m.range(),1,8);
        bounded(r.heal(),0,10000);bounded(r.durationSeconds(),0.05,300);
        bounded(a.healthCostPercent(),0,99);bounded(a.durationSeconds(),0.05,300);bounded(a.movementBonusPercent(),0,500);bounded(a.attackBonusPercent(),0,500);
        bounded(a.damageBonusPercent(),0,500);
        bounded(b.health(),1,1024);bounded(b.range(),1,8);bounded(b.maxPlaced(),1,32);
        if(!apply)return;
        mine=m;recovery=r;adrenaline=a;barrier=b;bearTrap=t;
        smallHealthPack=small;largeHealthPack=large;
    }
    static void bounded(double value,double min,double max) { if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("Invalid gadget value: "+value); }
    static <T> T read(String id,Class<T> type,T defaults) throws IOException {
        return read(id,type,defaults,java.util.Map.of(),true);
    }
    static <T> T read(String id,Class<T> type,T defaults,java.util.Map<String,com.google.gson.JsonObject> overrides,boolean create) throws IOException {
        Path path=ServerFiles.root().resolve("item/"+id+".json");
        if(create)ConfigLayout.writeNew(path,ServerFiles.GSON.toJson(defaults));
        if(Files.exists(path)&&Files.size(path)>65536)throw new IOException("Item config too large: "+path);
        var json=overrides.containsKey(id)?overrides.get(id):Files.exists(path)?com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject():new com.google.gson.JsonObject();
        var merged=ServerFiles.GSON.toJsonTree(defaults).getAsJsonObject();
        for(var e:json.entrySet()) {
            // Pre-throw-only hand axes had these keys. Ignore them when upgrading existing servers.
            if(id.equals("hand_axe")&&java.util.Set.of("meleeDamage","meleeCooldownSeconds").contains(e.getKey()))continue;
            if(!merged.has(e.getKey()))throw new IllegalArgumentException("Unknown item option: "+e.getKey());merged.add(e.getKey(),e.getValue());
        }
        return ServerFiles.GSON.fromJson(merged,type);
    }
    private GadgetConfig() { }
}
