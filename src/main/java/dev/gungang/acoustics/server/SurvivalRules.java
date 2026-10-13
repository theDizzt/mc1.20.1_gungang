package dev.gungang.acoustics.server;

public final class SurvivalRules {
    public static final int LIFE_COOLDOWN=2400,LIFE_TICKS=60,JET_COOLDOWN=480,JET_TICKS=60,TRAIL_TICKS=20;
    public static final float LIFE_HEALTH=.001f,LIFE_ABSORPTION=30,JET_DAMAGE=25;
    public static final double JET_SPEED=.85;
    public static boolean recovered(float health) {return health>LIFE_HEALTH;}
    public static float drain(float absorption) {return Math.max(0,absorption-(float)ExtraGadgetConfig.life.drainAmount());}
    private SurvivalRules() { }
}
