package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.client.PaddedGlyph;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve antialiased coverage when oversampled monochrome glyphs are scaled. */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
    @Shadow @Final private boolean colored;
    @Unique
    private boolean gungang$smooth;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gungang$clear(CallbackInfo callback) {
        // A new GL texture holds undefined memory, often an old atlas. Filtering reads the gaps between glyphs,
        // so leftovers showed up as thin lines around characters.
        NativeImage empty = new NativeImage(colored ? NativeImage.Format.RGBA : NativeImage.Format.LUMINANCE, 256, 256, true);
        bind();
        empty.upload(0, 0, 0, 0, 0, 256, 256, false, true);
    }

    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true)
    private SheetGlyphInfo gungang$pad(SheetGlyphInfo glyph) {
        return PaddedGlyph.wanted(glyph) ? new PaddedGlyph(glyph) : glyph;
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void gungang$filterGlyph(SheetGlyphInfo glyph, CallbackInfoReturnable<BakedGlyph> callback) {
        if (callback.getReturnValue() == null) return;
        // TTF coverage uses a monochrome atlas, separate from colored bitmap fonts.
        gungang$smooth |= !glyph.isColored() && glyph.getOversample() > 1.0f;
        // NativeImage.upload resets filtering each time a new glyph is uploaded.
        if (gungang$smooth) setFilter(true, false);
    }

    @Override
    public void setFilter(boolean blur, boolean mipmap) {
        // Text render states request nearest filtering again when binding the atlas.
        // Font atlases have no mip chain; keep the high-resolution coverage linear.
        boolean enabled = AcousticConfig.SPEC.isLoaded()
                ? AcousticConfig.FONT_SMOOTHING.get() : AcousticConfig.FONT_SMOOTHING.getDefault();
        boolean smooth = gungang$smooth && enabled;
        super.setFilter(gungang$smooth ? smooth : blur, smooth ? false : mipmap);
    }
}
