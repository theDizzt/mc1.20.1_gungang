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
        // Round up so a living player with less than one HP never appears dead.
        return String.format(Locale.ROOT, "%.0f", Math.ceil(value));
    }
    public static String label(float health, float maximum) { return number(health); }
    public static String label(float health,float maximum,float absorption) {
        float total=Math.max(0,health)+Math.max(0,absorption);
        // Life support keeps 0.001 HP internally; avoid rounding that sentinel up to an extra HP.
        return number(total>0?Math.max(health>0?1:0,total-.0011f):0);
    }
    private HudHealth() { }
}
