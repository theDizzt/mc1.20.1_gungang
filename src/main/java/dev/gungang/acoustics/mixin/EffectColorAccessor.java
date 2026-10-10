package dev.gungang.acoustics.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface EffectColorAccessor {
    @Accessor("DATA_EFFECT_COLOR_ID")
    static EntityDataAccessor<Integer> colorKey() { throw new AssertionError(); }
}
