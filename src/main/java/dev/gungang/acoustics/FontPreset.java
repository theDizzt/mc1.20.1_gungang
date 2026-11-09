package dev.gungang.acoustics;

/**
 * Bundled TTF presets; Galmuri11 Bold is the default. GUNGANG (GUNGANG Latin + Mulmaru Hangul) at size 9 maps one
 * GUNGANG pixel to one GUI pixel; its Hangul ink height equals the Latin 'A' (7 px). Weapon ids and specs use plain
 * GUNGANG with a Galmuri fallback through {@code HudText.UI_FONT}, whatever preset is selected.
 */
public enum FontPreset {
    GALMURI("galmuri11-bold.ttf", 9),
    GUNGANG("gungang_hangul.ttf", 9),
    EXTERNAL("galmuri11-bold.ttf", 9);

    public final String file;
    public final double size;
    FontPreset(String file, double size) { this.file=file; this.size=size; }
}
