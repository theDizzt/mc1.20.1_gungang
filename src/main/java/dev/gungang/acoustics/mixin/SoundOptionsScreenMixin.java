package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.GungangSoundVolume;
import java.util.Arrays;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the "GunGang 효과음" slider after the vanilla category sliders on the Music & Sounds screen. */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {
    @Inject(method = "getAllSoundOptionsExceptMaster", at = @At("RETURN"), cancellable = true)
    private void gungang$addSlider(CallbackInfoReturnable<OptionInstance<?>[]> cir) {
        var vanilla = cir.getReturnValue();
        var options = Arrays.copyOf(vanilla, vanilla.length + 1);
        options[vanilla.length] = GungangSoundVolume.option();
        cir.setReturnValue(options);
    }
}
