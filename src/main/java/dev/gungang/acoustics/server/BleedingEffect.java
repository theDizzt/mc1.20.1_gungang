package dev.gungang.acoustics.server;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The bleeding status as players see it: its icon beside the health bar, its tint on the portrait, its removal by
 * cures. It does nothing by itself; {@link Bleeding} deals the damage (per weapon strength) while this effect runs.
 */
public final class BleedingEffect extends MobEffect {
    public BleedingEffect(){super(MobEffectCategory.HARMFUL,0xA0101A);}
}
