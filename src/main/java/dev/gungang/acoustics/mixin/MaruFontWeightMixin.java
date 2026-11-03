package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.FontPreset;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A subpixel ink pass for the light Maru face; preserves advances and line wrapping. */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class MaruFontWeightMixin {
    @Unique private boolean gungang$maru;

    @Inject(method = "accept", at = @At("HEAD"))
    private void gungang$selectFace(int index, Style style, int codepoint, CallbackInfoReturnable<Boolean> callback) {
        boolean loaded = AcousticConfig.SPEC.isLoaded();
        boolean custom = loaded ? AcousticConfig.CUSTOM_FONT.get() : AcousticConfig.CUSTOM_FONT.getDefault();
        FontPreset preset = loaded ? AcousticConfig.FONT_PRESET.get() : AcousticConfig.FONT_PRESET.getDefault();
        gungang$maru = custom && preset == FontPreset.MARU_MINYA && Style.DEFAULT_FONT.equals(style.getFont());
    }

    // Individual argument hooks avoid synthetic Args classes, which failed to load
    // in the production renderer stack (NoClassDefFoundError: synthetic.args.Args$1).
    @ModifyArg(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V"), index = 1)
    private boolean gungang$ink(boolean bold) {
        return gungang$maru || bold;
    }

    @ModifyArg(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V"), index = 3)
    private float gungang$inkOffset(float offset) {
        // Vanilla's full bold offset is too heavy for the 12px face. At GUI scale 3
        // this adds about one physical pixel, within the existing inter-glyph gap.
        return gungang$maru ? .35f : offset;
    }
}
