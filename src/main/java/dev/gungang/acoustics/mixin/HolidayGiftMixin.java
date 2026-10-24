package dev.gungang.acoustics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armourer's Workshop hands every player a holiday gift sack on login; match loadouts must not get extra items. */
@Pseudo
@Mixin(targets = "moe.plushie.armourers_workshop.init.ModHolidays", remap = false)
public abstract class HolidayGiftMixin {
    @Inject(method = "welcome", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gungang$noGift(CallbackInfo ci) {ci.cancel();}
}
