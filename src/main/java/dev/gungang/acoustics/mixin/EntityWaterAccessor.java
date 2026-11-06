package dev.gungang.acoustics.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 224: the aquarium's display fish count as swimming, so fish renderers do not lay them on their side. */
@Mixin(Entity.class)
public interface EntityWaterAccessor {
    @Accessor("wasTouchingWater")
    void gungang$setInWater(boolean inWater);
}
