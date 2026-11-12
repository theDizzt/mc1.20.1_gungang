package dev.gungang.acoustics.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The stacked secondary bars above the health bar ("보조 체력바"), styled by what they show:
 * <ul>
 * <li>{@link Kind#COOLDOWN} pastel blue, hourglass: cooldowns and other countdowns ("2.4s").</li>
 * <li>{@link Kind#HEALTH} pastel green, green cross: health and shields; orange at 50 % or less, red at 25 % or less.</li>
 * <li>{@link Kind#AMMO} pastel yellow, bullet: magazines; the text turns orange at 50 % or less, red at 25 % or less.</li>
 * <li>{@link Kind#OTHER} pastel purple, star: everything else.</li>
 * </ul>
 * The icon sits in its own cell left of the bar. Inside, from the left: the value in the number font (a "100/125"
 * value draws "/125" smaller), the percentage in brackets for health and ammo, then a small Korean caption.
 */
public final class AuxBar {
    public enum Kind {
        COOLDOWN(0xFF9CCBFF), HEALTH(0xFF9FE6A8), AMMO(0xFFF6E38C), OTHER(0xFFCBB2FF);
        final int color;
        Kind(int color) { this.color = color; }
    }
    static final int WARNING = 0xFFFFC58F, DANGER = 0xFFFF9C9C;
    static final int TEXT = 0xFFFFFFFF, TEXT_WARNING = 0xFFFFAA55, TEXT_DANGER = 0xFFFF6B6B;
    /** Gap between the icon cell and the bar. */
    static final int ICON_GAP = 2;

    /** "2.4s": countdowns and other times. */
    public static String seconds(float ticks) { return String.format(Locale.ROOT, "%.1fs", Math.max(0, ticks) / 20f); }
    /** "100/125". */
    public static String ratio(float current, float maximum) { return HudHealth.number(current) + "/" + HudHealth.number(maximum); }

    /** One bar; {@code value} may be empty (caption only), {@code caption} the small Korean text after it. */
    public static void draw(GuiGraphics g, int center, int y, int width, int height, Kind kind, float current, float maximum,
                            String value, String caption, float partial) {
        float fraction = fraction(current, maximum);
        int x = center - width / 2;
        frame(g, x, y, width, height, kind, fraction);
        int fill = barColor(kind, fraction);
        HudHealthBars.fill(g, x + 1, y + 1, width - 2, height - 2, fraction, fill);
        HudHealthBars.stripes(g, x + 1, y + 1, width - 2, height - 2, fraction, time(partial));
        ShooterHud.finish(g, x + 1, y + 1, width - 2, height - 2, fraction);
        text(g, x, y, width, height, kind, fraction, value, caption);
    }
    /** Hermes-style charges: one cell per charge, the refilling one dimmer ({@code next} 0..1). */
    public static void cells(GuiGraphics g, int center, int y, int width, int height, Kind kind, int ready, int max, float next,
                             String value, String caption, float partial) {
        max = Math.max(1, max);
        int x = center - width / 2, gap = 2, inner = width - 2, cell = (inner - gap * (max - 1)) / max;
        float fraction = fraction(ready + (ready < max ? next : 0), max);
        frame(g, x, y, width, height, kind, fraction);
        int color = barColor(kind, fraction);
        double seconds = time(partial);
        for (int i = 0; i < max; i++) {
            int cx = x + 1 + i * (cell + gap), cw = i == max - 1 ? x + width - 1 - cx : cell;
            if (i > 0) g.fill(cx - gap, y + 1, cx, y + height - 1, ShooterHud.BAR_EDGE);
            float filled = i < ready ? 1 : i == ready ? next : 0;
            HudHealthBars.fill(g, cx, y + 1, cw, height - 2, filled, i < ready ? color : (color & 0xFFFFFF) | 0x80000000);
            if (i < ready) HudHealthBars.stripes(g, cx, y + 1, cw, height - 2, 1, seconds);
            ShooterHud.finish(g, cx, y + 1, cw, height - 2, filled);
        }
        text(g, x, y, width, height, kind, fraction, value, caption);
    }

    static float fraction(float current, float maximum) {
        if (!Float.isFinite(current) || !Float.isFinite(maximum) || maximum <= 0) return 0;
        return Math.max(0, Math.min(1, current / maximum));
    }
    static int barColor(Kind kind, float fraction) {
        if (kind != Kind.HEALTH) return kind.color;
        return fraction <= .25f ? DANGER : fraction <= .5f ? WARNING : kind.color;
    }
    static int textColor(Kind kind, float fraction) {
        if (kind != Kind.AMMO) return TEXT;
        return fraction <= .25f ? TEXT_DANGER : fraction <= .5f ? TEXT_WARNING : TEXT;
    }
    private static double time(float partial) {
        var player = Minecraft.getInstance().player;
        return ((player == null ? 0 : player.tickCount) + partial) / 20.0;
    }
    /** The bar card and, left of it, the icon cell. */
    private static void frame(GuiGraphics g, int x, int y, int width, int height, Kind kind, float fraction) {
        ShooterHud.card(g, x, y, width, height, ShooterHud.BAR_EDGE, ShooterHud.SURFACE, 0);
        int cell = height, ix = x - ICON_GAP - cell;
        ShooterHud.card(g, ix, y, cell, height, ShooterHud.BAR_EDGE, ShooterHud.SURFACE, 0);
        icon(g, kind, ix + 1, y + 1, cell - 2, barColor(kind, fraction));
    }

    // ---- Icons ---------------------------------------------------------------------------------------------------

    private static final String[] HOURGLASS = {
            "#########",
            ".#.....#.",
            ".##...##.",
            "..##.##..",
            "...###...",
            "..#...#..",
            ".#..#..#.",
            ".#.###.#.",
            "#########"};
    private static final String[] CROSS = {
            "...###...",
            "...###...",
            "...###...",
            "#########",
            "#########",
            "#########",
            "...###...",
            "...###...",
            "...###..."};
    private static final String[] BULLET = {
            "....#....",
            "...###...",
            "...###...",
            "..#####..",
            "..#####..",
            "..#####..",
            "..#####..",
            ".#######.",
            ".#######."};
    private static final String[] STAR = {
            "....#....",
            "....#....",
            "...###...",
            "#########",
            ".#######.",
            "..#####..",
            "..##.##..",
            ".##...##.",
            ".#.....#."};
    static String[] pattern(Kind kind) {
        return switch (kind) { case COOLDOWN -> HOURGLASS; case HEALTH -> CROSS; case AMMO -> BULLET; case OTHER -> STAR; };
    }
    /** The kind's 9×9 pixel icon, scaled into a {@code size} square, with a one-pixel dark outline. */
    private static void icon(GuiGraphics g, Kind kind, int x, int y, int size, int color) {
        var rows = pattern(kind);
        float px = size / 9f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(px, px, 1);
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++)
            if (rows[r].charAt(c) == '#') g.fill(c, r, c + 1, r + 1, color);
        g.pose().popPose();
    }

    // ---- Text ----------------------------------------------------------------------------------------------------

    /** Size of "/max" and "(40%)" relative to the value, and of the caption. */
    private static final float SMALL = .6f, CAPTION = .75f;
    private static void text(GuiGraphics g, int x, int y, int width, int height, Kind kind, float fraction, String value, String caption) {
        var font = Minecraft.getInstance().font;
        int color = textColor(kind, fraction);
        float size = Math.min(1.25f, (height - 2) / 8f);
        // Every piece sits on the digits' baseline (the number font fills 7 rows).
        float left = x + 3, right = x + width - 3, bottom = y + (height + 7 * size) / 2f;
        if (value != null && !value.isEmpty()) {
            int slash = value.indexOf('/');
            String current = slash < 0 ? value : value.substring(0, slash), maximum = slash < 0 ? "" : value.substring(slash);
            left = piece(g, HudText.number(current), left, bottom, size, 7, color);
            if (!maximum.isEmpty()) left = piece(g, HudText.number(maximum), left, bottom, size * SMALL, 7, color);
            if (slash >= 0 && (kind == Kind.HEALTH || kind == Kind.AMMO))
                left = piece(g, HudText.number("(" + Math.round(fraction * 100) + "%)"), left + 2 * size * SMALL, bottom, size * SMALL, 7, color);
            left += 3;
        }
        if (caption == null || caption.isEmpty()) return;
        float scale = size * CAPTION;
        int room = (int) Math.floor((right - left) / scale);
        if (room <= font.width("…")) return;
        String text = caption;
        if (font.width(HudText.gungang(text)) > room) {
            while (!text.isEmpty() && font.width(HudText.gungang(text + "…")) > room) text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
            text = text.strip() + "…";
        }
        // The caption's glyphs end about a row above the 9-row line's bottom.
        piece(g, HudText.gungang(text), left, bottom, scale, 8, TEXT);
    }
    /** Draws {@code text} at {@code scale} with its bottom ({@code rows} glyph rows down) on {@code bottom}, outlined; returns its right edge. */
    private static float piece(GuiGraphics g, Component text, float x, float bottom, float scale, int rows, int color) {
        var font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(x, bottom - rows * scale, 0);
        g.pose().scale(scale, scale, 1);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (dx != 0 || dy != 0) g.drawString(font, text, dx, dy, 0xFF000000, false);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
        return x + font.width(text) * scale;
    }
    private AuxBar() { }
}
