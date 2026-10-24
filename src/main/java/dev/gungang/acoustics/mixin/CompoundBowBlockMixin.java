package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arrows never change map blocks: no glass, fragile or wood breaking, no ice melting and no fire, whatever the bow config says. */
@Pseudo
@Mixin(targets="com.korkuluk.standalonebow.BlockImpactHandler",remap=false)
public abstract class CompoundBowBlockMixin {
    @Inject(method="onHit",at=@At("HEAD"),cancellable=true,require=1)
    private static void gungang$block(@Coerce Object value,BlockHitResult hit,CallbackInfo ci){
        var arrow=(Projectile)value;var level=arrow.level();
        if(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SpawnGateBlock){arrow.discard();ci.cancel();return;}
        if(GimmickEntity.at(level,hit.getBlockPos())!=null){
            if(!level.isClientSide&&!SafeZones.contains(arrow.getOwner())&&!SpawnGateProtection.bulletBlocked(arrow,hit.getLocation()))
                GimmickEntity.hit(level,hit.getBlockPos(),(float)(((CompoundBow.ArrowAccess)value).gungang$shotDamage()*AdrenalineDamage.multiplier(arrow.getOwner())));
            ((CompoundBow.ArrowAccess)value).gungang$embed(hit);ci.cancel();
        }
    }
    @Redirect(method="onHit",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",remap=true),require=1)
    private static boolean gungang$keepBlock(ServerLevel level,BlockPos pos,boolean drop){return false;}
    @Redirect(method="onHit",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",remap=true),require=1)
    private static boolean gungang$keepIce(ServerLevel level,BlockPos pos,BlockState state,int flags){return false;}
    // Fire on the map spreads to wood and wool; the flame arrow ignores the bow's setFireToBlocks option.
    @Inject(method={"ignite","igniteFlameArrow"},at=@At("HEAD"),cancellable=true,require=2)
    private static void gungang$noFire(CallbackInfo ci){ci.cancel();}
}
