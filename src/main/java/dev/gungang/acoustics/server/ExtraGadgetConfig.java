package dev.gungang.acoustics.server;

import java.io.IOException;

/** Configurations for equipment whose original implementations used fixed constants. */
public final class ExtraGadgetConfig {
    public record Molotov(double radius,double durationSeconds,int burnSeconds,double cooldownSeconds,double burnDamage) {
        public Molotov(double radius,double duration,int burn,double cooldown){this(radius,duration,burn,cooldown,1);}
    }
    public record Orbital(double radius,double centerDamage,double edgeDamage,double delaySeconds,double range,double cooldownSeconds) { }
    public record Life(double absorption,double durationSeconds,double drainAmount,double drainIntervalSeconds,double cooldownSeconds) { }
    public record Jet(double speed,double durationSeconds,double collisionDamage,double knockback,int burnSeconds,double trailSeconds,double cooldownSeconds,boolean verticalFlight,double burnDamage) {
        public Jet(double speed,double duration,double damage,double knockback,int burn,double trail,double cooldown,boolean vertical){this(speed,duration,damage,knockback,burn,trail,cooldown,vertical,1);}
    }
    public record Scanner(double durationSeconds,double cooldownSeconds) { }
    public record Shield(double capacity,double repairSeconds,double movementPenaltyPercent,boolean outOfCombatRegen,double regenDelaySeconds,double regenPerSecond,double meleeReductionPercent) {
        public Shield(double capacity,double repair){this(capacity,repair,15);}
        public Shield(double capacity,double repair,double penalty){this(capacity,repair,penalty,true,5,5,20);}
    }
    /** Thrown distance on flat ground grows from min to max while use is held for chargeSeconds. */
    public record HandAxe(double meleeDamage,double meleeCooldownSeconds,double throwDamage,double minThrowDistance,double maxThrowDistance,double chargeSeconds,double cooldownSeconds,double arcDegrees) { }
    public static Molotov molotov=new Molotov(5,8,3,36);
    public static Orbital orbital=new Orbital(24,256,84,5,64,240);
    public static Life life=new Life(30,2.9,1,.1,120);
    public static Jet jet=new Jet(.85,3,25,1.5,3,1,24,false);
    public static Scanner scanner=new Scanner(10,120);
    public static Shield shield=new Shield(75,90);
    public static HandAxe handAxe=new HandAxe(35,.6,66,5,24,1,16,0);
    public static int ticks(double seconds){return (int)Math.ceil(seconds*20);}
    public static void load() throws IOException {
        load(java.util.Map.of(),true);
    }
    static void load(java.util.Map<String,com.google.gson.JsonObject> overrides,boolean apply)throws IOException {
        var m=GadgetConfig.read("molotov",Molotov.class,new Molotov(5,8,3,36),overrides,apply);
        var o=GadgetConfig.read("orbital_strike",Orbital.class,new Orbital(24,256,84,5,64,240),overrides,apply);
        var l=GadgetConfig.read("life_support",Life.class,new Life(30,2.9,1,.1,120),overrides,apply);
        var j=GadgetConfig.read("jetpack",Jet.class,new Jet(.85,3,25,1.5,3,1,24,false),overrides,apply);
        var s=GadgetConfig.read("infrared_scanner",Scanner.class,new Scanner(10,120),overrides,apply);
        var h=GadgetConfig.read("shield",Shield.class,new Shield(75,90),overrides,apply);
        var x=GadgetConfig.read("hand_axe",HandAxe.class,new HandAxe(35,.6,66,5,24,1,16,0),overrides,apply);
        GadgetConfig.bounded(x.meleeDamage(),0,10000);GadgetConfig.bounded(x.meleeCooldownSeconds(),0,60);GadgetConfig.bounded(x.throwDamage(),0,10000);
        GadgetConfig.bounded(x.minThrowDistance(),1,64);GadgetConfig.bounded(x.maxThrowDistance(),x.minThrowDistance(),64);GadgetConfig.bounded(x.chargeSeconds(),.05,30);GadgetConfig.bounded(x.arcDegrees(),0,80);
        GadgetConfig.bounded(m.radius(),.1,32);GadgetConfig.bounded(m.durationSeconds(),.05,300);GadgetConfig.bounded(m.burnSeconds(),0,300);
        GadgetConfig.bounded(m.burnDamage(),0,10000);GadgetConfig.bounded(j.burnDamage(),0,10000);
        GadgetConfig.bounded(o.radius(),.1,64);GadgetConfig.bounded(o.centerDamage(),0,10000);GadgetConfig.bounded(o.edgeDamage(),0,10000);GadgetConfig.bounded(o.delaySeconds(),.05,300);GadgetConfig.bounded(o.range(),1,128);
        GadgetConfig.bounded(l.absorption(),.01,1024);GadgetConfig.bounded(l.durationSeconds(),.05,300);GadgetConfig.bounded(l.drainAmount(),0,1024);GadgetConfig.bounded(l.drainIntervalSeconds(),.05,300);
        GadgetConfig.bounded(j.speed(),.01,3);GadgetConfig.bounded(j.durationSeconds(),.05,300);GadgetConfig.bounded(j.collisionDamage(),0,10000);GadgetConfig.bounded(j.knockback(),0,10);GadgetConfig.bounded(j.burnSeconds(),0,300);GadgetConfig.bounded(j.trailSeconds(),0,30);
        GadgetConfig.bounded(s.durationSeconds(),.05,300);GadgetConfig.bounded(h.capacity(),1,10000);GadgetConfig.bounded(h.repairSeconds(),0,86400);
        GadgetConfig.bounded(h.movementPenaltyPercent(),0,99);GadgetConfig.bounded(h.regenDelaySeconds(),0,300);GadgetConfig.bounded(h.regenPerSecond(),0,10000);GadgetConfig.bounded(h.meleeReductionPercent(),0,100);
        for(double c:new double[]{m.cooldownSeconds(),o.cooldownSeconds(),l.cooldownSeconds(),j.cooldownSeconds(),s.cooldownSeconds(),x.cooldownSeconds()})GadgetConfig.bounded(c,0,86400);
        if(apply){molotov=m;orbital=o;life=l;jet=j;scanner=s;shield=h;handAxe=x;}
    }
    private ExtraGadgetConfig() { }
}
