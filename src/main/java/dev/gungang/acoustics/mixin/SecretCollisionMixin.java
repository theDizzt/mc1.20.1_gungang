package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.SecretBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Secret-wand passable blocks keep their model but lose collision on both sides, so movement prediction agrees. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SecretCollisionMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void gungang$passableCached(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (SecretBlocks.mode(level, pos) == SecretBlocks.PASSABLE) cir.setReturnValue(Shapes.empty());
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void gungang$passable(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (SecretBlocks.mode(level, pos) == SecretBlocks.PASSABLE) cir.setReturnValue(Shapes.empty());
    }

    /** No in-wall texture overlay while the camera passes through. */
    @Inject(method = "isViewBlocking", at = @At("HEAD"), cancellable = true)
    private void gungang$seeThrough(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SecretBlocks.mode(level, pos) == SecretBlocks.PASSABLE) cir.setReturnValue(false);
    }
}
