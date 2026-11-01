package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gungang.acoustics.server.SecretBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Secret-wand invisible blocks are left out of the chunk mesh; only real chunk builds are affected, not item/prop renders. */
@Mixin(BlockRenderDispatcher.class)
public abstract class SecretRenderMixin {
    // Forge-added overload: it has no SRG name, so it is not remapped.
    @Inject(method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void gungang$hide(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose, VertexConsumer consumer,
            boolean checkSides, RandomSource random, ModelData data, RenderType type, CallbackInfo ci) {
        if (level instanceof RenderChunkRegion && SecretBlocks.clientMode(pos) == SecretBlocks.INVISIBLE) ci.cancel();
    }
}
