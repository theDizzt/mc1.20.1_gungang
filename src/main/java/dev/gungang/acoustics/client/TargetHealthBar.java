package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class TargetHealthBar {
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(2048));

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        // Prop hunt: overhead bars would give hidden players away.
        if (!ShooterHud.visible(mc) || mc.getCameraEntity() == null || PropHuntClient.active()) return;
        Vec3 eye = event.getCamera().getPosition();
        boolean showNumbers = !MatchHud.active();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || target == mc.player
                    || target.isSpectator() || target.isInvisibleTo(mc.player)
                    || (target instanceof dev.gungang.acoustics.server.DeployableEntity deployed && !deployed.showHealthBar())
                    // Allies, bots included, show in the top-left roster instead.
                    || TeamHud.teammate(target)
                    || target.distanceToSqr(mc.player) > 96 * 96) continue;
            var pose = event.getPoseStack();
            Vec3 position = target.getPosition(event.getPartialTick());
            // Keep the bar below vanilla name labels; the render type uses normal depth testing.
            double top = target.getBbHeight() + 0.22;
            if(target instanceof dev.gungang.acoustics.server.DeployableEntity deployed && deployed.wall())top+=3;
            pose.pushPose();
            pose.translate(position.x - eye.x, position.y + top - eye.y, position.z - eye.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f matrix = pose.last().pose();
            VertexConsumer vertices = BUFFER.getBuffer(RenderType.textBackground());
            rectangle(vertices, matrix, -30, -4, 30, -3, 0xFF60696D);
            rectangle(vertices, matrix, -30, 3, 30, 4, 0xFF60696D);
            rectangle(vertices, matrix, -30, -3, -29, 3, 0xFF60696D);
            rectangle(vertices, matrix, 29, -3, 30, 3, 0xFF60696D);
            var layers = HudHealthBars.layers(target, event.getPartialTick());
            float fraction = HudHealth.fraction(target.getHealth(), target.getMaxHealth());
            int color = fraction <= 0.25f ? 0xFFFF9292 : fraction <= 0.5f ? 0xFFFFDC98 : 0xFFF5F5F0;
            float[] edges = layers.boundaries();
            for (int i = 1; i < edges.length; i++) {
                float start = edges[i - 1], end = edges[i];
                if (end > start) rectangle(vertices, matrix, -29 + 58 * start, -3, -29 + 58 * end, 3,
                        layers.colorAt((start + end) / 2, color));
            }
            if (showNumbers) healthNumbers(mc, pose, target, color);
            pose.popPose();
        }
        BUFFER.endBatch();
    }

    /** Outside a match, right-align the whole health label just to the left of the bar. */
    private static void healthNumbers(Minecraft mc, com.mojang.blaze3d.vertex.PoseStack pose, LivingEntity target, int color) {
        var current = HudText.gungang(HudHealth.number(target.getHealth()));
        var maximum = HudText.gungang("/" + HudHealth.number(target.getMaxHealth()));
        float small = .65f;
        float currentWidth = mc.font.width(current), maxWidth = mc.font.width(maximum) * small;
        float left = -34 - currentWidth - maxWidth;
        mc.font.drawInBatch(current, left, -4, color, true, pose.last().pose(), BUFFER,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        pose.pushPose();
        // Align the smaller suffix to the current-health text's baseline.
        pose.translate(left + currentWidth, -4 + 7 * (1 - small), 0);
        pose.scale(small, small, 1);
        mc.font.drawInBatch(maximum, 0, 0, color, true, pose.last().pose(), BUFFER,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        pose.popPose();
    }

    private static void rectangle(VertexConsumer out, Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255, alpha = color >>> 24;
        out.vertex(matrix, x0, y0, 0).color(red, green, blue, alpha).uv2(15728880).endVertex();
        out.vertex(matrix, x0, y1, 0).color(red, green, blue, alpha).uv2(15728880).endVertex();
        out.vertex(matrix, x1, y1, 0).color(red, green, blue, alpha).uv2(15728880).endVertex();
        out.vertex(matrix, x1, y0, 0).color(red, green, blue, alpha).uv2(15728880).endVertex();
    }

    private TargetHealthBar() { }
}
