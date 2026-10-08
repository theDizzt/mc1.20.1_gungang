package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.client.AcousticChannel;
import dev.gungang.acoustics.client.AcousticFrame;
import dev.gungang.acoustics.physics.AcousticMath;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All methods run on Minecraft's sound executor, where its OpenAL context is current. */
@Mixin(Channel.class)
public abstract class ChannelMixin implements AcousticChannel {
    @Shadow @Final private int source;
    @Shadow public abstract void setVolume(float volume);
    @Shadow public abstract void linearAttenuation(float distance);
    @Shadow public abstract void disableAttenuation();
    @Unique private float gungang$baseVolume = 1;
    @Unique private float gungang$range = 16;
    @Unique private double gungang$gain = 1;
    @Unique private double gungang$hf = 1;
    @Unique private boolean gungang$enabled;
    @Unique private boolean gungang$originalLinear;
    @Unique private int gungang$filter;
    @Unique private boolean gungang$filterUnavailable;
    @Unique private long gungang$lastUpdate;
    @Unique private static boolean gungang$warned;

    @Inject(method = "linearAttenuation", at = @At("HEAD"))
    private void gungang$rememberRange(float distance, CallbackInfo ci) {
        gungang$range = distance;
        gungang$originalLinear = true;
    }

    @ModifyArg(method = "setVolume", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alSourcef(IIF)V", remap = false), index = 2)
    private float gungang$volume(float volume) {
        gungang$baseVolume = volume;
        return (float) (volume * gungang$gain);
    }

    @Override
    public void gungang$apply(AcousticFrame frame) {
        if (!frame.enabled()) {
            if (gungang$enabled) {
                gungang$enabled = false;
                gungang$gain = gungang$hf = 1;
                if (gungang$originalLinear) linearAttenuation(gungang$range);
                else disableAttenuation();
                setVolume(gungang$baseVolume);
                gungang$releaseFilter();
            }
            gungang$lastUpdate = 0;
            return;
        }
        long now = System.nanoTime();
        double gain = frame.transmission() * (frame.manageDistance()
                ? AcousticMath.distanceGain(frame.distance(), gungang$range, frame.reference(), frame.rolloff()) : 1);
        if (gungang$lastUpdate == 0) {
            gungang$gain = gain;
            gungang$hf = frame.highFrequency();
        } else {
            double elapsed = (now - gungang$lastUpdate) * 1e-9;
            gungang$gain = AcousticMath.smooth(gungang$gain, gain, elapsed, frame.smoothing());
            gungang$hf = AcousticMath.smooth(gungang$hf, frame.highFrequency(), elapsed, frame.smoothing());
        }
        gungang$lastUpdate = now;
        gungang$enabled = true;
        // Distance is included exactly once in our gain; vanilla still controls source/category/master volume.
        disableAttenuation();
        setVolume(gungang$baseVolume);
        gungang$updateFilter();
    }

    @Unique
    private void gungang$updateFilter() {
        if (gungang$filterUnavailable) return;
        if (gungang$filter == 0 && gungang$hf > 0.999) return;
        if (gungang$filter == 0) {
            long context = ALC10.alcGetCurrentContext();
            long device = context == 0 ? 0 : ALC10.alcGetContextsDevice(context);
            if (device == 0 || !ALC.getCapabilities().ALC_EXT_EFX) {
                gungang$filterUnavailable = true;
                gungang$warnOnce();
                return;
            }
            gungang$filter = EXTEfx.alGenFilters();
            if (gungang$filter == 0) {
                gungang$filterUnavailable = true;
                gungang$warnOnce();
                return;
            }
            EXTEfx.alFilteri(gungang$filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(gungang$filter, EXTEfx.AL_LOWPASS_GAIN, 1);
        }
        EXTEfx.alFilterf(gungang$filter, EXTEfx.AL_LOWPASS_GAINHF, (float) gungang$hf);
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, gungang$filter);
    }

    @Unique
    private static void gungang$warnOnce() {
        if (!gungang$warned) {
            gungang$warned = true;
            LogUtils.getLogger().warn("Gungang Acoustics: OpenAL EFX unavailable; using volume attenuation without low-pass filtering.");
        }
    }

    @Unique
    private void gungang$releaseFilter() {
        if (gungang$filter != 0) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, 0);
            EXTEfx.alDeleteFilters(gungang$filter);
            gungang$filter = 0;
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void gungang$destroy(CallbackInfo ci) { gungang$releaseFilter(); }
}
