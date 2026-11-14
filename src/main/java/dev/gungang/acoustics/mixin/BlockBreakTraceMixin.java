package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.BlockBreakTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every server block change passes here; {@link BlockBreakTrace} reports the ones that break a map block. */
@Mixin(Level.class)
public abstract class BlockBreakTraceMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),require=1)
    private void gungang$trace(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir){
        BlockBreakTrace.check((Level)(Object)this,pos,state);
    }
}
