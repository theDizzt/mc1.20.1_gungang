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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.sounds.SoundSource;
import dev.gungang.acoustics.client.GungangSoundVolume;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "play", at = @At("HEAD"))
    private void gungang$prepare(SoundInstance sound, CallbackInfo ci) { AcousticManager.prepare(sound); }

    /**
     * Vanilla clamps pitch to 0.5-2 (two octaves); the note block disguise and the widened vanilla note block
     * (NoteBlockRangeMixin) play six (0.125-8, F#1 to F#7 on the harp).
     */
    @Inject(method = "calculatePitch", at = @At("HEAD"), cancellable = true)
    private void gungang$notePitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        var id = sound.getLocation();
        if ((id.getNamespace().equals("gungang") && id.getPath().equals("prop_note"))
                || (id.getNamespace().equals("minecraft") && id.getPath().startsWith("block.note_block.")))
            cir.setReturnValue(net.minecraft.util.Mth.clamp(sound.getPitch(), .125f, 8f));
    }

    @Shadow protected abstract float calculateVolume(float volume, SoundSource source);
    /** The sound {@code play} is starting (after Forge's PlaySoundEvent may have replaced it), for its volume below. */
    @Unique private SoundInstance gungang$starting;

    @Redirect(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
    private float gungang$startingVolume(SoundInstance sound) { gungang$starting = sound; return sound.getVolume(); }

    /** GunGang sound volume (Music & Sounds screen) on a starting sound; a muted one is skipped like vanilla's zero volume. */
    @Redirect(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float gungang$startVolume(SoundEngine engine, float volume, SoundSource source) {
        float result = calculateVolume(volume, source) * GungangSoundVolume.scale(gungang$starting);
        gungang$starting = null;
        return result;
    }

    /** ... and on playing sounds when a volume changes or a tickable sound updates. */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void gungang$volume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float scale = GungangSoundVolume.scale(sound);
        if (scale != 1) cir.setReturnValue(cir.getReturnValueF() * scale);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void gungang$update(boolean paused, CallbackInfo ci) { AcousticManager.update(instanceToChannel, paused); }
}
