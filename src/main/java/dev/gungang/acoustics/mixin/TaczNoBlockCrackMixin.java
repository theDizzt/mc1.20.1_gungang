package dev.gungang.acoustics.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No block takes breaking progress (cracks) from TaCZ Tweaks shots or melee, since none can break (TaczNoBlockBreakMixin). */
@Pseudo
@Mixin(targets="me.muksc.tacztweaks.core.BlockBreakingManager",remap=false)
public abstract class TaczNoBlockCrackMixin {
    @Inject(method="addCurrentProgress",at=@At("HEAD"),cancellable=true,require=0)
    private void gungang$noProgress(ServerLevel level,BlockPos pos,float delta,CallbackInfoReturnable<Float> cir){cir.setReturnValue(0f);}
}
