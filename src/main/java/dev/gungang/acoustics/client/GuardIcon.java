package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.TwinBlades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The 경감 shield icon over the head of anyone guarding with the twin blades (right click), so everyone can see the
 * guard is up. The guard state is TaCZ's synced aiming flag, so every client knows it without a packet.
 */
@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class GuardIcon {
    private static final ResourceLocation TEXTURE = new ResourceLocation("gungang", "textures/gui/effect/reduction.png");
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(256));
    /** Half the icon's width in blocks, and its centre above the head (over the health bar and name). */
    private static final float HALF = 0.22f, ABOVE = 0.95f;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || PropHuntClient.active()) return;
        Vec3 eye = event.getCamera().getPosition();
        boolean drawn = false;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity body) || !body.isAlive() || body.isSpectator() || body.isInvisibleTo(mc.player)) continue;
            // Your own guard shows in your health bar; over your head only in third person.
            if (body == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
            if (body.distanceToSqr(eye) > 64 * 64 || !TwinBlades.guarding(body)) continue;
            Vec3 at = body.getPosition(event.getPartialTick());
            var pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(at.x - eye.x, at.y + body.getBbHeight() + ABOVE - eye.y, at.z - eye.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            // As vanilla name tags: x right, y down on screen.
            pose.scale(-1, -1, 1);
            var matrix = pose.last().pose();
            var out = BUFFER.getBuffer(RenderType.text(TEXTURE));
            out.vertex(matrix, -HALF, -HALF, 0).color(255, 255, 255, 255).uv(0, 0).uv2(15728880).endVertex();
            out.vertex(matrix, -HALF, HALF, 0).color(255, 255, 255, 255).uv(0, 1).uv2(15728880).endVertex();
            out.vertex(matrix, HALF, HALF, 0).color(255, 255, 255, 255).uv(1, 1).uv2(15728880).endVertex();
            out.vertex(matrix, HALF, -HALF, 0).color(255, 255, 255, 255).uv(1, 0).uv2(15728880).endVertex();
            pose.popPose();
            drawn = true;
        }
        if (drawn) BUFFER.endBatch();
    }

    private GuardIcon() { }
}
