package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.EscortCart;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Arrows, thrown gadgets and the crosshair hit the cart only where its model is, not its turned bounding box. Vanilla
 * returns the entity whose box the path enters first; a cart turned diagonally has a box far larger than its model
 * (about 5.7×5.7 blocks for a 3×5 cart), so a player standing in its empty corners was reported "behind" the cart.
 * The cart's model hit is therefore compared with the nearest other entity, and the closer one wins.
 */
@Mixin(ProjectileUtil.class)
public abstract class EscortProjectileHitMixin {
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",at=@At("RETURN"),cancellable=true)
    private static void gungang$projectileCart(Level level,Entity source,Vec3 from,Vec3 to,AABB box,Predicate<Entity> filter,float inflate,CallbackInfoReturnable<EntityHitResult> ci){
        if(ci.getReturnValue()!=null&&ci.getReturnValue().getEntity() instanceof EscortCart cart){
            var other=ProjectileUtil.getEntityHitResult(level,source,from,to,box,filter.and(e->!(e instanceof EscortCart)),inflate);
            ci.setReturnValue(gungang$nearer(cart,other,from,to,inflate));
        }
    }
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",at=@At("RETURN"),cancellable=true)
    private static void gungang$pickCart(Entity source,Vec3 from,Vec3 to,AABB box,Predicate<Entity> filter,double range,CallbackInfoReturnable<EntityHitResult> ci){
        if(ci.getReturnValue()!=null&&ci.getReturnValue().getEntity() instanceof EscortCart cart){
            var other=ProjectileUtil.getEntityHitResult(source,from,to,box,filter.and(e->!(e instanceof EscortCart)),range);
            ci.setReturnValue(gungang$nearer(cart,other,from,to,other==null?0:other.getEntity().getPickRadius()));
        }
    }
    /** The cart's model hit or the other entity's, whichever the path reaches first; null when neither is hit. */
    private static EntityHitResult gungang$nearer(EscortCart cart,EntityHitResult other,Vec3 from,Vec3 to,double inflate){
        var hit=cart.clip(from,to);
        if(hit.isEmpty())return other;
        if(other!=null){
            var body=other.getEntity().getBoundingBox().inflate(inflate);
            double distance=body.contains(from)?0:body.clip(from,to).map(from::distanceToSqr).orElse(Double.MAX_VALUE);
            if(distance<hit.get().distanceToSqr(from))return other;
        }
        return new EntityHitResult(cart,hit.get());
    }
}
