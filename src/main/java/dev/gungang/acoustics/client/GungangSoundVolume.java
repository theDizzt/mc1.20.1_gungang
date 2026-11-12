package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/**
 * One volume for every sound GunGang adds (sound events in the {@code gungang} namespace: turret, heal, cooldown
 * chime, prop note ...), whichever vanilla category they play in. The vanilla category and master volumes still
 * apply on top. The slider sits on the vanilla Music & Sounds screen ({@code SoundOptionsScreenMixin}).
 */
public final class GungangSoundVolume {
    private static OptionInstance<Double> option;

    /** 0..1 for a GunGang sound, 1 for anything else. */
    public static float scale(SoundInstance sound) {
        if (sound == null) return 1;
        var id = sound.getLocation();
        if (id == null || !id.getNamespace().equals("gungang")) return 1;
        return (float) value();
    }
    static double value() {
        try { return AcousticConfig.SPEC.isLoaded() ? AcousticConfig.GUNGANG_SOUND_VOLUME.get() : 1; }
        catch (RuntimeException notLoaded) { return 1; }
    }

    /** The slider, labelled and formatted like the vanilla category sliders. */
    public static OptionInstance<Double> option() {
        if (option == null) option = new OptionInstance<>("gungang.options.gungangSoundVolume", OptionInstance.noTooltip(),
                (label, value) -> value == 0 ? Options.genericValueLabel(label, CommonComponents.OPTION_OFF)
                        : Component.translatable("options.percent_value", label, (int) Math.round(value * 100)),
                OptionInstance.UnitDouble.INSTANCE, value(), GungangSoundVolume::set);
        else option.set(value());
        return option;
    }
    private static void set(double value) {
        try {
            AcousticConfig.GUNGANG_SOUND_VOLUME.set(Math.max(0, Math.min(1, value)));
            AcousticConfig.SPEC.save();
        } catch (RuntimeException notLoaded) { return; }
        // Re-evaluates the volume of every playing sound (vanilla does this for any non-master category).
        var options = Minecraft.getInstance().options;
        Minecraft.getInstance().getSoundManager().updateSourceVolume(SoundSource.BLOCKS, options.getSoundSourceVolume(SoundSource.BLOCKS));
    }
    private GungangSoundVolume() { }
}
