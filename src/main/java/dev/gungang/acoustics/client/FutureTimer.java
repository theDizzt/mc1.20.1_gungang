package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.MatchReport;
import dev.gungang.acoustics.server.MatchSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * GUNGANG-font match clock: gold-glowing digits over a thin scan line. Every digit that
 * changes slides in from above while the old one drops away, with a brief RGB split and accent flash; the scan line
 * sweeps once per change. Same footprint as the LCD clock (centred on x, about 16 GUI pixels tall from y).
 */
final class FutureTimer {
    /** Font units to GUI pixels; the number font's 7-row digits become 14 pixels tall. */
    private static final float SCALE = 2f;
    /** Digit change animation, scan line sweep and countdown pop lengths in seconds. */
    private static final float CHANGE_SECONDS = .32f, SWEEP_SECONDS = .55f, POP_SECONDS = .35f, POP_SCALE = .5f;
    private static final int BODY = 0xFFFFFF, GOLD = 0xFFC83D, LIGHT_GOLD = 0xFFE08A, MAGENTA = 0xFF4FD8, AMBER = 0xFFB040, RED = 0xFF4A4A;
    private static final Anim MATCH = new Anim(), TEXT = new Anim();

    /** {@code brackets}: HUD corner brackets either side of the digits (escort HUD only). */
    static void draw(GuiGraphics g, MatchSnapshot state, float centerX, int y, boolean brackets) {
        int second = state.seconds();
        if (!MatchTimerHud.finalCountdown(state)) {
            render(g, MatchReport.time(second), centerX, y, MATCH, GOLD, BODY, false, brackets);
            return;
        }
        // Final ten seconds: bare number, amber turning red for the last three, popping in each second.
        render(g, String.valueOf(second), centerX, y, MATCH, second <= 3 ? RED : AMBER, 0xFFF2D8, true, brackets);
    }
    static void draw(GuiGraphics g, String text, float centerX, int y) {
        render(g, text, centerX, y, TEXT, GOLD, BODY, false, false);
    }

    private static void render(GuiGraphics g, String text, float centerX, int y, Anim a, int accent, int body, boolean countdown,
                               boolean brackets) {
        long now = System.nanoTime();
        a.update(text, now);
        float sinceChange = (now - a.changedAt) / 1e9f;
        int cell = 0;
        for (char c = '0'; c <= '9'; c++) cell = Math.max(cell, HudText.numberWidth(String.valueOf(c)));
        int colonWidth = HudText.numberWidth(":"), units = 0;
        for (int i = 0; i < text.length(); i++) units += text.charAt(i) == ':' ? colonWidth : cell;
        float half = units * SCALE / 2;

        g.pose().pushPose();
        g.pose().translate(centerX, y + 8, 0);
        if (countdown) {
            float t = Math.min(1, sinceChange / POP_SECONDS), settle = 1 - (1 - t) * (1 - t) * (1 - t);
            float pop = 1 + POP_SCALE * (1 - settle);
            g.pose().scale(pop, pop, 1);
        }
        if (brackets) frame(g, half, accent, Math.max(0, 1 - sinceChange / CHANGE_SECONDS), countdown);
        sweep(g, half, accent, sinceChange / SWEEP_SECONDS);

        // Colon stays lit for the first half of each second, so the blink follows the clock.
        float colon = sinceChange % 1f < .5f ? 1 : .35f;
        float x = -half;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int width = c == ':' ? colonWidth : cell;
            int slot = text.length() - 1 - i;
            float t = c == ':' ? 1 : Math.min(1, (now - a.at[slot]) / 1e9f / CHANGE_SECONDS);
            float e = 1 - (1 - t) * (1 - t);
            if (t < 1 && a.old[slot] != 0)
                glyph(g, a.old[slot], x, width, 4 * e, (1 - e) * .7f, accent, body, 1, 0);
            glyph(g, c, x, width, -4 * (1 - e), c == ':' ? colon : .2f + .8f * e, accent, body, e, countdown ? 1 : .55f);
            x += width * SCALE;
        }
        g.pose().popPose();
    }

    /**
     * One character centred in its cell at (x, dy) relative to the clock's vertical middle. {@code settle} runs 0..1
     * over the change animation: below 1 the glyph shows an RGB split and an accent tint.
     */
    private static void glyph(GuiGraphics g, char c, float x, int width, float dy, float alpha, int accent, int body,
                              float settle, float glow) {
        if (alpha <= .02f) return;
        String s = String.valueOf(c);
        float left = x + (width - HudText.numberWidth(s)) * SCALE / 2 + SCALE / 2, top = dy - 7.5f;
        float split = 1 - settle;
        // Soft accent glow on four sides, brighter while the digit is changing.
        float glowAlpha = alpha * (glow * .22f + .5f * split);
        if (glowAlpha > .02f) for (int k = 0; k < 4; k++)
            text(g, s, left + (k == 0 ? -.75f : k == 1 ? .75f : 0), top + (k == 2 ? -.75f : k == 3 ? .75f : 0), accent, glowAlpha);
        text(g, s, left + 1, top + 1, 0x000000, alpha * .55f);
        if (split > .02f) {
            text(g, s, left - 2 * split, top, MAGENTA, alpha * .55f * split);
            text(g, s, left + 2 * split, top, GOLD, alpha * .55f * split);
        }
        text(g, s, left, top, mix(body, accent, .65f * split), alpha);
    }
    private static void text(GuiGraphics g, String s, float x, float y, int rgb, float alpha) {
        int a = Math.round(Math.min(1, alpha) * 255);
        if (a < 5) return; // drawString reads alpha below 4 as opaque
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SCALE, SCALE, 1);
        g.drawString(Minecraft.getInstance().font, HudText.number(s), 0, 0, a << 24 | rgb & 0xFFFFFF, false);
        g.pose().popPose();
    }

    /** Corner brackets either side of the digits with a small marker dot; they kick outward on every change. */
    private static void frame(GuiGraphics g, float half, int accent, float flash, boolean countdown) {
        int bx = Math.round(half + 4 + 2 * flash * flash);
        int edge = argb(accent, .55f + .45f * flash), dot = argb(accent, .3f + .5f * flash);
        int top = -7, bottom = 7;
        for (int side = -1; side <= 1; side += 2) {
            int x = side < 0 ? -bx - 1 : bx, in = side < 0 ? 1 : -1;
            g.fill(x, top, x + 1, top + 4, edge);
            g.fill(Math.min(x, x + 3 * in), top, Math.max(x + 1, x + 1 + 3 * in), top + 1, edge);
            g.fill(x, bottom - 4, x + 1, bottom, edge);
            g.fill(Math.min(x, x + 3 * in), bottom - 1, Math.max(x + 1, x + 1 + 3 * in), bottom, edge);
            int dx = side < 0 ? x - 3 : x + 2;
            g.fill(dx, -1, dx + 1, 1, dot);
            if (countdown) g.fill(dx - side * 2, -1, dx - side * 2 + 1, 1, argb(accent, .2f + .6f * flash));
        }
    }
    /** Half-pixel scan line under the digits; a bright head runs across it after each change. */
    private static void sweep(GuiGraphics g, float half, int accent, float progress) {
        g.pose().pushPose();
        g.pose().scale(.5f, .5f, 1);
        int left = Math.round(-half * 2), right = Math.round(half * 2), y = 17;
        g.fill(left, y, right, y + 1, argb(accent, .28f));
        if (progress < 1) {
            int head = Math.round(left + (right - left) * (1 - (1 - progress) * (1 - progress)));
            float fade = 1 - progress;
            for (int k = 0; k < 4; k++) {
                int w = 6 + k * 6;
                g.fill(Math.max(left, head - w), y, Math.min(right, head + 2), y + 1, argb(LIGHT_GOLD, .3f * fade));
            }
            g.fill(Math.max(left, head - 2), y - 1, Math.min(right, head + 2), y + 2, argb(accent, .9f * fade));
        }
        g.pose().popPose();
    }

    private static int argb(int rgb, float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24 | rgb & 0xFFFFFF;
    }
    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int gr = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | gr << 8 | bl;
    }

    /** Per-slot (counted from the right, so mm:ss stays aligned) change times and the outgoing characters. */
    private static final class Anim {
        static final int SLOTS = 16;
        final char[] shown = new char[SLOTS], old = new char[SLOTS];
        final long[] at = new long[SLOTS];
        long changedAt;
        void update(String text, long now) {
            boolean changed = false;
            for (int slot = 0; slot < SLOTS; slot++) {
                char c = slot < text.length() ? text.charAt(text.length() - 1 - slot) : 0;
                if (shown[slot] == c) continue;
                old[slot] = shown[slot] == ':' ? 0 : shown[slot];
                shown[slot] = c;
                at[slot] = now;
                changed = true;
            }
            if (changed) changedAt = now;
        }
    }

    private FutureTimer() { }
}
