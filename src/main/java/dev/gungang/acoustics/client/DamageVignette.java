package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class DamageVignette {
    private static Player tracked;
    private static float health, absorption, maximum;
    private static int hurtTime;
    private static long lastHit;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = Minecraft.getInstance().player;
        if (player != tracked) {
            tracked = player;
            lastHit = 0;
            if (player == null) return;
            health = player.getHealth();
            absorption = player.getAbsorptionAmount();
            maximum = player.getMaxHealth();
        }
        if (player == null) return;
        boolean damaged = player.getHealth() < Math.min(health, player.getMaxHealth())
                || (player.getAbsorptionAmount() < absorption && player.hurtTime > hurtTime);
        if (damaged && maximum == player.getMaxHealth()) lastHit = System.nanoTime();
        health = player.getHealth();
        absorption = player.getAbsorptionAmount();
        maximum = player.getMaxHealth();
        hurtTime = player.hurtTime;
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!ShooterHud.visible(mc) || lastHit == 0) return;
        double age = (System.nanoTime() - lastHit) / 1_000_000_000.0;
        if (age >= 1.1) return;
        float strength = (float) (0.72 * Math.pow(1 - age / 1.1, 2));
        int red = Math.round(220 * (1 - HudHealth.fraction(mc.player.getHealth(), mc.player.getMaxHealth())));
        gui.setupOverlayRenderState(true, false);
        var matrix = graphics.pose().last().pose();
        var vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        int columns = 40, rows = 24;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                float x0 = (float) col / columns, x1 = (float) (col + 1) / columns;
                float y0 = (float) row / rows, y1 = (float) (row + 1) / rows;
                vertices.vertex(matrix, x0 * width, y0 * height, 0).color(red, 0, 0, alpha(x0, y0, strength)).endVertex();
                vertices.vertex(matrix, x0 * width, y1 * height, 0).color(red, 0, 0, alpha(x0, y1, strength)).endVertex();
                vertices.vertex(matrix, x1 * width, y1 * height, 0).color(red, 0, 0, alpha(x1, y1, strength)).endVertex();
                vertices.vertex(matrix, x1 * width, y0 * height, 0).color(red, 0, 0, alpha(x1, y0, strength)).endVertex();
            }
        }
        graphics.flush();
    }

    private static int alpha(float x, float y, float strength) {
        double radius = Math.sqrt(Math.pow(x * 2 - 1, 2) + Math.pow(y * 2 - 1, 2));
        double edge = Math.max(0, Math.min(1, (radius - 0.45) / 0.75));
        return (int) (255 * strength * edge * edge * (3 - 2 * edge));
    }

    private DamageVignette() { }
}
