package dev.gungang.acoustics.server;
import java.io.IOException;
import java.util.*;
public final class GimmickConfig {
    public record Blast(double damage,double radius,double respawnSeconds){}
    public record Wall(double health){}
    public record Puddle(double soundVolume,int soundIntervalTicks){}
    public record Acid(double poisonSeconds,int amplifier){}
    public record Nozzle(double height,int particlesPerSecond){}
    public static Blast uxo=new Blast(80,5,30),gas=new Blast(80,5,30);
    public static Puddle puddle=new Puddle(.7,6);
    public static Acid acid=new Acid(8,0);
    public static Nozzle nozzle=new Nozzle(3,40);
    public static Map<String,Wall> walls=defaults();
    public static Map<String,Wall> defaults(){
        var map=new LinkedHashMap<String,Wall>();map.put("cracked_oak_planks",new Wall(100));map.put("cracked_glass",new Wall(60));map.put("cracked_glass_pane",new Wall(40));
        for(var color:net.minecraft.world.item.DyeColor.values())map.put("cracked_"+color.getName()+"_concrete",new Wall(200));return map;
    }
    public static void load()throws IOException{
        var u=GadgetConfig.read("uxo",Blast.class,new Blast(80,5,30));var g=GadgetConfig.read("gas_barrel",Blast.class,new Blast(80,5,30));
        var p=GadgetConfig.read("puddle",Puddle.class,new Puddle(.7,6));var a=GadgetConfig.read("acid",Acid.class,new Acid(8,0));var n=GadgetConfig.read("nozzle",Nozzle.class,new Nozzle(3,40));
        for(var b:List.of(u,g)){GadgetConfig.bounded(b.damage(),0,10000);GadgetConfig.bounded(b.radius(),.1,32);GadgetConfig.bounded(b.respawnSeconds(),.05,86400);}
        GadgetConfig.bounded(p.soundVolume(),0,4);GadgetConfig.bounded(p.soundIntervalTicks(),1,100);
        GadgetConfig.bounded(a.poisonSeconds(),.05,300);GadgetConfig.bounded(a.amplifier(),0,4);GadgetConfig.bounded(n.height(),.1,16);GadgetConfig.bounded(n.particlesPerSecond(),1,200);
        var w=new LinkedHashMap<String,Wall>();for(var e:defaults().entrySet()){var value=GadgetConfig.read(e.getKey(),Wall.class,e.getValue());GadgetConfig.bounded(value.health(),1,10000);w.put(e.getKey(),value);}
        uxo=u;gas=g;puddle=p;acid=a;nozzle=n;walls=Map.copyOf(w);
    }
    private GimmickConfig(){}
}
