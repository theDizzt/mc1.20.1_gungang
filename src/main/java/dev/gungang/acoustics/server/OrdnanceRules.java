package dev.gungang.acoustics.server;

public final class OrdnanceRules {
    public static final int MOLOTOV_COOLDOWN=36*20, FIRE_DURATION=8*20, BURN_SECONDS=3;
    public static final int ORBITAL_COOLDOWN=240*20, ORBITAL_DELAY=5*20;
    public static final double FIRE_RADIUS=5, ORBITAL_RADIUS=24, TARGET_RANGE=64;
    public static float orbitalDamage(double distance) {
        if(distance>ORBITAL_RADIUS)return 0;
        return (float)(256-(256-84)*Math.max(0,distance)/ORBITAL_RADIUS);
    }
    public static boolean inFire(double dx,double dy,double dz) {
        return dx*dx+dz*dz<=FIRE_RADIUS*FIRE_RADIUS&&dy>=-1&&dy<=2;
    }
    public static boolean inBlast(double dx,double dy,double dz) {
        return dx*dx+dy*dy+dz*dz<=ORBITAL_RADIUS*ORBITAL_RADIUS;
    }
    private OrdnanceRules() { }
}
