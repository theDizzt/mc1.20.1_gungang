package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.SecretBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chests, signs and other block-entity models of invisible blocks are hidden too. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class SecretBlockEntityMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void gungang$hide(E entity, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        if (entity.getLevel() != null && entity.getLevel().isClientSide
                && SecretBlocks.clientMode(entity.getBlockPos()) == SecretBlocks.INVISIBLE) ci.cancel();
    }
}
