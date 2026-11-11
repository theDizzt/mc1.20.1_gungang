package dev.gungang.acoustics.server;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** What a health pack cures: harmful status effects, burning and bleeding. */
public final class StatusCure {
    public static boolean ailing(LivingEntity entity){
        return entity.isOnFire()||Bleeding.bleeding(entity)
                ||entity.getActiveEffects().stream().anyMatch(e->e.getEffect().getCategory()==MobEffectCategory.HARMFUL);
    }
    /** Returns whether anything was cured. */
    public static boolean cure(LivingEntity entity){
        boolean cured=ailing(entity);
        for(var effect:entity.getActiveEffects().stream().filter(e->e.getEffect().getCategory()==MobEffectCategory.HARMFUL).map(e->e.getEffect()).toList())
            entity.removeEffect(effect);
        entity.clearFire();Bleeding.clear(entity);
        return cured;
    }
    private StatusCure(){}
}
