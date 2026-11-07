package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.EscortCart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Piercing TaCZ bullets (pierce above 1) collect every entity on their path and spend one pierce per entity, so the cart
 * cost them a single pierce and players or bots behind it were still hit. The cart is solid: hits beyond it are dropped,
 * and the bullet keeps exactly enough pierce for the hits in front of it, so TaCZ discards it on the cart.
 */
@Pseudo
@Mixin(targets="com.tacz.guns.util.EntityUtil",remap=false)
public abstract class TaczCartStopMixin {
    private static java.lang.reflect.Method gungang$entity,gungang$hit;
    private static java.lang.reflect.Field gungang$pierce;
    @Inject(method="findEntitiesOnPath",at=@At("RETURN"),cancellable=true,require=0)
    private static void gungang$stopAtCart(Projectile bullet,Vec3 from,Vec3 to,CallbackInfoReturnable<List<Object>> ci){
        var hits=ci.getReturnValue();if(hits==null||hits.isEmpty())return;
        try{
            if(gungang$entity==null){
                var type=Class.forName("com.tacz.guns.entity.EntityKineticBullet$EntityResult");
                gungang$entity=type.getMethod("getEntity");gungang$hit=type.getMethod("getHitPos");
            }
            double cart=Double.MAX_VALUE;
            for(var hit:hits)if(gungang$entity.invoke(hit) instanceof EscortCart)cart=Math.min(cart,((Vec3)gungang$hit.invoke(hit)).distanceToSqr(from));
            if(cart==Double.MAX_VALUE)return;
            var kept=new ArrayList<Object>();boolean cartKept=false;
            for(var hit:hits){
                Entity entity=(Entity)gungang$entity.invoke(hit);double distance=((Vec3)gungang$hit.invoke(hit)).distanceToSqr(from);
                if(entity instanceof EscortCart){if(!cartKept&&distance<=cart){kept.add(hit);cartKept=true;}}
                else if(distance<cart)kept.add(hit);
            }
            if(gungang$pierce==null){var field=Class.forName("com.tacz.guns.entity.EntityKineticBullet").getDeclaredField("pierce");field.setAccessible(true);gungang$pierce=field;}
            if(gungang$pierce.getDeclaringClass().isInstance(bullet))gungang$pierce.setInt(bullet,Math.min(gungang$pierce.getInt(bullet),kept.size()));
            ci.setReturnValue(kept);
        }catch(ReflectiveOperationException|ClassCastException incompatible){/* An unknown TaCZ build keeps its own piercing. */}
    }
}
