package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.PlayerImpulse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LivingEntity.knockback alone marks hasImpulse, which does not notify its own player client. */
@Mixin(LivingEntity.class)
public abstract class PlayerKnockbackMixin {
    @Unique private Vec3 gungang$beforeKnockback;
    @Inject(method="knockback",at=@At("HEAD"))
    private void gungang$before(double strength,double x,double z,CallbackInfo ci){
        if((Object)this instanceof ServerPlayer p)gungang$beforeKnockback=p.getDeltaMovement();
    }
    @Inject(method="knockback",at=@At("RETURN"))
    private void gungang$send(double strength,double x,double z,CallbackInfo ci){
        if((Object)this instanceof ServerPlayer p&&gungang$beforeKnockback!=null&&!p.getDeltaMovement().equals(gungang$beforeKnockback))PlayerImpulse.sync(p);
        gungang$beforeKnockback=null;
    }
}
