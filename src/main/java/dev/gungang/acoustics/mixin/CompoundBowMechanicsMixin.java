package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.CompoundBow;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.korkuluk.standalonebow.BowMechanics",remap=false)
public abstract class CompoundBowMechanicsMixin {
    @Inject(method="consumeAmmo",at=@At("HEAD"),cancellable=true,require=1)
    private static void gungang$infinite(ServerPlayer player,CallbackInfo ci){
        if(CompoundBow.infiniteAmmo(player))ci.cancel();
    }
}
