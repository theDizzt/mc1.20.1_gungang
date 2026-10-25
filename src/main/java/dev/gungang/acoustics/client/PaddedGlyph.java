package dev.gungang.acoustics.client;

import com.mojang.blaze3d.font.SheetGlyphInfo;

/**
 * A TTF glyph with one empty texel on every side. Under linear filtering the quad then fades out inside its own
 * transparent border instead of being cut at the inked edge, which left faint rectangle lines and uneven edges.
 */
public record PaddedGlyph(SheetGlyphInfo glyph) implements SheetGlyphInfo {
    public static boolean wanted(SheetGlyphInfo glyph) {return !(glyph instanceof PaddedGlyph)&&!glyph.isColored()&&glyph.getOversample()>1;}
    private float texel() {return 1/glyph.getOversample();}
    @Override public int getPixelWidth() {return glyph.getPixelWidth()+2;}
    @Override public int getPixelHeight() {return glyph.getPixelHeight()+2;}
    // The atlas is cleared when created, so the border texels are already empty.
    @Override public void upload(int x,int y) {glyph.upload(x+1,y+1);}
    @Override public boolean isColored() {return false;}
    @Override public float getOversample() {return glyph.getOversample();}
    @Override public float getBearingX() {return glyph.getBearingX()-texel();}
    @Override public float getBearingY() {return glyph.getBearingY()-texel();}
    @Override public float getLeft() {return glyph.getLeft()-texel();}
    @Override public float getRight() {return glyph.getRight()+texel();}
    @Override public float getUp() {return glyph.getUp()-texel();}
    @Override public float getDown() {return glyph.getDown()+texel();}
}
