package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gungang.acoustics.AcousticConfig;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * TTF advances are fractions of a pixel, so the same letter landed at a different sub-pixel offset each time and
 * linear filtering drew it slightly differently. Snapping each glyph's origin to the screen pixel grid makes repeated
 * letters identical; the running pen position is untouched, so string widths do not change.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontPixelSnapMixin {
    @Shadow @Final private Matrix4f pose;

    @ModifyArg(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V"), index = 4)
    private float gungang$snapX(float x) {return gungang$snap(x, pose.m00(), pose.m30());}

    @ModifyArg(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V"), index = 5)
    private float gungang$snapY(float y) {return gungang$snap(y, pose.m11(), pose.m31());}

    private float gungang$snap(float value, float scale, float offset) {
        boolean smooth = AcousticConfig.SPEC.isLoaded() ? AcousticConfig.FONT_SMOOTHING.get() : AcousticConfig.FONT_SMOOTHING.getDefault();
        // Only flat, unrotated screen text under an orthographic projection (GUI and HUD), never text in the world.
        if (!smooth || scale <= 0 || pose.m01() != 0 || pose.m10() != 0 || RenderSystem.getProjectionMatrix().m33() != 1) return value;
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float pixel = Math.round((value * scale + offset) * guiScale);
        return (pixel / guiScale - offset) / scale;
    }
}
