package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.OffhandMacro;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.AimKey", remap = false)
public abstract class TaczAimInputMixin {
    @Inject(method = "onAimHoldingPreInput", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gungang$macroInput(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (OffhandMacro.ownsGunInput()) ci.cancel();
    }
}
