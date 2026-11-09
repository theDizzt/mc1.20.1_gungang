package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hit combo right of the health bar: "x12" on top, the combo's total damage ("3942.6") below.
 * Each hit within {@link #WINDOW_MS} of the previous one extends the combo; it then fades out.
 */
@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class ComboHud {
    static final long WINDOW_MS = 3000, POP_MS = 220, FADE_MS = 400;
    private static final float COUNT_SCALE = 1.6f;
    private static final int GOLD = 0xFFD84A, WHITE = 0xFFFFFF;
    private static int count;
    private static double total, shown;
    private static long last, popped;

    public static void hit(float amount) {
        if (!(amount > 0) || !Float.isFinite(amount)) return;
        long now = Util.getMillis();
        if (now - last > WINDOW_MS) { count = 0; total = 0; shown = 0; }
        count++;
        total += amount;
        last = popped = now;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { count = 0; total = shown = 0; last = 0; }

    public static boolean enabled() { return AcousticConfig.COMBO_HUD.get(); }

    /** Width reserved right of the health bar so the bar does not move when a combo starts. */
    public static int reservedWidth() {
        var font = Minecraft.getInstance().font;
        return Math.round(Math.max(font.width(HudText.number("x99")) * COUNT_SCALE * 1.2f, font.width(HudText.number("9999.9")))) + 12;
    }

    /** Draws the combo with its left edge at x, vertically around a bar at (barY, barHeight). */
    public static void render(GuiGraphics g, int x, int barY, int barHeight, int space) {
        long now = Util.getMillis(), age = now - last;
        if (count <= 0 || age > WINDOW_MS || space <= 4) return;
        var font = Minecraft.getInstance().font;
        // The total counts up quickly toward the real value after each hit.
        shown += (total - shown) * Math.min(1, 0.35);
        if (Math.abs(total - shown) < 0.05) shown = total;
        float alpha = Math.min(1, (WINDOW_MS - age) / (float) FADE_MS);
        int a = Math.max(4, Math.round(alpha * 255)) << 24;

        String combo = "x" + count;
        String damage = String.format(Locale.ROOT, "%.1f", shown);
        var comboText = HudText.number(combo);
        var damageText = HudText.number(damage);
        float fit = Math.min(1f, space / Math.max(1f, Math.max(font.width(comboText) * COUNT_SCALE, font.width(damageText))));

        // Pop: overshoot to 1.45x then ease back, flashing from white to gold.
        float t = Math.min(1, (now - popped) / (float) POP_MS);
        float ease = 1 - (1 - t) * (1 - t) * (1 - t);
        float pop = 1 + .45f * (1 - ease);
        int color = blend(WHITE, GOLD, ease);
        float scale = COUNT_SCALE * fit;
        int top = barY + barHeight / 2 - Math.round(9 * scale) - 1;
        g.pose().pushPose();
        // Grow around the left middle of the count so it stays anchored beside the bar.
        g.pose().translate(x, top + 4.5f * scale, 0);
        g.pose().scale(scale * pop, scale * pop, 1);
        HudText.drawNumber(g, comboText, 0, -5, a | color);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(x, barY + barHeight / 2f + 1, 0);
        g.pose().scale(fit, fit, 1);
        HudText.drawNumber(g, damageText, 0, 0, a | WHITE);
        // Remaining combo time as a thin line under the damage.
        int width = font.width(damageText);
        int left = Math.round(width * (1 - age / (float) WINDOW_MS));
        g.fill(0, 10, width, 11, (Math.round(alpha * 0x40) << 24));
        g.fill(0, 10, left, 11, (Math.max(4, Math.round(alpha * 0xC0)) << 24) | GOLD);
        g.pose().popPose();
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int gr = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return r << 16 | gr << 8 | b;
    }

    private ComboHud() { }
}
