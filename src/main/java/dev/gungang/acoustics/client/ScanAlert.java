package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.TurretSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Shown to a player an enemy's M95 scope pulse has revealed ("m95_observed" in the utility HUD): a blue vignette
 * around the screen's edges (the middle stays clear), a blue "스캔됨" caption, and the scan sound. Each new pulse
 * (every 2.5 s while still in view) flares the vignette, which stays fainter in between and fades once the
 * observation ends; the 4-second sound does not overlap itself.
 */
public final class ScanAlert {
    /** Seconds: the vignette's flare in and out on each pulse, its fade after the observation ends, and the caption's least time. */
    private static final double FADE_IN = 0.12, GLOW = 1.4, FADE_OUT = 0.5, CAPTION = 1.6;
    /** Vignette strength kept while still observed between pulses (1 = the flare's peak). */
    private static final double HOLD = 0.45;
    /** Radius (centre 0, mid-edge 1, corner 1.41) inside which the screen stays clear, and the corners' alpha at full strength. */
    static final double CLEAR = 0.62, EDGE_ALPHA = 190;
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
        // Seconds since the observation ran out (0 while it lasts).
        double ended = active ? 0 : Math.max(0, (mc.level.getGameTime() - expires + partialTick) / 20.0);
        if (age >= Math.max(GLOW, CAPTION) && ended >= FADE_OUT) return;
        // Each scan pulse flares the vignette (fast in, eased out over GLOW); while still observed it stays at HOLD.
        double flare = age < FADE_IN ? age / FADE_IN : age < GLOW ? Math.pow(1 - (age - FADE_IN) / (GLOW - FADE_IN), 2) : 0;
        double hold = active ? HOLD : HOLD * Math.max(0, 1 - ended / FADE_OUT);
        float strength = (float) (age < FADE_IN ? flare : Math.max(flare, hold));
        if (strength > 0) {
            gui.setupOverlayRenderState(true, false);
            vignette(g, width, height, strength);
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
    /** Vignette alpha at a screen point (0..1 each way): clear in the middle, deepening blue towards the edges and corners. */
    static int edge(float x, float y, float strength) {
        double radius = Math.sqrt(Math.pow(x * 2 - 1, 2) + Math.pow(y * 2 - 1, 2));
        double t = Math.max(0, Math.min(1, (radius - CLEAR) / (1.42 - CLEAR)));
        return (int) Math.round(EDGE_ALPHA * strength * t * t * (3 - 2 * t));
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
