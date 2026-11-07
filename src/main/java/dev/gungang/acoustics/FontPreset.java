package dev.gungang.acoustics;

/** Bundled TTF sizes normalized to Galmuri's Hangul ink height (1100 / 1400). */
public enum FontPreset {
    MARU_MINYA("x12y12pxmaruminyahangul.ttf", 9.0 * 6 / 7),
    GALMURI_LEGACY("galmuri11-bold.ttf", 9),
    EXTERNAL("x12y12pxmaruminyahangul.ttf", 9.0 * 6 / 7);

    public final String file;
    public final double size;
    FontPreset(String file, double size) { this.file=file; this.size=size; }
}
