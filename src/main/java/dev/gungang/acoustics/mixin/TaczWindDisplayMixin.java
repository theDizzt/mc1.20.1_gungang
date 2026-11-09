package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.WindBlowerVisuals;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The 35 classic blower (bf1:wex) shows wind instead of fire; see {@link WindBlowerVisuals}. */
@Pseudo
@Mixin(targets="com.tacz.guns.client.resource.GunDisplayInstance",remap=false)
public abstract class TaczWindDisplayMixin {
    @Shadow @Final private ResourceLocation displayId;
    @Inject(method="getMuzzleFlash",at=@At("RETURN"),cancellable=true,require=0)
    private void gungang$windFlash(CallbackInfoReturnable<Object> cir) {
        if(WindBlowerVisuals.blower(displayId)){var wind=WindBlowerVisuals.muzzleFlash();if(wind!=null)cir.setReturnValue(wind);}
    }
    @Inject(method="getParticle",at=@At("RETURN"),cancellable=true,require=0)
    private void gungang$windParticle(CallbackInfoReturnable<Object> cir) {
        if(WindBlowerVisuals.blower(displayId)){var wind=WindBlowerVisuals.particle();if(wind!=null)cir.setReturnValue(wind);}
    }
    @Inject(method="getTracerColor",at=@At("RETURN"),cancellable=true,require=0)
    private void gungang$windTracer(CallbackInfoReturnable<float[]> cir) {
        if(WindBlowerVisuals.blower(displayId))cir.setReturnValue(WindBlowerVisuals.tracer());
    }
}
