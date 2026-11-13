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

    /** Plain GUNGANG (Latin, digits, symbols) with Galmuri11 Bold for everything else (Hangul), for weapon ids and specs, independent of the font preset. */
    public static final ResourceLocation UI_FONT = new ResourceLocation(GungangAcoustics.ID, "gungang_ui");
    public static MutableComponent gungang(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(UI_FONT));
    }
    /** The longest prefix of value that fits maxWidth in {@link #UI_FONT}. */
    public static String fitGungang(String value, int maxWidth) {
        var font = Minecraft.getInstance().font;
        String text = value;
        while (!text.isEmpty() && font.width(gungang(text)) > maxWidth) text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        return text;
    }
    public static Component number(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(NUMBER_FONT));
    }
    public static int numberWidth(String value) { return Minecraft.getInstance().font.width(number(value)); }
    /** Number-font text with a translucent black drop shadow one glyph pixel down-right (vanilla's is only a darker tint). */
    public static void drawNumber(GuiGraphics g, Component text, int x, int y, int color) {
        var font = Minecraft.getInstance().font;
        // Translucent black: half the text's own alpha (an alpha below 4 means opaque to drawString), never below 4
        // itself, which drawString would also read as opaque.
        int alpha = (color & 0xFC000000) == 0 ? 255 : color >>> 24;
        int shadow = Math.max(4, alpha * 0x80 / 255) << 24;
        g.drawString(font, text, x + 1, y + 1, shadow, false);
        g.drawString(font, text, x, y, color, false);
    }
    /** Small number-font label (e.g. a prop catalog id) scaled by {@code size} and right-aligned to {@code right}. */
    public static void smallNumber(GuiGraphics g, String value, int right, int y, float size, int color) {
        g.pose().pushPose();
        g.pose().translate(right - numberWidth(value) * size, y, 0);
        g.pose().scale(size, size, 1);
        drawNumber(g, number(value), 0, 0, color);
        g.pose().popPose();
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
    /** "00:00"-style clock text (also "1:05:09"); drawn with {@link #drawTime} so the digits keep fixed cells. */
    public static boolean time(String value) { return value.matches("[0-9]+(:[0-9][0-9])+"); }
    private static int digitCell() {
        int cell = 0;
        for (char c = '0'; c <= '9'; c++) cell = Math.max(cell, numberWidth(String.valueOf(c)));
        return cell;
    }
    /** Width of {@code value} in the number font with tabular digits. */
    public static int timeWidth(String value) {
        int cell = digitCell(), colon = numberWidth(":"), width = 0;
        for (int i = 0; i < value.length(); i++) width += value.charAt(i) == ':' ? colon : cell;
        return width;
    }
    /** Number-font clock with every digit centred in the widest digit's cell, so the time never shifts as it counts. */
    public static void drawTime(GuiGraphics g, String value, int x, int y, int color) {
        int cell = digitCell(), colon = numberWidth(":");
        for (int i = 0; i < value.length(); i++) {
            String c = String.valueOf(value.charAt(i));
            int w = c.equals(":") ? colon : cell;
            drawNumber(g, number(c), x + (w - numberWidth(c)) / 2, y, color);
            x += w;
        }
    }
    private static boolean numeric(char c) { return c >= '0' && c <= '9' || ".:/+-%".indexOf(c) >= 0; }

    public static void draw(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        drawScaled(graphics, value, x, y, maxWidth, color, AcousticConfig.HUD_TEXT_SIZE.get() / 100f, HudText::styled);
    }
    /**
     * {@link #draw} that never cuts letters off: a value wider than maxWidth is shrunk to fit and kept vertically
     * centred on the line it would have used (nicknames).
     */
    public static void drawFit(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        var font = Minecraft.getInstance().font;
        float size = AcousticConfig.HUD_TEXT_SIZE.get() / 100f;
        int width = Math.max(1, font.width(styled(value)));
        float scale = Math.min(size, maxWidth / (float) width);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + font.lineHeight * (size - scale) / 2f, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, styled(value), 0, 0, color, false);
        graphics.pose().popPose();
    }
    public static void health(net.minecraft.client.gui.GuiGraphics g, String value, int x, int y, int width, int height, int color) {
        health(g, value, x, y, width, height, color, 1f);
    }
    public static void health(GuiGraphics g, String value, int x, int y, int width, int height, int color, float sizeMultiplier) {
        centered(g, value, x, y, width, height, color, sizeMultiplier, 8, HudText::styled);
    }
    /** {@link #health} with a black outline one font pixel thick, for labels over bright bars. */
    public static void gungangOutlined(GuiGraphics g, String value, int x, int y, int width, int height, int color) {
        float px = healthSize(height, 1f);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) continue;
            g.pose().pushPose();
            g.pose().translate(dx * px, dy * px, 0);
            centered(g, value, x, y, width, height, 0xFF000000, 1f, 8, HudText::gungang);
            g.pose().popPose();
        }
        centered(g, value, x, y, width, height, color, 1f, 8, HudText::gungang);
    }
    public static void healthOutlined(GuiGraphics g, String value, int x, int y, int width, int height, int color) {
        float px = healthSize(height, 1f);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) continue;
            g.pose().pushPose();
            g.pose().translate(dx * px, dy * px, 0);
            health(g, value, x, y, width, height, 0xFF000000);
            g.pose().popPose();
        }
        health(g, value, x, y, width, height, color);
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
    /** Width {@link #healthNumber} draws {@code value} at in a box {@code height} tall (size multiplier 1). */
    public static float healthDrawnWidth(String value, int height) { return healthWidth(value) * healthSize(height, 1f); }
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
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        // Too wide for maxWidth: scrolls as a banner (Marquee) instead of losing its end.
        Marquee.draw(graphics, font, text, 0, 0, (int) Math.floor(maxWidth / scale), color, false);
        graphics.pose().popPose();
    }
    private HudText() { }
}
