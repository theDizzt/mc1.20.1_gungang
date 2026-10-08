package dev.gungang.acoustics.client;

import com.mojang.blaze3d.audio.Channel;
import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class AcousticManager {
    // Weak keys also release sounds cancelled by another mod or never assigned a channel.
    private static final Map<SoundInstance, AcousticFrame> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    private static int cursor;
    private static long tickStamp;
    private static int initialTraces;

    public static void prepare(SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;
        // Bound expensive diffraction searches during bursts; direct wall transmission always runs.
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (tickStamp != now) { tickStamp = now; initialTraces = 0; }
        PENDING.put(sound, frame(sound, initialTraces++ < AcousticConfig.UPDATE_BUDGET.get()));
    }

    @SubscribeEvent
    public static void onStatic(PlaySoundSourceEvent event) { attach(event.getSound(), event.getChannel()); }

    @SubscribeEvent
    public static void onStream(PlayStreamingSourceEvent event) { attach(event.getSound(), event.getChannel()); }

    private static void attach(SoundInstance sound, Channel channel) {
        AcousticFrame frame = PENDING.remove(sound);
        if (frame != null) ((AcousticChannel) channel).gungang$apply(frame);
    }

    public static void update(Map<SoundInstance, ChannelAccess.ChannelHandle> channels, boolean paused) {
        Minecraft mc = Minecraft.getInstance();
        if (paused || !mc.isSameThread()) return;
        if (mc.level == null || channels.isEmpty()) { PENDING.clear(); cursor = 0; return; }
        // A snapshot preserves the engine's sound identity and looping/stopping behavior.
        var entries = channels.entrySet().toArray(Map.Entry[]::new);
        int count = Math.min(AcousticConfig.UPDATE_BUDGET.get(), entries.length);
        for (int i = 0; i < count; i++) {
            var entry = entries[Math.floorMod(cursor++, entries.length)];
            SoundInstance sound = (SoundInstance) entry.getKey();
            ChannelAccess.ChannelHandle handle = (ChannelAccess.ChannelHandle) entry.getValue();
            AcousticFrame frame = frame(sound, true);
            handle.execute(channel -> ((AcousticChannel) channel).gungang$apply(frame));
        }
    }

    private static AcousticFrame frame(SoundInstance sound, boolean trace) {
        Minecraft mc = Minecraft.getInstance();
        boolean externalDistance = TaczCompatibility.managesOwnDistance(sound);
        if (!AcousticConfig.ENABLED.get() || mc.level == null || sound.isRelative()
                || (sound.getAttenuation() == SoundInstance.Attenuation.NONE && !externalDistance)
                || sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.RECORDS
                || sound.getSource() == SoundSource.MASTER) return AcousticFrame.BYPASS;
        String id = sound.getLocation().toString();
        for (String excluded : AcousticConfig.EXCLUDED_SOUNDS.get()) {
            if (id.equals(excluded) || excluded.equals(sound.getLocation().getNamespace() + ":*")) return AcousticFrame.BYPASS;
        }
        Vec3 source = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        double distance = source.distanceTo(listener);
        if (!Double.isFinite(distance)) return AcousticFrame.BYPASS;
        var transmission = WorldAcoustics.evaluate(mc.level, source, listener, trace);
        return new AcousticFrame(true, !externalDistance, distance, transmission.gain(), AcousticConfig.LOW_PASS.get() ? transmission.highFrequency() : 1,
                AcousticConfig.REFERENCE_DISTANCE.get(), AcousticConfig.ROLLOFF.get(), AcousticConfig.SMOOTHING.get());
    }

    private AcousticManager() { }
}
