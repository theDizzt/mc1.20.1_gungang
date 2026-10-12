package dev.gungang.acoustics.mixin;
import dev.gungang.acoustics.server.SecondaryRuntime;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="me.xjqsh.lrtactical.capability.CustomItemCoolDowns",remap=false)
public abstract class TacticalCooldownMixin {
    @Inject(method="isOnCooldown",at=@At("HEAD"),cancellable=true,require=0)
    private void ready(ResourceLocation id,CallbackInfoReturnable<Boolean> ci) { if(SecondaryRuntime.overriding())ci.setReturnValue(false); }
    @Inject(method="addCooldown",at=@At("HEAD"),cancellable=true,require=0)
    private void ignore(ResourceLocation id,int ticks,CallbackInfo ci) { if(SecondaryRuntime.overriding()) {SecondaryRuntime.requestedCooldown();ci.cancel();} }
}
