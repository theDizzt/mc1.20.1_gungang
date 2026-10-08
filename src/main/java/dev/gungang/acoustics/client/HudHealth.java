package dev.gungang.acoustics.client;

import java.util.Locale;

/** Use Minecraft health points, not heart units; support modified maximum health. */
public final class HudHealth {
    public static float fraction(float health, float maximum) {
        if (!Float.isFinite(health) || !Float.isFinite(maximum) || maximum <= 0) return 0;
        return Math.max(0, Math.min(1, health / maximum));
    }
    public static String number(float value) {
        if (!Float.isFinite(value) || value <= 0) return "0";
        return value == Math.floor(value) ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value);
    }
    public static String label(float health, float maximum) { return number(health) + "/" + number(maximum); }
    private HudHealth() { }
}
