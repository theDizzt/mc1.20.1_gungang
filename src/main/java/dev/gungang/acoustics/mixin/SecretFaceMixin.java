package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.SecretBlocks;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Faces behind an invisible block are drawn, otherwise hiding a wall block would leave a see-through hole. */
@Mixin(Block.class)
public abstract class SecretFaceMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void gungang$exposed(BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockPos neighbor,
            CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof RenderChunkRegion && SecretBlocks.clientMode(neighbor) == SecretBlocks.INVISIBLE) cir.setReturnValue(true);
    }
}
