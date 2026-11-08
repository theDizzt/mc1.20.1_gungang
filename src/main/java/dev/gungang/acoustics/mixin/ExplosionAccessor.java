package dev.gungang.acoustics.mixin;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The blast radius, for map gimmicks an explosion reaches (they never enter its block list). */
@Mixin(Explosion.class)
public interface ExplosionAccessor {
    @Accessor("radius") float gungang$radius();
}
