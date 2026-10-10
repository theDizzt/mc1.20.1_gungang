package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Larger, shadow-free HUD lettering with width measured after styling and scaling. */
public final class HudText {
    public static void draw(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        drawScaled(graphics, value, x, y, maxWidth, color, AcousticConfig.HUD_TEXT_SIZE.get() / 100f);
    }
    public static void health(net.minecraft.client.gui.GuiGraphics g, String value, int x, int y, int width, int height, int color) {
        health(g, value, x, y, width, height, color, 1f);
    }
    public static void health(GuiGraphics g, String value, int x, int y, int width, int height, int color, float sizeMultiplier) {
        float size = Math.min(height / 8f, AcousticConfig.HUD_TEXT_SIZE.get() / 100f * 1.2f) * sizeMultiplier;
        drawScaled(g, value, x, y + Math.round((height - 8 * size) / 2), width, color, size);
    }
    private static void drawScaled(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color, float scale) {
        var font = Minecraft.getInstance().font;

        // Minecraft simulates bold by drawing a shifted second glyph. Preserve the
        // TTF's original outlines instead of duplicating its thin strokes.
        boolean bold = !AcousticConfig.CUSTOM_FONT.get() && AcousticConfig.HUD_BOLD_TEXT.get();
        Component text = Component.literal(value).withStyle(style -> style.withBold(bold));
        while (!value.isEmpty() && font.width(text) * scale > maxWidth) {
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
            text = Component.literal(value).withStyle(style -> style.withBold(bold));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
    private HudText() { }
}
