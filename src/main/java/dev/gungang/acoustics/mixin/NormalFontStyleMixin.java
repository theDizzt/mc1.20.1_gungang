package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.AcousticConfig;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Normalize bold for custom TTFs in both glyph rendering and width measurement. */
@Mixin(Style.class)
public abstract class NormalFontStyleMixin {
    @Inject(method = "isBold", at = @At("HEAD"), cancellable = true)
    private void gungang$normalWeight(CallbackInfoReturnable<Boolean> callback) {
        boolean custom = AcousticConfig.SPEC.isLoaded()
                ? AcousticConfig.CUSTOM_FONT.get() : AcousticConfig.CUSTOM_FONT.getDefault();
        if (custom) callback.setReturnValue(false);
    }
}
