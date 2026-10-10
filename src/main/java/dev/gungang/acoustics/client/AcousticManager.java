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
import net.minecraft.world.entity.player.Player;
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
        PENDING.put(sound, frame(sound, initialTraces++ < dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.UPDATE_BUDGET)));
    }

    @SubscribeEvent
    public static void onStatic(PlaySoundSourceEvent event) { attach(event.getSound(), event.getChannel()); }

    @SubscribeEvent
    public static void onStream(PlayStreamingSourceEvent event) { attach(event.getSound(), event.getChannel()); }

    private static void attach(SoundInstance sound, Channel channel) {
        CompassHud.soundPlayed(sound);
        AcousticFrame frame = PENDING.remove(sound);
        if (frame != null) ((AcousticChannel) channel).gungang$apply(frame);
    }

    public static void update(Map<SoundInstance, ChannelAccess.ChannelHandle> channels, boolean paused) {
        Minecraft mc = Minecraft.getInstance();
        if (paused || !mc.isSameThread()) return;
        if (mc.level == null || channels.isEmpty()) { PENDING.clear(); cursor = 0; return; }
        // A snapshot preserves the engine's sound identity and looping/stopping behavior.
        var entries = channels.entrySet().toArray(Map.Entry[]::new);
        int count = Math.min(dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.UPDATE_BUDGET), entries.length);
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
        if (mc.level == null || sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.RECORDS
                || sound.getSource() == SoundSource.MASTER) return AcousticFrame.BYPASS;
        String id = sound.getLocation().toString();
        for (String excluded : dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.EXCLUDED_SOUNDS)) {
            if (id.equals(excluded) || excluded.equals(sound.getLocation().getNamespace() + ":*")) return AcousticFrame.BYPASS;
        }
        double volumeScale = SoundVolume.scale(sound.getLocation().getPath(), externalDistance,
                dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.FOOTSTEP_VOLUME), dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.WEAPON_VOLUME));
        AcousticFrame volumeOnly = AcousticFrame.volumeOnly(volumeScale);
        if (!dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.ENABLED) || (sound.isRelative() && !externalDistance)
                || (sound.getAttenuation() == SoundInstance.Attenuation.NONE && !externalDistance)) return volumeOnly;
        Vec3 source = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        // The physical listener stays at the player's ears in first AND third person.
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 listener = mc.player != null ? mc.player.getEyePosition() : camera;
        if (sound.isRelative()) source = listener;
        double distance = source.distanceTo(listener);
        if (!Double.isFinite(distance)) return volumeOnly;
        Player emitter = null;
        if (externalDistance || sound.getSource() == SoundSource.PLAYERS) {
            double closest = Double.POSITIVE_INFINITY;
            for (Player candidate : mc.level.players()) {
                if (candidate.isSpectator() || !candidate.isAlive() || !candidate.getBoundingBox().inflate(0.8).contains(source)) continue;
                double separation = candidate.position().distanceToSqr(source);
                if (separation < closest) { closest = separation; emitter = candidate; }
            }
        }
        boolean clearFootsteps = !externalDistance && !dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.FOOTSTEP_OCCLUSION)
                && SoundVolume.isFootstep(sound.getLocation().getPath());
        var transmission = clearFootsteps || (emitter == mc.player && emitter != null) ? new WorldAcoustics.Transmission(1, 1)
                : WorldAcoustics.evaluate(mc.level, source, listener, camera, emitter, trace);
        if (dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.WATER_EFFECTS)) {
            var water = WaterAcoustics.evaluate(mc.level, source, listener);
            transmission = new WorldAcoustics.Transmission(transmission.gain() * water.gain(),
                    transmission.highFrequency() * water.highFrequency());
        }
        return new AcousticFrame(true, !externalDistance, distance, transmission.gain(), dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.LOW_PASS) ? transmission.highFrequency() : 1,
                dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.REFERENCE_DISTANCE), dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.ROLLOFF), dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.SMOOTHING), volumeScale);
    }

    private AcousticManager() { }
}
