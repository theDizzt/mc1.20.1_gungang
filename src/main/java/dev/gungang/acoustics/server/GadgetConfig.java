package dev.gungang.acoustics.server;
import java.nio.file.*;
import java.io.IOException;

public final class GadgetConfig {
    public record Mine(double damage,double radius,double triggerRadius,int maxPlaced,double disarmSeconds,double range,boolean showHealthBar) { }
    public record Recovery(double heal,double durationSeconds) { }
    public record Adrenaline(double healthCostPercent,double durationSeconds,double movementBonusPercent,double attackBonusPercent,double damageBonusPercent) {
        public Adrenaline(double cost,double duration,double movement,double attack) {this(cost,duration,movement,attack,20);}
    }
    public record Barrier(double health,double range) { }
    public record HealthPack(double heal,double cooldownSeconds) { }
    public static HealthPack smallHealthPack=new HealthPack(35,10),largeHealthPack=new HealthPack(70,15);
    public static Mine mine=new Mine(100,3,1.2,3,5,5,false);
    public static Recovery recovery=new Recovery(50,5);
    public static Adrenaline adrenaline=new Adrenaline(50,7,20,20);
    public static Barrier barrier=new Barrier(200,5);
    public static void load() throws IOException {
        ExtraGadgetConfig.load();
        GimmickConfig.load();
        Mine m=read("landmine",Mine.class,new Mine(100,3,1.2,3,5,5,false));
        Recovery r=read("recovery_potion",Recovery.class,new Recovery(50,5));
        Adrenaline a=read("adrenaline",Adrenaline.class,new Adrenaline(50,7,20,20));
        Barrier b=read("barrier",Barrier.class,new Barrier(200,5));
        HealthPack small=read("small_health_pack",HealthPack.class,new HealthPack(35,10));
        HealthPack large=read("large_health_pack",HealthPack.class,new HealthPack(70,15));
        for(var pack:java.util.List.of(small,large)){bounded(pack.heal(),0,10000);bounded(pack.cooldownSeconds(),0,86400);}
        bounded(m.damage(),0,10000);bounded(m.radius(),0.1,32);bounded(m.triggerRadius(),0.1,16);bounded(m.maxPlaced(),1,32);bounded(m.disarmSeconds(),0.1,60);bounded(m.range(),1,8);
        bounded(r.heal(),0,10000);bounded(r.durationSeconds(),0.05,300);
        bounded(a.healthCostPercent(),0,99);bounded(a.durationSeconds(),0.05,300);bounded(a.movementBonusPercent(),0,500);bounded(a.attackBonusPercent(),0,500);
        bounded(a.damageBonusPercent(),0,500);
        bounded(b.health(),1,1024);bounded(b.range(),1,8);
        mine=m;recovery=r;adrenaline=a;barrier=b;
        smallHealthPack=small;largeHealthPack=large;
    }
    static void bounded(double value,double min,double max) { if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("Invalid gadget value: "+value); }
    static <T> T read(String id,Class<T> type,T defaults) throws IOException {
        Path path=ServerFiles.root().resolve("item/"+id+".json");
        ConfigLayout.writeNew(path,ServerFiles.GSON.toJson(defaults));
        if(Files.size(path)>65536)throw new IOException("Item config too large: "+path);
        var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        var merged=ServerFiles.GSON.toJsonTree(defaults).getAsJsonObject();
        for(var e:json.entrySet()) { if(!merged.has(e.getKey()))throw new IllegalArgumentException("Unknown item option: "+e.getKey());merged.add(e.getKey(),e.getValue()); }
        return ServerFiles.GSON.fromJson(merged,type);
    }
    private GadgetConfig() { }
}
