package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.EscortCart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TaCZ bullets clip entity bounding boxes; the cart's turned box is far larger than its model, so test the model pieces instead. */
@Pseudo
@Mixin(targets="com.tacz.guns.util.EntityUtil",remap=false)
public abstract class TaczCartHitMixin {
    private static java.lang.reflect.Constructor<?> gungang$result;
    @Inject(method="getHitResult",at=@At("HEAD"),cancellable=true,require=0)
    private static void gungang$cartHit(Projectile bullet,Entity target,Vec3 from,Vec3 to,CallbackInfoReturnable<Object> ci){
        if(!(target instanceof EscortCart cart))return;
        var hit=cart.clip(from,to);
        if(hit.isEmpty()){ci.setReturnValue(null);return;}
        try{
            if(gungang$result==null)gungang$result=Class.forName("com.tacz.guns.entity.EntityKineticBullet$EntityResult").getConstructor(Entity.class,Vec3.class,boolean.class);
            ci.setReturnValue(gungang$result.newInstance(cart,hit.get(),false));
        }catch(ReflectiveOperationException error){throw new IllegalStateException("TaCZ hit result changed",error);}
    }
}
