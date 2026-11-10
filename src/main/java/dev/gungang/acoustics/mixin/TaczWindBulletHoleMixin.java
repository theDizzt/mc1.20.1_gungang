package dev.gungang.acoustics.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wind impacts must not leave bullet decals. */
@Pseudo
@Mixin(targets="com.tacz.guns.client.particle.BulletHoleParticle$Provider",remap=false)
public abstract class TaczWindBulletHoleMixin {
    @Inject(method="createParticle",at=@At("HEAD"),cancellable=true,require=0)
    private void gungang$noWindDecal(@Coerce Object option,ClientLevel level,double x,double y,double z,
                                   double dx,double dy,double dz,CallbackInfoReturnable<Object> cir){
        try {
            if("bf1:wex".equals(option.getClass().getMethod("getGunId").invoke(option)))cir.setReturnValue(null);
        } catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot identify wind decal",failure);}
    }
}
