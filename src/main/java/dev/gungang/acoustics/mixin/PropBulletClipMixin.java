package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.PropBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TaCZ uses ClipContext on both sides before testing entities, so skipping scenery preserves hits behind it. */
@Mixin(ClipContext.class)
public abstract class PropBulletClipMixin {
    @Unique private boolean gungang$thinScenery;

    @Inject(method="<init>",at=@At("RETURN"))
    private void gungang$bullet(Vec3 from,Vec3 to,ClipContext.Block block,ClipContext.Fluid fluid,Entity entity,CallbackInfo ci){
        gungang$thinScenery=block==ClipContext.Block.COLLIDER&&PropBullet.penetrates(entity);
    }

    @Inject(method="getBlockShape",at=@At("RETURN"),cancellable=true)
    private void gungang$thinBlock(BlockState state,BlockGetter level,BlockPos pos,CallbackInfoReturnable<VoxelShape> cir){
        if(gungang$thinScenery)cir.setReturnValue(PropBullet.blockingShape(cir.getReturnValue()));
    }
}
