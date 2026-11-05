package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.AcousticManager;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "play", at = @At("HEAD"))
    private void gungang$prepare(SoundInstance sound, CallbackInfo ci) { AcousticManager.prepare(sound); }

    /** Vanilla clamps pitch to 0.5-2 (two octaves); the note block disguise plays six (0.125-8, F#1 to F#7 on the harp). */
    @Inject(method = "calculatePitch", at = @At("HEAD"), cancellable = true)
    private void gungang$notePitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound.getLocation().getNamespace().equals("gungang") && sound.getLocation().getPath().equals("prop_note"))
            cir.setReturnValue(net.minecraft.util.Mth.clamp(sound.getPitch(), .125f, 8f));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void gungang$update(boolean paused, CallbackInfo ci) { AcousticManager.update(instanceToChannel, paused); }
}
