package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
}
