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

/** Arrows, thrown gadgets and the crosshair hit the cart only where its model is, not its turned bounding box. */
@Mixin(ProjectileUtil.class)
public abstract class EscortProjectileHitMixin {
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",at=@At("RETURN"),cancellable=true)
    private static void gungang$projectileCart(Level level,Entity source,Vec3 from,Vec3 to,AABB box,Predicate<Entity> filter,float inflate,CallbackInfoReturnable<EntityHitResult> ci){
        if(ci.getReturnValue()!=null&&ci.getReturnValue().getEntity() instanceof EscortCart cart){
            var hit=cart.clip(from,to);
            ci.setReturnValue(hit.isPresent()?new EntityHitResult(cart,hit.get()):ProjectileUtil.getEntityHitResult(level,source,from,to,box,filter.and(e->!(e instanceof EscortCart)),inflate));
        }
    }
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",at=@At("RETURN"),cancellable=true)
    private static void gungang$pickCart(Entity source,Vec3 from,Vec3 to,AABB box,Predicate<Entity> filter,double range,CallbackInfoReturnable<EntityHitResult> ci){
        if(ci.getReturnValue()!=null&&ci.getReturnValue().getEntity() instanceof EscortCart cart){
            var hit=cart.clip(from,to);
            ci.setReturnValue(hit.isPresent()?new EntityHitResult(cart,hit.get()):ProjectileUtil.getEntityHitResult(source,from,to,box,filter.and(e->!(e instanceof EscortCart)),range));
        }
    }
}
