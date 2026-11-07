package dev.gungang.acoustics.mixin;

import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 199 conduit disguise: a lone conduit has no prismarine frame, so PropEffects forces its awake, open-eyed look. */
@Mixin(ConduitBlockEntity.class)
public interface ConduitStateAccessor {
    @Accessor("isActive") void gungang$setActive(boolean active);
    @Accessor("isHunting") void gungang$setHunting(boolean hunting);
}
