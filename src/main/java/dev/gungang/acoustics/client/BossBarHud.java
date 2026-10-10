package dev.gungang.acoustics.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.BossEvent;

public final class BossBarHud {
    public static int bottom = 14;
    public static void beginFrame() { bottom = 14; }
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
        String label = String.format(Locale.ROOT, "%.1f%%", displayed * 100);
        g.pose().pushPose();
        g.pose().translate(x + 91, y + 2, 0);
        g.pose().scale(0.6f, 0.6f, 1);
        int left = -mc.font.width(label) / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (dx != 0 || dy != 0) g.drawString(mc.font, label, left + dx, dy, 0xFF000000, false);
        g.drawString(mc.font, label, left, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
    }
    private BossBarHud() { }
}
