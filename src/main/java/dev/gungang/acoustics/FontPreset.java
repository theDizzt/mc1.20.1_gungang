package dev.gungang.acoustics;

/**
 * Bundled TTF presets. GUNGANG (GUNGANG Latin + Mulmaru Hangul) at size 9 maps one
 * GUNGANG pixel to one GUI pixel; its Hangul ink height equals the Latin 'A' (7 px).
 */
public enum FontPreset {
    GUNGANG("gungang_hangul.ttf", 9),
    GALMURI("galmuri11-bold.ttf", 9),
    EXTERNAL("gungang_hangul.ttf", 9);

    public final String file;
    public final double size;
    FontPreset(String file, double size) { this.file=file; this.size=size; }
}
