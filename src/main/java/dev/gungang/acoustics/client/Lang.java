package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * HUD / screen language (config hudLanguage, KO or EN). The Korean source text is the key: {@link #tr} returns it as
 * is in Korean, and in English looks it up in assets/gungang_acoustics/lang/hud_en.json, falling back to the Korean
 * when an entry is missing. Leading and trailing spaces of a fragment are kept, so pieces of a built-up line
 * ("출격까지 " + n + "초") translate one by one.
 */
public final class Lang {
    private static Map<String,String> english;

    public static boolean english() {
        try { return AcousticConfig.SPEC.isLoaded() && "EN".equals(AcousticConfig.HUD_LANGUAGE.get()); }
        catch (RuntimeException notLoaded) { return false; }
    }
    public static String tr(String korean) {
        if (korean == null || korean.isEmpty() || !english()) return korean;
        var table = table();
        String found = table.get(korean);
        if (found != null) return found;
        int start = 0, end = korean.length();
        while (start < end && Character.isWhitespace(korean.charAt(start))) start++;
        while (end > start && Character.isWhitespace(korean.charAt(end - 1))) end--;
        if (start == 0 && end == korean.length()) return korean;
        found = table.get(korean.substring(start, end));
        return found == null ? korean : korean.substring(0, start) + found + korean.substring(end);
    }
    /** {@link String#format} on the translated pattern. */
    public static String format(String korean, Object... args) { return String.format(java.util.Locale.ROOT, tr(korean), args); }
    /** Switches KO / EN, saves the client config and rebuilds the open screen in the new language. */
    public static void toggle() {
        AcousticConfig.HUD_LANGUAGE.set(english() ? "KO" : "EN");
        AcousticConfig.SPEC.save();
        var mc = Minecraft.getInstance();
        if (mc.screen != null) mc.screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
    /** The toggle's own label: the language it switches to. */
    public static String toggleLabel() { return english() ? "한국어" : "English"; }
    private static synchronized Map<String,String> table() {
        if (english != null) return english;
        var map = new HashMap<String,String>();
        try (var in = Lang.class.getResourceAsStream("/assets/gungang_acoustics/lang/hud_en.json")) {
            if (in != null) {
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : json.entrySet()) map.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception failure) { com.mojang.logging.LogUtils.getLogger().warn("[GunGang] HUD English table unavailable", failure); }
        return english = Map.copyOf(map);
    }
    private Lang() { }
}
