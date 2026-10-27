package dev.gungang.acoustics.server;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Lethal acid poisoning: eight damage once per second at amplifier 0. */
public final class AcidPoisonEffect extends MobEffect {
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    public AcidPoisonEffect(){super(MobEffectCategory.HARMFUL,0x99FF19);}
    public static boolean active(){return ACTIVE.get();}
    public static int interval(int amplifier){return Math.max(1,20>>Math.max(0,Math.min(30,amplifier)));}
    @Override public boolean isDurationEffectTick(int duration,int amplifier){return duration%interval(amplifier)==0;}
    @Override public void applyEffectTick(LivingEntity entity,int amplifier){
        if(entity.level().isClientSide||!entity.isAlive()
                ||entity instanceof Player player&&PropDimensions.prop(player)!=null)return;
        // Ignore hurt cooldown for this tick only; preserve protection against other attacks.
        int cooldown=entity.invulnerableTime;
        boolean previous=ACTIVE.get();
        ACTIVE.set(true);
        try {entity.invulnerableTime=0;entity.hurt(entity.damageSources().magic(),8);}
        finally {entity.invulnerableTime=cooldown;if(previous)ACTIVE.set(true);else ACTIVE.remove();}
    }
}
