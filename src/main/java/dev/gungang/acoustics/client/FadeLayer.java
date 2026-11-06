package dev.gungang.acoustics.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Draws a group of HUD elements see-through as one layer: everything between {@link #begin} and {@link #end} goes to
 * an offscreen buffer that is then composited at a single opacity, so overlapping parts don't show through each other.
 */
final class FadeLayer {
    private static TextureTarget target;

    static void begin(GuiGraphics g) {
        g.flush();
        var window = Minecraft.getInstance().getWindow();
        int w = window.getWidth(), h = window.getHeight();
        if (target == null) {
            target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
        } else if (target.width != w || target.height != h) target.resize(w, h, Minecraft.ON_OSX);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
    }

    static void end(GuiGraphics g, float opacity) {
        g.flush();
        var mc = Minecraft.getInstance();
        mc.getMainRenderTarget().bindWrite(true);
        // The buffer holds premultiplied colour (drawn with normal alpha blending onto transparent black).
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShaderColor(opacity, opacity, opacity, opacity);
        float w = g.guiWidth(), h = g.guiHeight();
        Matrix4f m = new Matrix4f();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, 0, h, 0).uv(0, 0).endVertex();
        b.vertex(m, w, h, 0).uv(1, 0).endVertex();
        b.vertex(m, w, 0, 0).uv(1, 1).endVertex();
        b.vertex(m, 0, 0, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    private FadeLayer() { }
}
