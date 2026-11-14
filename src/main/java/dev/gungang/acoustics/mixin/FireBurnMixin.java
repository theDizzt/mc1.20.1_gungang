package dev.gungang.acoustics.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire never burns a map block away (or replaces it with more fire); it still burns out by itself. Forge's
 * {@code tryCatchFire} replaces vanilla's {@code checkBurnOut}, so the name is not remapped.
 */
@Mixin(FireBlock.class)
public abstract class FireBurnMixin {
    @Inject(method="tryCatchFire",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void gungang$noBurn(Level level,BlockPos pos,int chance,RandomSource random,int age,Direction face,CallbackInfo ci){ci.cancel();}
}
