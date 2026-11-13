package dev.gungang.acoustics.server;

import java.io.IOException;

/** Configurations for equipment whose original implementations used fixed constants. */
public final class ExtraGadgetConfig {
    public record M95(double damage,double headshotMultiplier,int magazine,double rechargePerSecond,double maximumCharge,
                      double smallThreshold,int smallBlocks,double largeThreshold,int largeBlocks,double thirdThreshold,int thirdBlocks,double unlimitedThreshold,double hitscanRange,double scopeRevealSeconds,
                      double smallHitPadding,double largeHitPadding,double thirdHitPadding,double unlimitedHitPadding,
                      boolean smallDamageFalloff,boolean largeDamageFalloff,boolean thirdDamageFalloff,boolean unlimitedDamageFalloff,double piercingDamage,double normalHitCharge) {
        public M95(double damage,double headshotMultiplier,int magazine,double rechargePerSecond,double maximumCharge,double smallThreshold,int smallBlocks,double largeThreshold,int largeBlocks,double thirdThreshold,int thirdBlocks,double unlimitedThreshold,double hitscanRange,double scopeRevealSeconds,double smallHitPadding,double largeHitPadding,double thirdHitPadding,double unlimitedHitPadding){
            this(damage,headshotMultiplier,magazine,rechargePerSecond,maximumCharge,smallThreshold,smallBlocks,largeThreshold,largeBlocks,thirdThreshold,thirdBlocks,unlimitedThreshold,hitscanRange,scopeRevealSeconds,smallHitPadding,largeHitPadding,thirdHitPadding,unlimitedHitPadding,true,true,false,false,damage,50);
        }
        public M95(double damage,double headshotMultiplier,int magazine,double rechargePerSecond,double maximumCharge,double smallThreshold,int smallBlocks,double largeThreshold,int largeBlocks,double thirdThreshold,int thirdBlocks,double unlimitedThreshold,double hitscanRange,double scopeRevealSeconds){
            this(damage,headshotMultiplier,magazine,rechargePerSecond,maximumCharge,smallThreshold,smallBlocks,largeThreshold,largeBlocks,thirdThreshold,thirdBlocks,unlimitedThreshold,hitscanRange,scopeRevealSeconds,0,.4,.4,.4);
        }
        public M95 withScopeDelay(double seconds){return new M95(damage,headshotMultiplier,magazine,rechargePerSecond,maximumCharge,smallThreshold,smallBlocks,largeThreshold,largeBlocks,thirdThreshold,thirdBlocks,unlimitedThreshold,hitscanRange,seconds,smallHitPadding,largeHitPadding,thirdHitPadding,unlimitedHitPadding,smallDamageFalloff,largeDamageFalloff,thirdDamageFalloff,unlimitedDamageFalloff,piercingDamage,normalHitCharge);}
    }
    public static final M95 M95_DEFAULTS=new M95(37,1.33,3,10,200,10,1,75,2,125,4,200,256,5,0,.4,.4,.4,true,true,false,false,33,50);
    public static M95 m95=M95_DEFAULTS;
    public record AirAssault(double centerSize,double centerDamage,double outerSize,double outerDamage,double shockwaveSize,double shockwaveDamage,
                             double hoverSeconds,double diveRange,double diveSeconds,double airborneReductionPercent,double hitSpeedBonusPercent,double hitSpeedSeconds) { }
    public static final AirAssault AIR_ASSAULT_DEFAULTS=new AirAssault(2,60,4,45,12,10,1,15,.3,15,15,1.25);
    public static AirAssault airAssault=AIR_ASSAULT_DEFAULTS;
    public record VoidAnchor(double health,double pullRange,int maxTargets,double pullBlocksPerSecond,double damage) { }
    public static final VoidAnchor VOID_ANCHOR_DEFAULTS=new VoidAnchor(60,10,1,5,20);
    public static VoidAnchor voidAnchor=VOID_ANCHOR_DEFAULTS;
    public record CommandFlag(double health,double cooldownSeconds,double placementRange,
                              double baseRadius,double baseHealPerSecond,double healingUpgradeSeconds,
                              double upgradedHealPerSecond,double healingRadius,double defenseUpgradeSeconds,
                              double defenseRadius,double damageReductionPercent,double globalUpgradeSeconds) { }
    public static final CommandFlag COMMAND_FLAG_DEFAULTS=new CommandFlag(100,75,5,10,1,20,5,20,60,40,15,180);
    public static CommandFlag commandFlag=COMMAND_FLAG_DEFAULTS;
    public record ArrowGrenade(double damage,double projectileRange,double durationSeconds,double cooldownSeconds,double emissionIntervalSeconds,double projectileSpeed,double throwSpeed) {
        public int emissionIntervalTicks(){return Math.max(1,ticks(emissionIntervalSeconds));}
    }
    public static ArrowGrenade arrowGrenade=new ArrowGrenade(16,6,12,30,1,1.2,1.1);
    public record Molotov(double radius,double durationSeconds,int burnSeconds,double cooldownSeconds,double burnDamage) {
        public Molotov(double radius,double duration,int burn,double cooldown){this(radius,duration,burn,cooldown,1);}
    }
    public record Orbital(double radius,double centerDamage,double edgeDamage,double delaySeconds,double range,double cooldownSeconds) { }
    /**
     * 73 경계 감시 포격: red smoke over a radius-{@code radius} area, then after {@code delaySeconds} a
     * {@code durationSeconds} barrage of {@code volleySize} missiles every {@code volleyIntervalSeconds}, each aimed at
     * the most crowded spots in the area. A missile falls for {@code flightSeconds} through any roof and deals
     * {@code damage} within {@code blastRadius} to everyone (owner and allies too) not behind cover.
     */
    public record Barrage(double radius,double delaySeconds,double durationSeconds,int volleySize,double volleyIntervalSeconds,
                          double damage,double blastRadius,double flightSeconds,double range,double cooldownSeconds) {
        /** Volleys in one barrage (8 s / 1 s = 8, so 8 × 3 = 24 missiles by default). */
        public int volleys(){return Math.max(1,(int)Math.round(durationSeconds/volleyIntervalSeconds));}
    }
    public static final Barrage BARRAGE_DEFAULTS=new Barrage(32,3,8,3,1,32,4,.5,64,210);
    public static Barrage barrage=BARRAGE_DEFAULTS;
    public record Life(double absorption,double durationSeconds,double drainAmount,double drainIntervalSeconds,double cooldownSeconds) { }
    public record Jet(double speed,double durationSeconds,double collisionDamage,double knockback,int burnSeconds,double trailSeconds,double cooldownSeconds,boolean verticalFlight,double burnDamage) {
        public Jet(double speed,double duration,double damage,double knockback,int burn,double trail,double cooldown,boolean vertical){this(speed,duration,damage,knockback,burn,trail,cooldown,vertical,1);}
    }
    /**
     * 74 Hermes' Boots: a fixed-step forward dash of dashDistance blocks over dashSeconds. Up to maxCharges dashes are
     * stored; each spent one comes back chargeSeconds later (one at a time). trailSeconds: how long the glow lingers.
     */
    public record Hermes(double dashDistance,double dashSeconds,int maxCharges,double chargeSeconds,double trailSeconds) {
        public int dashTicks(){return Math.max(1,(int)Math.round(dashSeconds*20));}
    }
    public static final Hermes HERMES_DEFAULTS=new Hermes(4,.2,2,6,.35);
    public static Hermes hermes=HERMES_DEFAULTS;
    public record Scanner(double durationSeconds,double cooldownSeconds) { }
    public record Shield(double capacity,double repairSeconds,double movementPenaltyPercent,boolean outOfCombatRegen,double regenDelaySeconds,double regenPerSecond,double meleeReductionPercent) {
        public Shield(double capacity,double repair){this(capacity,repair,15);}
        public Shield(double capacity,double repair,double penalty){this(capacity,repair,penalty,true,5,5,20);}
    }
    /** Thrown distance on flat ground grows from min to max while use is held for chargeSeconds. */
    public record HandAxe(double throwDamage,double minThrowDistance,double maxThrowDistance,double chargeSeconds,double cooldownSeconds,double arcDegrees) { }
    /** Dash distance in blocks over the fixed six-tick dash, and the energy (out of 100) one dash spends. */
    /** tierNDamage: thrust damage at combo tier N (the first hit is tier 1). */
    public record Rapier(double dashDistance,int energyCost,double attackIntervalSeconds,double tier1Damage,double tier2Damage,double tier3Damage,double tier4Damage,double tier5Damage,double energyRegenPerSecond,double energyRegenDelaySeconds,int killEnergyGain) {
        public Rapier(double distance,int cost,double interval,double d1,double d2,double d3,double d4,double d5){this(distance,cost,interval,d1,d2,d3,d4,d5,10,0,20);}
        public Rapier(double dashDistance,int energyCost){this(dashDistance,energyCost,.6);}
        public Rapier(double dashDistance,int energyCost,double attackIntervalSeconds){this(dashDistance,energyCost,attackIntervalSeconds,27,33,40,48,57);}
        public double damage(int tier){return switch(tier){case 2->tier2Damage;case 3->tier3Damage;case 4->tier4Damage;case 5->tier5Damage;default->tier1Damage;};}
    }
    public static Rapier rapier=new Rapier(10,50,.6);
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
    /** Global explosion multiplier: all TaCZ targets; players/bots for other explosions. The legacy JSON key is retained. */
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
    /**
     * 35 classic blower: windRange limits the left-click wind's push; right click fires an air cannon
     * ({@link dev.gungang.acoustics.server.WindBlower}).
     */
    public record WindBlower(double windRange,int cannonAmmoCost,double cannonDamage,double cannonKnockbackMultiplier,double cannonSelfKnockback,
                             double cannonRange,double cannonRadius,double cannonCooldownSeconds) { }
    public static WindBlower windBlower=new WindBlower(7,30,10,3,1,8,1.5,1);
    /**
     * 33 shuriken ({@link ShurikenItem}): every throw spends ammoPerThrow of the capacity-round magazine. Semi throws
     * three in a row along the aim (semiIntervalTicks apart), burst three at once fanned burstAngle degrees apart, and
     * ricochet one that bounces on to the nearest enemy within ricochetRadius of each body it hits (up to three bodies).
     * No range falloff; projectiles fly projectileSpeed blocks per tick for at most flightSeconds.
     */
    public record Shuriken(int capacity,int ammoPerThrow,double reloadSeconds,double attackIntervalSeconds,double projectileSpeed,double spreadDegrees,double flightSeconds,
                           double semiDamage,int semiIntervalTicks,double burstDamage,double burstAngle,
                           double ricochetDamage1,double ricochetDamage2,double ricochetDamage3,double ricochetRadius,double doubleJumpVelocity,double wallClimbVelocity) {
        public double ricochetDamage(int hop){return switch(hop){case 1->ricochetDamage1;case 2->ricochetDamage2;default->ricochetDamage3;};}
    }
    public static final Shuriken SHURIKEN_DEFAULTS=new Shuriken(18,3,1.5,.5,3,1.5,5,13,2,13,8,33,22,11,16,.5,.42);
    public static Shuriken shuriken=SHURIKEN_DEFAULTS;
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
        var flag=GadgetConfig.read("command_flag",CommandFlag.class,COMMAND_FLAG_DEFAULTS,overrides,apply);
        GadgetConfig.bounded(flag.health(),1,1024);GadgetConfig.bounded(flag.cooldownSeconds(),0,86400);
        GadgetConfig.bounded(flag.placementRange(),1,8);
        for(double radius:new double[]{flag.baseRadius(),flag.healingRadius(),flag.defenseRadius()})GadgetConfig.bounded(radius,.1,128);
        for(double heal:new double[]{flag.baseHealPerSecond(),flag.upgradedHealPerSecond()})GadgetConfig.bounded(heal,0,10000);
        GadgetConfig.bounded(flag.healingUpgradeSeconds(),0,86400);
        GadgetConfig.bounded(flag.defenseUpgradeSeconds(),flag.healingUpgradeSeconds(),86400);
        GadgetConfig.bounded(flag.globalUpgradeSeconds(),flag.defenseUpgradeSeconds(),86400);
        GadgetConfig.bounded(flag.damageReductionPercent(),0,100);
        if(apply)commandFlag=flag;
        var assault=GadgetConfig.read("air_assault",AirAssault.class,AIR_ASSAULT_DEFAULTS,overrides,apply);
        GadgetConfig.bounded(assault.centerSize(),.1,64);GadgetConfig.bounded(assault.outerSize(),assault.centerSize(),64);GadgetConfig.bounded(assault.shockwaveSize(),assault.outerSize(),64);
        for(double d:new double[]{assault.centerDamage(),assault.outerDamage(),assault.shockwaveDamage()})GadgetConfig.bounded(d,0,10000);
        GadgetConfig.bounded(assault.hoverSeconds(),.05,30);GadgetConfig.bounded(assault.diveRange(),1,64);GadgetConfig.bounded(assault.diveSeconds(),.05,10);
        GadgetConfig.bounded(assault.airborneReductionPercent(),0,100);GadgetConfig.bounded(assault.hitSpeedBonusPercent(),0,500);GadgetConfig.bounded(assault.hitSpeedSeconds(),0,60);
        var anchor=GadgetConfig.read("void_anchor",VoidAnchor.class,VOID_ANCHOR_DEFAULTS,overrides,apply);
        GadgetConfig.bounded(anchor.health(),1,1024);GadgetConfig.bounded(anchor.pullRange(),.1,64);GadgetConfig.bounded(anchor.maxTargets(),1,32);
        GadgetConfig.bounded(anchor.pullBlocksPerSecond(),.1,40);GadgetConfig.bounded(anchor.damage(),0,10000);
        if(apply){airAssault=assault;voidAnchor=anchor;}
        var ag=GadgetConfig.read("arrow_grenade",ArrowGrenade.class,new ArrowGrenade(16,6,12,30,1,1.2,1.1),overrides,apply);
        GadgetConfig.bounded(ag.damage(),0,10000);GadgetConfig.bounded(ag.projectileRange(),.1,16);GadgetConfig.bounded(ag.durationSeconds(),.05,60);
        GadgetConfig.bounded(ag.cooldownSeconds(),0,86400);GadgetConfig.bounded(ag.emissionIntervalSeconds(),.05,60);
        GadgetConfig.bounded(ag.projectileSpeed(),.5,4);GadgetConfig.bounded(ag.throwSpeed(),.1,3);
        var m=GadgetConfig.read("molotov",Molotov.class,new Molotov(5,8,3,36),overrides,apply);
        var o=GadgetConfig.read("orbital_strike",Orbital.class,new Orbital(24,256,84,5,64,240),overrides,apply);
        var pb=GadgetConfig.read("perimeter_barrage",Barrage.class,BARRAGE_DEFAULTS,overrides,apply);
        GadgetConfig.bounded(pb.radius(),1,64);GadgetConfig.bounded(pb.delaySeconds(),0,60);GadgetConfig.bounded(pb.durationSeconds(),.05,120);
        GadgetConfig.bounded(pb.volleySize(),1,16);GadgetConfig.bounded(pb.volleyIntervalSeconds(),.1,30);GadgetConfig.bounded(pb.damage(),0,10000);
        GadgetConfig.bounded(pb.blastRadius(),.5,16);GadgetConfig.bounded(pb.flightSeconds(),.05,5);GadgetConfig.bounded(pb.range(),1,128);GadgetConfig.bounded(pb.cooldownSeconds(),0,86400);
        if(pb.volleys()*pb.volleySize()>400)throw new IllegalArgumentException("경계 감시 포격 미사일 수가 너무 많습니다 (최대 400).");
        var l=GadgetConfig.read("life_support",Life.class,new Life(30,2.9,1,.1,120),overrides,apply);
        var j=GadgetConfig.read("jetpack",Jet.class,new Jet(.85,3,25,1.5,3,1,24,false),overrides,apply);
        var hb=GadgetConfig.read("hermes_boots",Hermes.class,HERMES_DEFAULTS,overrides,apply);
        GadgetConfig.bounded(hb.dashDistance(),.5,32);GadgetConfig.bounded(hb.dashSeconds(),.05,2);GadgetConfig.bounded(hb.maxCharges(),1,10);
        GadgetConfig.bounded(hb.chargeSeconds(),0,86400);GadgetConfig.bounded(hb.trailSeconds(),0,5);
        var s=GadgetConfig.read("infrared_scanner",Scanner.class,new Scanner(10,120),overrides,apply);
        var h=GadgetConfig.read("shield",Shield.class,new Shield(75,90),overrides,apply);
        var x=GadgetConfig.read("hand_axe",HandAxe.class,new HandAxe(66,5,24,1,16,0),overrides,apply);
        GadgetConfig.bounded(x.throwDamage(),0,10000);
        GadgetConfig.bounded(x.minThrowDistance(),1,64);GadgetConfig.bounded(x.maxThrowDistance(),x.minThrowDistance(),64);GadgetConfig.bounded(x.chargeSeconds(),.05,30);GadgetConfig.bounded(x.arcDegrees(),0,80);
        var r=GadgetConfig.read("rapier",Rapier.class,new Rapier(10,50,.6),overrides,apply);
        var rapierMigration=ServerFiles.root().resolve("item/.rapier-energy-v2");
        if(!overrides.containsKey("rapier")&&!java.nio.file.Files.exists(rapierMigration)){
            r=new Rapier(r.dashDistance(),50,r.attackIntervalSeconds(),r.tier1Damage(),r.tier2Damage(),r.tier3Damage(),r.tier4Damage(),r.tier5Damage(),10,0,20);
            if(apply){java.nio.file.Files.writeString(ServerFiles.root().resolve("item/rapier.json"),ServerFiles.GSON.toJson(r));java.nio.file.Files.writeString(rapierMigration,"Rapier energy defaults updated.");}
        }
        GadgetConfig.bounded(r.energyRegenPerSecond(),0,100);GadgetConfig.bounded(r.energyRegenDelaySeconds(),0,60);GadgetConfig.bounded(r.killEnergyGain(),0,100);
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
        var sniper=GadgetConfig.read("m95",M95.class,M95_DEFAULTS,overrides,apply);
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
        var scopeMigration=ServerFiles.root().resolve("item/.m95-scope10-pulse-v4");
        if(!overrides.containsKey("m95")&&!java.nio.file.Files.exists(scopeMigration)){
            if(sniper.scopeRevealSeconds()==5||sniper.scopeRevealSeconds()==20){
                sniper=sniper.withScopeDelay(10);
                if(apply)java.nio.file.Files.writeString(ServerFiles.root().resolve("item/m95.json"),ServerFiles.GSON.toJson(sniper));
            }
            if(apply)java.nio.file.Files.writeString(scopeMigration,"M95 default scope delay upgraded to 10 seconds.\n");
        }
        var balanceMigration=ServerFiles.root().resolve("item/.m95-balance-37-33-v5");
        if(!overrides.containsKey("m95")&&!java.nio.file.Files.exists(balanceMigration)){
            sniper=new M95(37,sniper.headshotMultiplier(),sniper.magazine(),10,Math.max(200,sniper.maximumCharge()),10,1,75,2,125,4,200,sniper.hitscanRange(),5,0,.4,.4,.4,true,true,false,false,33,sniper.normalHitCharge());
            if(apply){java.nio.file.Files.writeString(ServerFiles.root().resolve("item/m95.json"),ServerFiles.GSON.toJson(sniper));java.nio.file.Files.writeString(balanceMigration,"M95 37/33 balance applied once. Further modify edits are preserved.\n");}
        }
        GadgetConfig.bounded(sniper.damage(),0,10000);GadgetConfig.bounded(sniper.headshotMultiplier(),0,10000);
        GadgetConfig.bounded(sniper.piercingDamage(),0,10000);
        GadgetConfig.bounded(sniper.normalHitCharge(),0,10000);
        GadgetConfig.bounded(sniper.magazine(),1,1000);GadgetConfig.bounded(sniper.rechargePerSecond(),0,10000);
        GadgetConfig.bounded(sniper.maximumCharge(),1,10000);GadgetConfig.bounded(sniper.smallThreshold(),1,sniper.maximumCharge());
        GadgetConfig.bounded(sniper.largeThreshold(),sniper.smallThreshold(),sniper.maximumCharge());
        GadgetConfig.bounded(sniper.thirdThreshold(),sniper.largeThreshold(),sniper.maximumCharge());
        GadgetConfig.bounded(sniper.thirdBlocks(),0,256);
        GadgetConfig.bounded(sniper.unlimitedThreshold(),sniper.thirdThreshold(),sniper.maximumCharge());
        GadgetConfig.bounded(sniper.smallBlocks(),0,256);GadgetConfig.bounded(sniper.largeBlocks(),sniper.smallBlocks(),256);
        GadgetConfig.bounded(sniper.hitscanRange(),1,512);GadgetConfig.bounded(sniper.scopeRevealSeconds(),0,300);
        for(double padding:new double[]{sniper.smallHitPadding(),sniper.largeHitPadding(),sniper.thirdHitPadding(),sniper.unlimitedHitPadding()})GadgetConfig.bounded(padding,0,4);
        if(apply)m95=sniper;
        GadgetConfig.bounded(ac.damage(),0,10000);GadgetConfig.bounded(ac.arcDamage(),0,10000);GadgetConfig.bounded(ac.arcRadius(),0,32);GadgetConfig.bounded(ac.glowSeconds(),0,300);
        var wb=GadgetConfig.read("wind_blower",WindBlower.class,new WindBlower(7,30,10,3,1,8,1.5,1),overrides,apply);
        GadgetConfig.bounded(wb.windRange(),0,256);GadgetConfig.bounded(wb.cannonAmmoCost(),0,10000);GadgetConfig.bounded(wb.cannonDamage(),0,10000);
        GadgetConfig.bounded(wb.cannonKnockbackMultiplier(),0,100);GadgetConfig.bounded(wb.cannonSelfKnockback(),0,10);GadgetConfig.bounded(wb.cannonRange(),.5,128);
        GadgetConfig.bounded(wb.cannonRadius(),0,32);GadgetConfig.bounded(wb.cannonCooldownSeconds(),0,86400);
        var sh=GadgetConfig.read("shuriken",Shuriken.class,SHURIKEN_DEFAULTS,overrides,apply);
        GadgetConfig.bounded(sh.capacity(),1,1000);GadgetConfig.bounded(sh.ammoPerThrow(),1,sh.capacity());GadgetConfig.bounded(sh.reloadSeconds(),0,60);
        GadgetConfig.bounded(sh.attackIntervalSeconds(),.05,10);GadgetConfig.bounded(sh.projectileSpeed(),.2,8);GadgetConfig.bounded(sh.spreadDegrees(),0,45);
        GadgetConfig.bounded(sh.flightSeconds(),.1,30);GadgetConfig.bounded(sh.semiIntervalTicks(),0,20);GadgetConfig.bounded(sh.burstAngle(),0,90);
        for(double d:new double[]{sh.semiDamage(),sh.burstDamage(),sh.ricochetDamage1(),sh.ricochetDamage2(),sh.ricochetDamage3()})GadgetConfig.bounded(d,0,10000);
        GadgetConfig.bounded(sh.ricochetRadius(),0,64);GadgetConfig.bounded(sh.doubleJumpVelocity(),0,3);GadgetConfig.bounded(sh.wallClimbVelocity(),0,3);
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
        if(apply){barrage=pb;molotov=m;orbital=o;life=l;jet=j;hermes=hb;scanner=s;shield=h;handAxe=x;arrowGrenade=ag;rapier=r;katana=k;regeneration=rg;combat=cb;fallout=fo;twinBlades=tb;arcThrower=ac;windBlower=wb;shuriken=sh;}
    }
    private ExtraGadgetConfig() { }
}
