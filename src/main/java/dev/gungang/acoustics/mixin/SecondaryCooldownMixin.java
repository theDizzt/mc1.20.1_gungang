package dev.gungang.acoustics.mixin;
import dev.gungang.acoustics.server.SecondaryRuntime;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ItemCooldowns.class)
public abstract class SecondaryCooldownMixin {
    @Inject(method="getCooldownPercent",at=@At("HEAD"),cancellable=true)
    private void configured(Item item,float partial,CallbackInfoReturnable<Float> ci) {
        var value=SecondaryRuntime.cooldown((ItemCooldowns)(Object)this,item,partial);if(value!=null)ci.setReturnValue(value);
    }
    @Inject(method="addCooldown",at=@At("HEAD"),cancellable=true)
    private void override(Item item,int ticks,CallbackInfo ci) { if(SecondaryRuntime.overriding()) {SecondaryRuntime.requestedCooldown();ci.cancel();} }
}
