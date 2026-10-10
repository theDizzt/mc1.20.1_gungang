package dev.gungang.acoustics.mixin;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import dev.gungang.acoustics.AcousticConfig;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve antialiased coverage when oversampled monochrome glyphs are scaled. */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
    @Unique
    private boolean gungang$smooth;

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
