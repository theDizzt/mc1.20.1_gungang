package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.gungang.acoustics.AcousticConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Draw black without depth before the ordinary model covers its visible portions. */
public final class TeamSilhouette extends RenderType {
    private static final RenderType BLACK = create("gungang_team_silhouette", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(new TextureStateShard(new ResourceLocation("minecraft", "textures/block/white_concrete.png"), false, false))
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(32768));
    public static void lifeGlow(LivingEntity entity,EntityModel<?> model,PoseStack pose,MultiBufferSource buffers) {
        if(!(entity instanceof Player p))return;
        var state=dev.gungang.acoustics.server.SurvivalGadgets.clientState(p);
        if(state==null||state.lifeEnd()<=p.level().getGameTime()||!p.isAlive()||p.isSpectator())return;
        int color=state.color();pose.pushPose();pose.scale(1.003f,1.003f,1.003f);
        model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityTranslucent(new ResourceLocation("minecraft","textures/block/white_concrete.png"))),
                15728880,OverlayTexture.NO_OVERLAY,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,.65f);
        pose.popPose();
    }

    public static void capture(LivingEntity entity, EntityModel<?> model, PoseStack pose) {
        var mc = Minecraft.getInstance();
        if (!AcousticConfig.TEAM_SILHOUETTES.get() || mc.options.hideGui
                || !(entity instanceof Player player) || !TeamHud.teammate(player) || !player.isAlive()) return;
        model.renderToBuffer(pose, BUFFER.getBuffer(BLACK), 15728880, OverlayTexture.NO_OVERLAY, 0, 0, 0, 1);
        BUFFER.endBatch();
    }

    private TeamSilhouette(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                           boolean affects, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, affects, sort, setup, clear);
    }
}
