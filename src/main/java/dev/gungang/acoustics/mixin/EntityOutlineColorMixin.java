package dev.gungang.acoustics.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityOutlineColorMixin {
    @Inject(method="getTeamColor",at=@At("HEAD"),cancellable=true)
    private void gungang$outlineColor(CallbackInfoReturnable<Integer> ci){
        var entity=(Entity)(Object)this;
        if(entity instanceof dev.gungang.acoustics.server.PropEntity prop){
            ci.setReturnValue(prop.marker()?0xFF3030:0xFFFFFF);return;
        }
        Integer color=dev.gungang.acoustics.client.ScannerVision.color(entity);
        if(color!=null)ci.setReturnValue(color);
    }
}
