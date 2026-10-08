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

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "play", at = @At("HEAD"))
    private void gungang$prepare(SoundInstance sound, CallbackInfo ci) { AcousticManager.prepare(sound); }

    @Inject(method = "tick", at = @At("TAIL"))
    private void gungang$update(boolean paused, CallbackInfo ci) { AcousticManager.update(instanceToChannel, paused); }
}
