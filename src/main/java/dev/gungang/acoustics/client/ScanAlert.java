package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.TurretSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Shown to a player an enemy's M95 scope pulse has revealed ("m95_observed" in the utility HUD): a blue scan sweep
 * over the screen with a blue vignette, a blue "스캔됨" caption, and the scan sound. Each new pulse (every 2.5 s
 * while still in view) replays the sweep; the 4-second sound does not overlap itself.
 */
public final class ScanAlert {
    /** Length of the sweep and vignette, and how long the caption stays at least. */
    private static final double SWEEP = 0.7, GLOW = 1.4, CAPTION = 1.6;
    /** The scan sound's length: it is not restarted while still playing. */
    private static final long SOUND_NANOS = 4_000_000_000L;
    private static final int BLUE = 0x4FA8FF;
    /** Game tick the current observation ends (from the server), and when the latest sweep / sound started. */
    private static long expires;
    private static long sweepStart, soundStart;

    /** The utility HUD arrived at {@code now} with an observation lasting {@code remaining} more ticks. */
    static void observed(long now, float remaining) {
        long end = now + (long) Math.ceil(remaining);
        // A fresh pulse extends the observation; other HUD refreshes repeat the same end.
        if (end > expires + 5) {
            long time = System.nanoTime();
            sweepStart = time;
            if (time - soundStart >= SOUND_NANOS) {
                soundStart = time;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(TurretSounds.SCAN.get(), 1, 1));
            }
        }
        expires = Math.max(expires, end);
    }
    static void clear() { expires = sweepStart = soundStart = 0; }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        var mc = Minecraft.getInstance();
        if (sweepStart == 0 || mc.level == null || mc.player == null || !ShooterHud.visible(mc)) return;
        double age = (System.nanoTime() - sweepStart) / 1_000_000_000.0;
        boolean active = mc.level.getGameTime() < expires;
        if (age >= Math.max(GLOW, CAPTION) && !active) return;
        if (age < GLOW) {
            gui.setupOverlayRenderState(true, false);
            float strength = (float) Math.pow(1 - age / GLOW, 1.5);
            vignette(g, width, height, strength);
            lines(g, width, height, strength);
            if (age < SWEEP) sweep(g, width, height, (float) (age / SWEEP));
            g.flush();
        }
        // The caption stays while still observed, then fades.
        float fade = active ? 1 : (float) Math.max(0, 1 - (age - CAPTION) / .4);
        if (age < CAPTION) fade = 1;
        if (fade > 0) caption(g, width, height, fade);
    }
    /** Blue glow at the screen edges. */
    private static void vignette(GuiGraphics g, int width, int height, float strength) {
        var matrix = g.pose().last().pose();
        var vertices = g.bufferSource().getBuffer(RenderType.gui());
        int columns = 32, rows = 18, r = BLUE >> 16 & 255, gr = BLUE >> 8 & 255, b = BLUE & 255;
        for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) {
            float x0 = (float) col / columns, x1 = (float) (col + 1) / columns, y0 = (float) row / rows, y1 = (float) (row + 1) / rows;
            vertices.vertex(matrix, x0 * width, y0 * height, 0).color(r, gr, b, edge(x0, y0, strength)).endVertex();
            vertices.vertex(matrix, x0 * width, y1 * height, 0).color(r, gr, b, edge(x0, y1, strength)).endVertex();
            vertices.vertex(matrix, x1 * width, y1 * height, 0).color(r, gr, b, edge(x1, y1, strength)).endVertex();
            vertices.vertex(matrix, x1 * width, y0 * height, 0).color(r, gr, b, edge(x1, y0, strength)).endVertex();
        }
    }
    private static int edge(float x, float y, float strength) {
        double radius = Math.sqrt(Math.pow(x * 2 - 1, 2) + Math.pow(y * 2 - 1, 2));
        double t = Math.max(0, Math.min(1, (radius - 0.5) / 0.7));
        return (int) (150 * strength * t * t * (3 - 2 * t));
    }
    /** Faint horizontal scan lines over the whole screen. */
    private static void lines(GuiGraphics g, int width, int height, float strength) {
        int alpha = Math.round(28 * strength);
        if (alpha <= 0) return;
        for (int y = 0; y < height; y += 4) g.fill(0, y, width, y + 1, alpha << 24 | BLUE);
    }
    /** The bright scan line moving from top to bottom ({@code progress} 0..1) with a fading trail above it. */
    private static void sweep(GuiGraphics g, int width, int height, float progress) {
        int y = Math.round(progress * height), trail = Math.max(12, height / 6);
        for (int i = 0; i < trail; i++) {
            int alpha = Math.round(90f * (1 - (float) i / trail));
            g.fill(0, y - i - 1, width, y - i, alpha << 24 | BLUE);
        }
        g.fill(0, y - 1, width, y + 1, 0xE0000000 | 0xBFE3FF);
    }
    /** "스캔됨" in large blue letters, a little above the crosshair. */
    private static void caption(GuiGraphics g, int width, int height, float fade) {
        var font = Minecraft.getInstance().font;
        String text = Lang.tr("스캔됨");
        float scale = 2f;
        int alpha = Math.max(4, Math.round(255 * fade)), color = alpha << 24 | BLUE, outline = Math.max(4, Math.round(200 * fade)) << 24;
        g.pose().pushPose();
        g.pose().translate(width / 2f - font.width(text) * scale / 2f, height * .3f, 0);
        g.pose().scale(scale, scale, 1);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (dx != 0 || dy != 0) g.drawString(font, text, dx, dy, outline, false);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
    private ScanAlert() { }
}
