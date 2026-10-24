package dev.gungang.acoustics.mixin;

import java.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="com.korkuluk.standalonebow.BowMechanics",remap=false)
public interface CompoundBowAimAccessor {
    @Accessor("AIMING") static Map<UUID,Boolean> gungang$aiming(){throw new AssertionError();}
}
