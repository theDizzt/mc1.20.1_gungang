package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 74 Hermes' Boots: double-tapping a movement key (W/A/S/D as bound) dashes that way. A movement key held on the other
 * axis at that moment turns it into a diagonal, so the dash takes one of eight directions around the view. The server
 * checks charges and the usual secondary gates ({@code HermesBoots.request}).
 */
@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class HermesInput {
    /** Second press within this many ticks of the first counts as a double tap. */
    static final int WINDOW = 6;
    private static final boolean[] down = new boolean[4];
    private static final long[] pressed = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        KeyMapping[] keys = {mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight};
        boolean usable = player != null && mc.level != null && mc.screen == null && player.isAlive() && !player.isSpectator()
                && !player.isPassenger() && !HermesBootsLayer.worn(player).isEmpty();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        boolean[] held = new boolean[4], edge = new boolean[4];
        for (int i = 0; i < 4; i++) { held[i] = keys[i].isDown(); edge[i] = held[i] && !down[i]; down[i] = held[i]; }
        for (int i = 0; i < 4; i++) {
            if (!edge[i]) continue;
            if (!usable) { pressed[i] = Long.MIN_VALUE / 2; continue; }
            if (now - pressed[i] <= WINDOW) {
                // One dash per tick even if two keys complete a double tap together.
                pressed[i] = Long.MIN_VALUE / 2;
                LobbyClient.action("hermesDash", Integer.toString(offset(i, held)), "");
                break;
            } else pressed[i] = now;
        }
    }
    /**
     * Degrees from the view for a double tap of key {@code tapped} (0 forward, 1 back, 2 left, 3 right) while the keys
     * in {@code held} are down: forward 0, right 90, back 180, left -90, diagonals between. Opposite keys cancel, never the
     * tapped one.
     */
    static int offset(int tapped, boolean[] held) {
        // The tapped key sets its own axis; a key held on the other axis adds the diagonal (both held: none).
        int forward = tapped == 0 ? 1 : tapped == 1 ? -1 : (held[0] ? 1 : 0) - (held[1] ? 1 : 0);
        int left = tapped == 2 ? 1 : tapped == 3 ? -1 : (held[2] ? 1 : 0) - (held[3] ? 1 : 0);
        int degrees = (int) Math.round(Math.toDegrees(Math.atan2(-left, forward)));
        return degrees == -180 ? 180 : degrees;
    }
    private HermesInput() { }
}
