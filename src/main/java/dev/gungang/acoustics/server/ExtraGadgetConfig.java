package dev.gungang.acoustics.server;

import java.io.IOException;

/** Configurations for equipment whose original implementations used fixed constants. */
public final class ExtraGadgetConfig {
    public record M95(double damage,double headshotMultiplier,int magazine,double rechargePerSecond,double maximumCharge,
                      double smallThreshold,int smallBlocks,double largeThreshold,int largeBlocks,double thirdThreshold,int thirdBlocks,double unlimitedThreshold,double hitscanRange,double scopeRevealSeconds) { }
    public static M95 m95=new M95(51,1.33,3,15,200,10,1,50,3,100,10,150,256,20);
    public record ArrowGrenade(double damage,double projectileRange,double durationSeconds,double cooldownSeconds,int emissionIntervalTicks,double projectileSpeed,double throwSpeed) { }
    public static ArrowGrenade arrowGrenade=new ArrowGrenade(16,6,12,30,20,1.2,1.1);
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
    public record HandAxe(double throwDamage,double minThrowDistance,double maxThrowDistance,double chargeSeconds,double cooldownSeconds,double arcDegrees) { }
    /** Dash distance in blocks over the fixed six-tick dash, and the energy (out of 100) one dash spends. */
    /** tierNDamage: thrust damage at combo tier N (the first hit is tier 1). */
    public record Rapier(double dashDistance,int energyCost,double attackIntervalSeconds,double tier1Damage,double tier2Damage,double tier3Damage,double tier4Damage,double tier5Damage) {
        public Rapier(double dashDistance,int energyCost){this(dashDistance,energyCost,.6);}
        public Rapier(double dashDistance,int energyCost,double attackIntervalSeconds){this(dashDistance,energyCost,attackIntervalSeconds,27,33,40,48,57);}
        public double damage(int tier){return switch(tier){case 2->tier2Damage;case 3->tier3Damage;case 4->tier4Damage;case 5->tier5Damage;default->tier1Damage;};}
    }
    public static Rapier rapier=new Rapier(10,33,.6);
    /** 29 katana right-click spin damage and the bleed its hits leave (damage per second over bleedSeconds). */
    public record Katana(double rightClickDamage,double bleedDamagePerSecond,double bleedSeconds) { }
    public static Katana katana=new Katana(40,7,5);
    /**
     * Out-of-combat recovery, starting delaySeconds after the last damage: every intervalSeconds the share of
     * percentPerSecond for that interval (2 %/s every 0.5 s: 1 % each time).
     */
    public record Regeneration(double percentPerSecond,double delaySeconds,double intervalSeconds) {
        public Regeneration(double percentPerSecond,double delaySeconds){this(percentPerSecond,delaySeconds,.5);}
    }
    public static Regeneration regeneration=new Regeneration(2,5,.5);
    /** Global combat rules: explosion damage to players and bots is multiplied by explosionPlayerMultiplier. */
    public record Combat(double explosionPlayerMultiplier) { }
    public static Combat combat=new Combat(2);
    /**
     * 30 GP-31 Ultimatum fallout: a green cloud of {@code radius} blocks left for durationSeconds where the shell
     * bursts. A body inside any cloud takes startDamage, and every intervalSeconds it is still inside one the next hit
     * grows by damageStep, up to maxDamage.
     */
    public record Fallout(double radius,double durationSeconds,double startDamage,double damageStep,double maxDamage,double intervalSeconds) {
        public double damage(int hits){return Math.min(maxDamage,startDamage+damageStep*hits);}
    }
    public static Fallout fallout=new Fallout(4,3,16,3,31,.5);
    /** 31 twin blades: blunt damage of each of the six attack motions, reach (blocks) and cone widths (degrees). */
    public record TwinBlades(double motion1Damage,double motion2Damage,double motion3Damage,double motion4Damage,double motion5Damage,double motion6Damage,
                             double range,double slashAngle,double thrustAngle,double guardMovementSpeed,double guardReductionPercent,double guardAngle) {
        public TwinBlades(double m1,double m2,double m3,double m4,double m5,double m6,double range,double slashAngle,double thrustAngle,double guardMovementSpeed){
            this(m1,m2,m3,m4,m5,m6,range,slashAngle,thrustAngle,guardMovementSpeed,90,120);
        }
        public double damage(int motion){return switch(motion){case 1->motion1Damage;case 2->motion2Damage;case 3->motion3Damage;case 4->motion4Damage;case 5->motion5Damage;default->motion6Damage;};}
    }
    /** 32 ARC-12: damage of a shot to each body its pellets reach, the arc to everyone within arcRadius of them, and the glow. */
    public record ArcThrower(double damage,double arcDamage,double arcRadius,double glowSeconds) { }
    public static ArcThrower arcThrower=new ArcThrower(36,24,3,1);
    public static TwinBlades twinBlades=new TwinBlades(24,24,24,24,48,72,4,100,30,100);
    public static Molotov molotov=new Molotov(5,8,3,36);
    public static Orbital orbital=new Orbital(24,256,84,5,64,240);
    public static Life life=new Life(30,2.9,1,.1,120);
    public static Jet jet=new Jet(.85,3,25,1.5,3,1,24,false);
    public static Scanner scanner=new Scanner(10,120);
    public static Shield shield=new Shield(75,90);
    public static HandAxe handAxe=new HandAxe(66,5,24,1,16,0);
    public static int ticks(double seconds){return (int)Math.ceil(seconds*20);}
    public static void load() throws IOException {
        load(java.util.Map.of(),true);
    }
    static void load(java.util.Map<String,com.google.gson.JsonObject> overrides,boolean apply)throws IOException {
        var ag=GadgetConfig.read("arrow_grenade",ArrowGrenade.class,new ArrowGrenade(16,6,12,30,20,1.2,1.1),overrides,apply);
        if(!overrides.containsKey("arrow_grenade")&&ag.emissionIntervalTicks()==1) {
            ag=new ArrowGrenade(ag.damage(),ag.projectileRange(),ag.durationSeconds(),ag.cooldownSeconds(),20,ag.projectileSpeed(),ag.throwSpeed());
            if(apply)java.nio.file.Files.writeString(ServerFiles.root().resolve("item/arrow_grenade.json"),ServerFiles.GSON.toJson(ag));
        }
        GadgetConfig.bounded(ag.damage(),0,10000);GadgetConfig.bounded(ag.projectileRange(),.1,16);GadgetConfig.bounded(ag.durationSeconds(),.05,60);
        GadgetConfig.bounded(ag.cooldownSeconds(),0,86400);GadgetConfig.bounded(ag.emissionIntervalTicks(),1,1200);
        GadgetConfig.bounded(ag.projectileSpeed(),.5,4);GadgetConfig.bounded(ag.throwSpeed(),.1,3);
        var m=GadgetConfig.read("molotov",Molotov.class,new Molotov(5,8,3,36),overrides,apply);
        var o=GadgetConfig.read("orbital_strike",Orbital.class,new Orbital(24,256,84,5,64,240),overrides,apply);
        var l=GadgetConfig.read("life_support",Life.class,new Life(30,2.9,1,.1,120),overrides,apply);
        var j=GadgetConfig.read("jetpack",Jet.class,new Jet(.85,3,25,1.5,3,1,24,false),overrides,apply);
        var s=GadgetConfig.read("infrared_scanner",Scanner.class,new Scanner(10,120),overrides,apply);
        var h=GadgetConfig.read("shield",Shield.class,new Shield(75,90),overrides,apply);
        var x=GadgetConfig.read("hand_axe",HandAxe.class,new HandAxe(66,5,24,1,16,0),overrides,apply);
        GadgetConfig.bounded(x.throwDamage(),0,10000);
        GadgetConfig.bounded(x.minThrowDistance(),1,64);GadgetConfig.bounded(x.maxThrowDistance(),x.minThrowDistance(),64);GadgetConfig.bounded(x.chargeSeconds(),.05,30);GadgetConfig.bounded(x.arcDegrees(),0,80);
        var r=GadgetConfig.read("rapier",Rapier.class,new Rapier(10,33,.6),overrides,apply);
        GadgetConfig.bounded(r.dashDistance(),1,24);GadgetConfig.bounded(r.energyCost(),1,100);GadgetConfig.bounded(r.attackIntervalSeconds(),.05,10);
        for(int i=1;i<=5;i++)GadgetConfig.bounded(r.damage(i),0,10000);
        var k=GadgetConfig.read("katana",Katana.class,new Katana(40,7,5),overrides,apply);
        var rg=GadgetConfig.read("health_regeneration",Regeneration.class,new Regeneration(2,5,.5),overrides,apply);
        GadgetConfig.bounded(rg.percentPerSecond(),0,100);GadgetConfig.bounded(rg.delaySeconds(),0,300);GadgetConfig.bounded(rg.intervalSeconds(),.05,60);
        var cb=GadgetConfig.read("combat",Combat.class,new Combat(2),overrides,apply);
        GadgetConfig.bounded(cb.explosionPlayerMultiplier(),0,100);
        var fo=GadgetConfig.read("fallout",Fallout.class,new Fallout(4,3,16,3,31,.5),overrides,apply);
        GadgetConfig.bounded(fo.radius(),.1,32);GadgetConfig.bounded(fo.durationSeconds(),.05,300);GadgetConfig.bounded(fo.intervalSeconds(),.05,60);
        for(double d:new double[]{fo.startDamage(),fo.damageStep(),fo.maxDamage()})GadgetConfig.bounded(d,0,10000);
        var ac=GadgetConfig.read("arc_thrower",ArcThrower.class,new ArcThrower(36,24,3,1),overrides,apply);
        var sniper=GadgetConfig.read("m95",M95.class,new M95(51,1.33,3,15,200,10,1,50,3,100,10,150,256,20),overrides,apply);
        var penetrationMigration=ServerFiles.root().resolve("item/.m95-penetration10-v3");
        if(!overrides.containsKey("m95")&&!java.nio.file.Files.exists(penetrationMigration)&&sniper.maximumCharge()==240&&sniper.smallThreshold()==20&&sniper.unlimitedThreshold()==100){
            sniper=new M95(sniper.damage(),1.33,sniper.magazine(),15,200,10,1,50,3,100,10,150,sniper.hitscanRange(),sniper.scopeRevealSeconds());
            if(apply)java.nio.file.Files.writeString(ServerFiles.root().resolve("item/m95.json"),ServerFiles.GSON.toJson(sniper));
        }
        if(!overrides.containsKey("m95")&&!java.nio.file.Files.exists(penetrationMigration)&&sniper.thirdThreshold()==100&&(sniper.thirdBlocks()==3||sniper.thirdBlocks()==6)){
            sniper=new M95(sniper.damage(),sniper.headshotMultiplier(),sniper.magazine(),sniper.rechargePerSecond(),sniper.maximumCharge(),sniper.smallThreshold(),sniper.smallBlocks(),sniper.largeThreshold(),sniper.largeBlocks(),sniper.thirdThreshold(),10,sniper.unlimitedThreshold(),sniper.hitscanRange(),sniper.scopeRevealSeconds());
            if(apply)java.nio.file.Files.writeString(ServerFiles.root().resolve("item/m95.json"),ServerFiles.GSON.toJson(sniper));
        }
        if(apply&&!overrides.containsKey("m95")&&!java.nio.file.Files.exists(penetrationMigration))java.nio.file.Files.writeString(penetrationMigration,"M95 penetration defaults migrated.\n");
        var scopeMigration=ServerFiles.root().resolve("item/.m95-scope20-v2");
        if(!overrides.containsKey("m95")&&!java.nio.file.Files.exists(scopeMigration)){
            if(sniper.scopeRevealSeconds()==5){
                sniper=new M95(sniper.damage(),sniper.headshotMultiplier(),sniper.magazine(),sniper.rechargePerSecond(),sniper.maximumCharge(),sniper.smallThreshold(),sniper.smallBlocks(),sniper.largeThreshold(),sniper.largeBlocks(),sniper.thirdThreshold(),sniper.thirdBlocks(),sniper.unlimitedThreshold(),sniper.hitscanRange(),20);
                if(apply)java.nio.file.Files.writeString(ServerFiles.root().resolve("item/m95.json"),ServerFiles.GSON.toJson(sniper));
            }
            if(apply)java.nio.file.Files.writeString(scopeMigration,"M95 default scope delay upgraded to 20 seconds.\n");
        }
        GadgetConfig.bounded(sniper.damage(),0,10000);GadgetConfig.bounded(sniper.headshotMultiplier(),0,10000);
        GadgetConfig.bounded(sniper.magazine(),1,1000);GadgetConfig.bounded(sniper.rechargePerSecond(),0,10000);
        GadgetConfig.bounded(sniper.maximumCharge(),1,10000);GadgetConfig.bounded(sniper.smallThreshold(),1,sniper.maximumCharge());
        GadgetConfig.bounded(sniper.largeThreshold(),sniper.smallThreshold(),sniper.maximumCharge());
        GadgetConfig.bounded(sniper.thirdThreshold(),sniper.largeThreshold(),sniper.maximumCharge());
        GadgetConfig.bounded(sniper.thirdBlocks(),0,256);
        GadgetConfig.bounded(sniper.unlimitedThreshold(),sniper.thirdThreshold(),sniper.maximumCharge());
        GadgetConfig.bounded(sniper.smallBlocks(),0,256);GadgetConfig.bounded(sniper.largeBlocks(),sniper.smallBlocks(),256);
        GadgetConfig.bounded(sniper.hitscanRange(),1,512);GadgetConfig.bounded(sniper.scopeRevealSeconds(),0,300);
        if(apply)m95=sniper;
        GadgetConfig.bounded(ac.damage(),0,10000);GadgetConfig.bounded(ac.arcDamage(),0,10000);GadgetConfig.bounded(ac.arcRadius(),0,32);GadgetConfig.bounded(ac.glowSeconds(),0,300);
        var tb=GadgetConfig.read("twin_blades",TwinBlades.class,new TwinBlades(24,24,24,24,48,72,4,100,30,100),overrides,apply);
        for(int i=1;i<=6;i++)GadgetConfig.bounded(tb.damage(i),0,10000);
        GadgetConfig.bounded(tb.range(),.5,16);GadgetConfig.bounded(tb.slashAngle(),1,360);GadgetConfig.bounded(tb.thrustAngle(),1,360);GadgetConfig.bounded(tb.guardMovementSpeed(),1,500);
        GadgetConfig.bounded(tb.guardReductionPercent(),0,100);GadgetConfig.bounded(tb.guardAngle(),0,360);
        GadgetConfig.bounded(k.rightClickDamage(),0,10000);GadgetConfig.bounded(k.bleedDamagePerSecond(),0,10000);GadgetConfig.bounded(k.bleedSeconds(),0,60);
        GadgetConfig.bounded(m.radius(),.1,32);GadgetConfig.bounded(m.durationSeconds(),.05,300);GadgetConfig.bounded(m.burnSeconds(),0,300);
        GadgetConfig.bounded(m.burnDamage(),0,10000);GadgetConfig.bounded(j.burnDamage(),0,10000);
        GadgetConfig.bounded(o.radius(),.1,64);GadgetConfig.bounded(o.centerDamage(),0,10000);GadgetConfig.bounded(o.edgeDamage(),0,10000);GadgetConfig.bounded(o.delaySeconds(),.05,300);GadgetConfig.bounded(o.range(),1,128);
        GadgetConfig.bounded(l.absorption(),.01,1024);GadgetConfig.bounded(l.durationSeconds(),.05,300);GadgetConfig.bounded(l.drainAmount(),0,1024);GadgetConfig.bounded(l.drainIntervalSeconds(),.05,300);
        GadgetConfig.bounded(j.speed(),.01,3);GadgetConfig.bounded(j.durationSeconds(),.05,300);GadgetConfig.bounded(j.collisionDamage(),0,10000);GadgetConfig.bounded(j.knockback(),0,10);GadgetConfig.bounded(j.burnSeconds(),0,300);GadgetConfig.bounded(j.trailSeconds(),0,30);
        GadgetConfig.bounded(s.durationSeconds(),.05,300);GadgetConfig.bounded(h.capacity(),1,10000);GadgetConfig.bounded(h.repairSeconds(),0,86400);
        GadgetConfig.bounded(h.movementPenaltyPercent(),0,99);GadgetConfig.bounded(h.regenDelaySeconds(),0,300);GadgetConfig.bounded(h.regenPerSecond(),0,10000);GadgetConfig.bounded(h.meleeReductionPercent(),0,100);
        for(double c:new double[]{m.cooldownSeconds(),o.cooldownSeconds(),l.cooldownSeconds(),j.cooldownSeconds(),s.cooldownSeconds(),x.cooldownSeconds()})GadgetConfig.bounded(c,0,86400);
        if(apply){molotov=m;orbital=o;life=l;jet=j;scanner=s;shield=h;handAxe=x;arrowGrenade=ag;rapier=r;katana=k;regeneration=rg;combat=cb;fallout=fo;twinBlades=tb;arcThrower=ac;}
    }
    private ExtraGadgetConfig() { }
}
