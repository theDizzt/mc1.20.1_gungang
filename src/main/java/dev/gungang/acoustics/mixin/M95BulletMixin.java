package dev.gungang.acoustics.mixin;
import dev.gungang.acoustics.server.M95;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.tacz.guns.entity.EntityKineticBullet",remap=false)
public abstract class M95BulletMixin implements M95.NativeBullet {
    @org.spongepowered.asm.mixin.gen.Invoker("onBulletTick") public abstract void gungang$nativeBulletTick();
    @Inject(method="onBulletTick",at=@At("HEAD"),cancellable=true)
    private void gungang$m95(CallbackInfo ci){var bullet=(Projectile)(Object)this;if(!M95.nativeTick(bullet)&&M95.intercept(bullet)){M95.fire(bullet);ci.cancel();}}
    @Inject(method="onHitEntity",at=@At("HEAD"))
    private void gungang$hit(@Coerce net.minecraft.world.phys.EntityHitResult hit,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end,CallbackInfo ci){
        M95.nativeHit((Projectile)(Object)this,hit.getLocation());
    }
}
