package dev.gungang.acoustics.server;

/** Pure damage budget and horizontal, front-facing coverage rules. */
public final class ShieldRules {
    public static final float CAPACITY=75;
    public record Hit(float durability,float damage) { }
    public static Hit absorb(float durability,float damage) {
        float blocked=Math.min(Math.max(0,durability),Math.max(0,damage));
        return new Hit(Math.max(0,durability-blocked),Math.max(0,damage-blocked));
    }
    public static boolean inFront(double lookX,double lookZ,double sourceX,double sourceZ) {
        return sourceX*sourceX+sourceZ*sourceZ>1e-8&&lookX*sourceX+lookZ*sourceZ>1e-6;
    }
    private ShieldRules() { }
}
