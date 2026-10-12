package dev.gungang.acoustics.server;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Only these area attacks bypass scoreboard friendly-fire protection. */
public final class OrdnanceDamage {
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    public static boolean active() {return ACTIVE.get();}
    public static void hurt(LivingEntity victim,DamageSource source,float amount) {
        boolean previous=ACTIVE.get();ACTIVE.set(true);
        try {victim.hurt(source,amount);}
        finally {if(previous)ACTIVE.set(true);else ACTIVE.remove();}
    }
    private OrdnanceDamage() { }
}
