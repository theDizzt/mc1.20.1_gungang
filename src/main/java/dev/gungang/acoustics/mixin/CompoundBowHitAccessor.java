package dev.gungang.acoustics.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="com.korkuluk.standalonebow.BowArrowEntity$Hit",remap=false)
public interface CompoundBowHitAccessor {
    @Accessor("entity") Entity gungang$entity();
    @Accessor("position") Vec3 gungang$position();
}
