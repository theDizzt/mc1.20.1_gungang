package dev.gungang.acoustics.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.BossEvent;

public final class BossBarHud {
    public static int bottom = 0;
    public static void beginFrame() { bottom = 0; }
    public static void draw(GuiGraphics g, int x, int y, BossEvent boss, int color) {
        var mc = Minecraft.getInstance();
        bottom = Math.max(bottom, y + 9);
        double seconds = mc.level == null ? 0 : (mc.level.getGameTime() + mc.getFrameTime()) / 20.0;
        float progress = Math.max(0, Math.min(1, boss.getProgress()));
        // LerpingBossEvent already interpolates network progress over 100 ms.
        // Retargeting a second ease on every frame prevents it from keeping up.
        float displayed = progress;
        ShooterHud.panel(g, x, y, 182, 9, 0xFF60696D, 0x8029353E);
        HudHealthBars.fill(g, x + 1, y + 1, 180, 7, displayed, color);
        HudHealthBars.stripes(g, x + 1, y + 1, 180, 7, displayed, seconds * 1.8);
        int segments = switch (boss.getOverlay()) {
            case PROGRESS -> 0;
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
        };
        for (int i = 1; i < segments; i++) {
            int notch = x + 1 + Math.round(180f * i / segments);
            g.fill(notch, y + 1, notch + 1, y + 8, 0xFF60696D);
        }
        label(g, String.format(Locale.ROOT, "%.1f", displayed * 100), x + 91, y + 1);
    }
    /** GUNGANG digits are 7 rows tall: scaled so they fill 5 of the bar's 7 inner rows. */
    private static final float SIZE = 5f / 7f, PERCENT = .7f;
    /** Extra font pixels between glyphs, so the one-pixel black outlines do not run the digits together. */
    private static final int TRACKING = 1;
    /**
     * The percentage in the GUNGANG number font, centred on {@code centerX} inside the 7-row bar starting at {@code top}.
     * Digits keep one cell width (the font's "1" is narrower) so the label does not jitter as it counts; the wide "%"
     * glyph is drawn smaller and bottom-aligned.
     */
    private static void label(GuiGraphics g, String value, int centerX, int top) {
        int cell = 0;
        for (char c = '0'; c <= '9'; c++) cell = Math.max(cell, HudText.numberWidth(String.valueOf(c)));
        cell += TRACKING;
        int width = 0;
        for (int i = 0; i < value.length(); i++) width += Character.isDigit(value.charAt(i)) ? cell : HudText.numberWidth(value.substring(i, i + 1)) + TRACKING;
        float percentWidth = HudText.numberWidth("%") * PERCENT, total = width * SIZE + percentWidth * SIZE;
        g.pose().pushPose();
        g.pose().translate(centerX - total / 2f, top + (7 - 7 * SIZE) / 2f, 0);
        g.pose().scale(SIZE, SIZE, 1);
        int x = 0;
        for (int i = 0; i < value.length(); i++) {
            String c = value.substring(i, i + 1);
            int w = Character.isDigit(c.charAt(0)) ? cell : HudText.numberWidth(c) + TRACKING;
            outlined(g, c, x + (w - TRACKING - HudText.numberWidth(c)) / 2, 0);
            x += w;
        }
        g.pose().translate(x, 7 * (1 - PERCENT), 0);
        g.pose().scale(PERCENT, PERCENT, 1);
        outlined(g, "%", 0, 0);
        g.pose().popPose();
    }
    private static void outlined(GuiGraphics g, String value, int x, int y) {
        var font = Minecraft.getInstance().font;
        var text = HudText.number(value);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (dx != 0 || dy != 0) g.drawString(font, text, x + dx, y + dy, 0xFF000000, false);
        g.drawString(font, text, x, y, 0xFFFFFFFF, false);
    }
    private BossBarHud() { }
}
