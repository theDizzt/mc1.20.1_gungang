package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Larger, shadow-free HUD lettering with width measured after styling and scaling. */
public final class HudText {
    /** Display TTF for large HUD numbers only; small labels keep the regular font. */
    public static final ResourceLocation NUMBER_FONT = new ResourceLocation(GungangAcoustics.ID, "hud_number");

    public static Component number(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(NUMBER_FONT));
    }
    public static int numberWidth(String value) { return Minecraft.getInstance().font.width(number(value)); }
    /** Number-font text with a pure black drop shadow one glyph pixel down-right (vanilla's is only a darker tint). */
    public static void drawNumber(GuiGraphics g, Component text, int x, int y, int color) {
        var font = Minecraft.getInstance().font;
        // Keep the text's own alpha; an alpha of 0 means opaque to drawString.
        int shadow = (color & 0xFC000000) == 0 ? 0xFF000000 : color & 0xFF000000;
        g.drawString(font, text, x + 1, y + 1, shadow, false);
        g.drawString(font, text, x, y, color, false);
    }
    /** Number font on digit runs only, so units such as "3명" keep the regular font. */
    public static Component numbers(String value) {
        MutableComponent text = Component.empty();
        for (int start = 0, i = 1; i <= value.length(); i++) {
            if (i < value.length() && numeric(value.charAt(i)) == numeric(value.charAt(start))) continue;
            String run = value.substring(start, i);
            text.append(numeric(run.charAt(0)) ? number(run) : Component.literal(run));
            start = i;
        }
        return text;
    }
    private static boolean numeric(char c) { return c >= '0' && c <= '9' || ".:/+-%".indexOf(c) >= 0; }

    public static void draw(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        drawScaled(graphics, value, x, y, maxWidth, color, AcousticConfig.HUD_TEXT_SIZE.get() / 100f, HudText::styled);
    }
    public static void health(net.minecraft.client.gui.GuiGraphics g, String value, int x, int y, int width, int height, int color) {
        health(g, value, x, y, width, height, color, 1f);
    }
    public static void health(GuiGraphics g, String value, int x, int y, int width, int height, int color, float sizeMultiplier) {
        centered(g, value, x, y, width, height, color, sizeMultiplier, 8, HudText::styled);
    }
    /**
     * Same placement as {@link #health}, drawn in the number font and centred on its 7-row digits. A "123/125" label
     * draws the "/125" maximum smaller, sharing the current value's baseline; it is dropped when it does not fit.
     */
    public static void healthNumber(GuiGraphics g, String value, int x, int y, int width, int height, int color, float sizeMultiplier) {
        int slash = value.indexOf('/');
        if (slash < 0) { centered(g, value, x, y, width, height, color, sizeMultiplier, 7, HudText::number); return; }
        String current = value.substring(0, slash), maximum = value.substring(slash);
        float size = healthSize(height, sizeMultiplier), small = size * MAX_SCALE;
        int top = y + Math.round((height - 7 * size) / 2);
        int currentWidth = Math.round(numberWidth(current) * size);
        drawScaled(g, current, x, top, width, color, size, HudText::number);
        if (currentWidth + numberWidth(maximum) * small > width) return;
        var font = Minecraft.getInstance().font;
        g.pose().pushPose();
        // Bottom-aligned with the larger digits.
        g.pose().translate(x + currentWidth, top + 7 * (size - small), 0);
        g.pose().scale(small, small, 1);
        g.drawString(font, number(maximum), 0, 0, color, false);
        g.pose().popPose();
    }
    /** Size of the "/max" part of a health label relative to the current value. */
    private static final float MAX_SCALE = .6f;
    /** Drawn width of a health label as {@link #healthNumber} lays it out, at text scale 1. */
    public static float healthWidth(String value) {
        int slash = value.indexOf('/');
        return slash < 0 ? numberWidth(value) : numberWidth(value.substring(0, slash)) + numberWidth(value.substring(slash)) * MAX_SCALE;
    }
    private static float healthSize(int height, float sizeMultiplier) {
        return Math.min(height / 8f, AcousticConfig.HUD_TEXT_SIZE.get() / 100f * 1.2f) * sizeMultiplier;
    }
    private static void centered(GuiGraphics g, String value, int x, int y, int width, int height, int color, float sizeMultiplier,
                                 int rows, Function<String, Component> style) {
        float size = healthSize(height, sizeMultiplier);
        drawScaled(g, value, x, y + Math.round((height - rows * size) / 2), width, color, size, style);
    }
    private static Component styled(String value) {
        // Minecraft simulates bold by drawing a shifted second glyph. Preserve the
        // TTF's original outlines instead of duplicating its thin strokes.
        boolean bold = !AcousticConfig.CUSTOM_FONT.get() && AcousticConfig.HUD_BOLD_TEXT.get();
        return Component.literal(value).withStyle(style -> style.withBold(bold));
    }
    private static void drawScaled(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color, float scale,
                                   Function<String, Component> style) {
        var font = Minecraft.getInstance().font;
        Component text = style.apply(value);
        while (!value.isEmpty() && font.width(text) * scale > maxWidth) {
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
            text = style.apply(value);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
    private HudText() { }
}
