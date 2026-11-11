package dev.gungang.acoustics.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector4f;

/**
 * Banner (marquee) text: text that fits is drawn as usual; text wider than its box is clipped to the box and slides
 * back and forth, pausing at each end, instead of being cut off. Widths and positions are in the current pose's
 * units, so it works inside scaled HUD drawing; the clip is mapped through the pose to screen coordinates.
 */
public final class Marquee {
    /** Scroll speed in text pixels per second, and the pause at each end. */
    private static final float SPEED = 30f;
    private static final double PAUSE = 1.2;

    public static void draw(GuiGraphics g, Font font, String text, float x, float y, int width, int color, boolean shadow) {
        draw(g, font, Component.literal(text == null ? "" : text), x, y, width, color, shadow);
    }
    public static void draw(GuiGraphics g, Font font, Component text, float x, float y, int width, int color, boolean shadow) {
        draw(g, font, text.getVisualOrderText(), x, y, width, color, shadow);
    }
    public static void draw(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, int width, int color, boolean shadow) {
        int full = font.width(text);
        if (width <= 0) return; // No room at all: nothing, as a cut-off string would have shown.
        g.pose().pushPose();
        if (full <= width) {
            g.pose().translate(x, y, 0);
            g.drawString(font, text, 0, 0, color, shadow);
            g.pose().popPose();
            return;
        }
        g.flush();
        clip(g, x, y - 1, x + width, y + font.lineHeight + 1);
        g.pose().translate(x - offset(full - width), y, 0);
        g.drawString(font, text, 0, 0, color, shadow);
        g.flush();
        g.disableScissor();
        g.pose().popPose();
    }
    /** Whether {@code text} would scroll in a box this wide. */
    public static boolean overflows(Font font, String text, int width) { return font.width(text) > width; }

    /** How far the text has slid left (0..overflow): pause, ease across, pause, ease back. */
    static float offset(int overflow) {
        double travel = Math.max(.4, overflow / SPEED), cycle = 2 * (PAUSE + travel);
        double t = net.minecraft.Util.getMillis() / 1000.0 % cycle, p;
        if (t < PAUSE) p = 0;
        else if (t < PAUSE + travel) p = (t - PAUSE) / travel;
        else if (t < 2 * PAUSE + travel) p = 1;
        else p = 1 - (t - 2 * PAUSE - travel) / travel;
        p = p * p * (3 - 2 * p);
        return (float) (overflow * p);
    }
    /** Scissor in screen units for a rectangle in the current pose (translate/scale only). */
    private static void clip(GuiGraphics g, float x1, float y1, float x2, float y2) {
        var matrix = g.pose().last().pose();
        Vector4f a = matrix.transform(new Vector4f(x1, y1, 0, 1)), b = matrix.transform(new Vector4f(x2, y2, 0, 1));
        g.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
    }
    private Marquee() { }
}
