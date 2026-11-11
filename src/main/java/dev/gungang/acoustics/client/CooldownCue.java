package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.server.SecondaryRuntime;
import dev.gungang.acoustics.server.TurretSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The local player's secondary cooldowns coming back: plays cooldown_ready (GunGang settings: on/off in HUD, volume
 * in 음향) and marks the moment for the slot's ready flash. A charged secondary (74 Hermes' Boots) cues each charge.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = dev.gungang.acoustics.GungangAcoustics.ID, value = Dist.CLIENT)
public final class CooldownCue {
    /** Last seen cooldown and ready charges per secondary id. */
    private record Seen(long remaining, int charges) {}
    private static final Map<String, Seen> SEEN = new HashMap<>();
    /** Game time each secondary last came ready, for the slot flash. */
    private static final Map<String, Long> READY = new HashMap<>();
    /** Ticks the ready flash takes to fade. */
    static final int FLASH = 8;

    static String id(ItemStack stack) {
        return SecondaryRuntime.secondary(stack) ? stack.getTag().getString("GunGangSecondaryId") : "";
    }
    /** 0..1 strength of the ready flash on this stack's slot (1 right as it came ready). */
    static float flash(ItemStack stack, float partial) {
        var mc = Minecraft.getInstance();
        Long at = READY.get(id(stack));
        if (at == null || mc.level == null) return 0;
        float age = mc.level.getGameTime() - at + partial;
        return age < 0 || age >= FLASH ? 0 : 1 - age / FLASH;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var present = new HashMap<String, Seen>();
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            String id = id(stack);
            if (id.isEmpty() || present.containsKey(id) || stack.getTag().getInt("GunGangCooldownTicks") <= 0) continue;
            present.put(id, new Seen(SecondaryRuntime.remaining(mc.player, stack), SecondaryRuntime.charges(mc.player, stack)));
        }
        for (var entry : present.entrySet()) {
            var before = SEEN.get(entry.getKey());
            var now = entry.getValue();
            // Re-equipping or a respawn only shows the state as it is; a cue needs a change seen from before.
            if (before != null && (before.remaining() > 0 && now.remaining() == 0 || now.charges() > before.charges())) ready(mc, entry.getKey());
        }
        SEEN.clear();
        SEEN.putAll(present);
    }
    private static void ready(Minecraft mc, String id) {
        READY.put(id, mc.level.getGameTime());
        if (!AcousticConfig.COOLDOWN_SOUND.get()) return;
        float volume = AcousticConfig.COOLDOWN_SOUND_VOLUME.get() / 100f;
        if (volume > 0) mc.getSoundManager().play(SimpleSoundInstance.forUI(TurretSounds.COOLDOWN_READY.get(), 1, volume));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SEEN.clear(); READY.clear(); }
    private CooldownCue() {}
}
