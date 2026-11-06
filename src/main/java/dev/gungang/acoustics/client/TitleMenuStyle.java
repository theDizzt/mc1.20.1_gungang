package dev.gungang.acoustics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** The loadout palette applied to the vanilla main menu without replacing its controls. */
public final class TitleMenuStyle {
    private TitleMenuStyle() { }

    public static boolean applies(AbstractWidget widget) {
        return Minecraft.getInstance().screen instanceof TitleScreen title
                && title.children().contains(widget);
    }

    private static int faded(int color, float alpha) {
        int opacity = Math.round((color >>> 24) * Math.max(0, Math.min(1, alpha)));
        return (color & 0xFFFFFF) | (opacity << 24);
    }

    private static boolean hasKey(Component text, String key) {
        return text.getContents() instanceof TranslatableContents translated && key.equals(translated.getKey());
    }

    public static void backdrop(GuiGraphics g, int width, int height, float alpha) {
        hologram(g, width, height, alpha);
        TacticalStyle.frame(g, 8, 8, width - 16, height - 16, faded(TacticalStyle.EDGE, alpha));
        g.fill(9, 9, width - 9, 10, faded(TacticalStyle.GOLD, alpha));
        g.fillGradient(10, 10, width - 10, Math.min(70, height / 3),
                faded(0x6622231B, alpha), faded(0x0022231B, alpha));

        // The menu's original positions also cover the demo and disabled-multiplayer states.
        int firstButton = height / 4 + 48;
        int left = width / 2 - 132;
        int bottom = Math.min(height - 18, firstButton + 112);
        TacticalStyle.tile(g, left, firstButton - 8, 264, bottom - firstButton + 8,
                faded(0xD9141511, alpha), faded(TacticalStyle.EDGE, alpha));
        g.fill(left + 1, firstButton - 8, left + 263, firstButton - 7, faded(TacticalStyle.GOLD, alpha));

        // Keep secondary labels out of the logo area on smaller GUI scales.
        if (width >= 600 && alpha >= 0.05F) {
            var font = Minecraft.getInstance().font;
            g.fill(22, 24, 24, 34, faded(TacticalStyle.GOLD, alpha));
            g.drawString(font, "GUNGANG / MAIN MENU", 30, 25, faded(TacticalStyle.INK, alpha), false);
            g.drawString(font, "JAVA EDITION / 1.20.1", 30, 40, faded(TacticalStyle.MUTED, alpha), false);
        }
    }

    /** Opaque procedural background: no panorama textures or per-frame asset allocations. */
    private static void hologram(GuiGraphics g, int width, int height, float alpha) {
        g.fillGradient(0, 0, width, height, 0xFF10130F, 0xFF070A08);
        g.fillGradient(0, height / 3, width, height,
                faded(0x003D4730, alpha), faded(0x243D4730, alpha));
        TacticalStyle.grid(g, 0, 0, width, height, 24, faded(0x125F7052, alpha));
        TacticalStyle.grid(g, 0, 0, width, height, 96, faded(0x246F8058, alpha));

        // Sparse illuminated registration marks keep the grid closer to a tactical display.
        for (int x = 96; x < width; x += 96) {
            for (int y = 96; y < height; y += 96) {
                int node = faded(0x466F8058, alpha);
                g.fill(x - 2, y, x + 3, y + 1, node);
                g.fill(x, y - 2, x + 1, y + 3, node);
            }
        }

        // One slow, low-contrast sweep every fourteen seconds; it never covers the controls.
        int scan = (int) ((Util.getMillis() % 14000L) * (height + 96L) / 14000L) - 48;
        int trailTop = Math.max(0, scan - 48);
        int trailBottom = Math.min(height, scan);
        if (trailBottom > trailTop) {
            g.fillGradient(0, trailTop, width, trailBottom,
                    faded(0x00D0B75E, alpha), faded(0x0CD0B75E, alpha));
        }
        if (scan >= 0 && scan < height) {
            g.fill(0, scan, width, scan + 1, faded(0x28D0B75E, alpha));
        }
        // Fine display scanlines and shaded edges sit behind the frame and menu text.
        for (int y = 0; y < height; y += 4) {
            g.fill(0, y, width, y + 1, faded(0x18000000, alpha));
        }
        g.fillGradient(0, 0, width, height / 4, faded(0x80000000, alpha), 0x00000000);
        g.fillGradient(0, height * 3 / 4, width, height, 0x00000000, faded(0x90000000, alpha));
        for (int y = 64; y < height - 40; y += 12) {
            int length = y % 48 == 16 ? 6 : 3;
            int color = faded(0x546F7051, alpha);
            g.fill(16, y, 16 + length, y + 1, color);
            g.fill(width - 16 - length, y, width - 16, y + 1, color);
        }
    }

    /** Draw only the surface; the original widget still owns input, narration and tooltips. */
    public static int button(GuiGraphics g, AbstractWidget button, float alpha) {
        int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
        boolean focused = button.active && button.isHoveredOrFocused();
        boolean quit = hasKey(button.getMessage(), "menu.quit");
        boolean primary = hasKey(button.getMessage(), "menu.singleplayer")
                || hasKey(button.getMessage(), "menu.playdemo");
        int accent = quit ? 0xFFCC3333 : TacticalStyle.GOLD;
        int edge = !button.active ? 0xFF33342D : focused || primary || quit ? accent : TacticalStyle.EDGE;
        int top = !button.active ? 0xFF151610 : quit ? (focused ? 0xFF38201B : 0xFF241A15)
                : focused ? 0xFF353222 : 0xFF23241D;
        TacticalStyle.frame(g, x, y, w, h, faded(edge, alpha));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1,
                faded(top, alpha), faded(TacticalStyle.PANEL, alpha));
        if (button.active && (focused || primary || quit)) {
            g.fill(x + 1, y + 1, x + (focused ? 3 : 2), y + h - 1, faded(accent, alpha));
            TacticalStyle.corner(g, x + w - 1, y + 1, focused ? 6 : 4, faded(accent, alpha));
        }
        return faded(!button.active ? TacticalStyle.DIM : focused || primary || quit ? accent : TacticalStyle.INK, alpha);
    }

    public static void icon(GuiGraphics g, AbstractWidget button, int color) {
        int cx = button.getX() + button.getWidth() / 2;
        int cy = button.getY() + button.getHeight() / 2;
        if (hasKey(button.getMessage(), "narrator.button.language")) {
            // Pixel globe, recognizable independently of the selected language.
            TacticalStyle.roundFrame(g, cx - 5, cy - 5, 11, 11, color);
            g.fill(cx - 5, cy, cx + 6, cy + 1, color);
            g.fill(cx, cy - 5, cx + 1, cy + 6, color);
            g.fill(cx - 3, cy - 3, cx - 2, cy + 4, color);
            g.fill(cx + 3, cy - 3, cx + 4, cy + 4, color);
        } else {
            // Accessibility figure in the same pixel geometry as the menu frame.
            g.fill(cx - 1, cy - 6, cx + 2, cy - 3, color);
            g.fill(cx - 5, cy - 2, cx + 6, cy - 1, color);
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
            g.fill(cx - 2, cy + 2, cx, cy + 5, color);
            g.fill(cx + 1, cy + 2, cx + 3, cy + 5, color);
        }
    }
}
