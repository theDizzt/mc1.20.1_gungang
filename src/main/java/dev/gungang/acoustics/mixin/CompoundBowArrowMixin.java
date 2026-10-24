package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.korkuluk.standalonebow.BowArrowEntity",remap=false)
public abstract class CompoundBowArrowMixin implements CompoundBow.ArrowAccess {
    @Shadow private float damage;
    @Shadow private int embeddedTicks;
    @Shadow private java.util.UUID attachedEntity;
    @Shadow public abstract boolean isEmbedded();
    @Shadow public abstract void finishImpact(Vec3 hit);
    @Shadow public abstract void finishBlockImpact(BlockHitResult hit);
    @Override public float gungang$shotDamage(){return damage;}
    @Override public void gungang$embed(BlockHitResult hit){finishBlockImpact(hit);}
    /** Block-embedded arrows only; arrows stuck in an entity keep the bow mod's own lifetime. Dev and SRG names of tick. */
    @Inject(method={"tick","m_8119_"},at=@At("HEAD"),cancellable=true,require=1)
    private void gungang$expire(CallbackInfo ci){
        var arrow=(Projectile)(Object)this;
        if(!arrow.level().isClientSide&&isEmbedded()&&attachedEntity==null&&embeddedTicks>=CompoundBow.WALL_ARROW_TICKS){arrow.discard();ci.cancel();}
    }
    @Inject(method="prepare",at=@At("RETURN"),require=1)
    private void gungang$damage(ServerPlayer player,ItemStack stack,float charge,CallbackInfo ci){
        damage=CompoundBow.damage(CompoundBowAimAccessor.gungang$aiming().getOrDefault(player.getUUID(),false),charge);
    }
    @Inject(method="hitEntity",at=@At("HEAD"),cancellable=true,require=1)
    private void gungang$deployable(@Coerce Object value,CallbackInfo ci){
        var hit=(CompoundBowHitAccessor)value;var arrow=(Projectile)(Object)this;
        if(hit.gungang$entity() instanceof DeployableEntity placed){
            if(!arrow.level().isClientSide&&!arrow.isRemoved()){
                placed.hurt(arrow.level().damageSources().thrown(arrow,arrow.getOwner()),damage);
                finishImpact(hit.gungang$position());
            }
            ci.cancel();
        }
    }
}
