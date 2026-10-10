package dev.gungang.acoustics;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class AcousticConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_FONT;
    public static final ForgeConfigSpec.ConfigValue<String> FONT_FILE;
    public static final ForgeConfigSpec.IntValue FONT_OVERSAMPLE;
    public static final ForgeConfigSpec.IntValue FOOTSTEP_VOLUME, WEAPON_VOLUME;
    public static final ForgeConfigSpec.BooleanValue FOOTSTEP_OCCLUSION, WATER_EFFECTS;
    public static final ForgeConfigSpec.DoubleValue FONT_OFFSET_X, FONT_OFFSET_Y;
    public static final ForgeConfigSpec.BooleanValue FONT_SMOOTHING;
    public static final ForgeConfigSpec.BooleanValue HUD_BOLD_TEXT;
    public static final ForgeConfigSpec.IntValue HUD_TEXT_SIZE;
    public static final ForgeConfigSpec.BooleanValue TEAM_HUD, KILL_LOG, TEAM_SILHOUETTES, COMPASS;
    public static final ForgeConfigSpec.BooleanValue ENABLED, DIFFRACTION, LOW_PASS, SHOOTER_HUD;
    public static final ForgeConfigSpec.DoubleValue REFERENCE_DISTANCE, ROLLOFF, WALL_STRENGTH, SMOOTHING;
    public static final ForgeConfigSpec.IntValue UPDATE_BUDGET, MAX_TRACE_DISTANCE, DIFFRACTION_RADIUS, HUD_OFFSET_X, HUD_OFFSET_Y, MIN_EFFECT_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_SOUNDS, MATERIALS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        WATER_EFFECTS = b.comment("Water-path attenuation, air-water interface loss and underwater low-pass filtering.").define("waterEffects", true);
        FOOTSTEP_OCCLUSION = b.comment("Apply wall occlusion and muffling to footsteps. Disable to keep footsteps clear; distance and volume settings still apply.")
                .define("footstepOcclusion", true);
        FOOTSTEP_VOLUME = b.comment("Footstep volume percent, 0=mute, 100=original, 300=3x. Independent of acoustic processing.")
                .defineInRange("footstepVolume", 100, 0, 300);
        WEAPON_VOLUME = b.comment("Weapon volume percent, including TaCZ gun sounds and recognized weapon event IDs. 0..300, 100=original.")
                .defineInRange("weaponVolume", 100, 0, 300);
        HUD_TEXT_SIZE = b.comment("HUD name/health text size in percent. Independent of GUI scale.").defineInRange("hudTextSize", 150, 100, 150);
        HUD_BOLD_TEXT = b.define("hudBoldText", true);
        CUSTOM_FONT = b.comment("Use a TTF from config/gungang/fonts, or bundled Galmuri11 Bold. Disable to restore the normal game/resource-pack font.")
                .define("customFont", true);
        FONT_FILE = b.comment("TTF filename in config/gungang/fonts. Empty selects the first .ttf in filename order; no files uses bundled Galmuri11 Bold.")
                .define("fontFile", "");
        FONT_OVERSAMPLE = b.comment("Selected and bundled TTF raster resolution multiplier, 1..8. Higher values use more atlas memory. Reload resources after changing.")
                .defineInRange("fontOversample", 6, 1, 8);
        FONT_SMOOTHING = b.comment("Linear filtering for oversampled monochrome font atlases, including resource-pack TTF fonts.")
                .define("fontSmoothing", true);
        FONT_OFFSET_X = b.comment("TTF horizontal shift in font units; positive moves right. Reload resources to apply.")
                .defineInRange("fontOffsetX", 0.0, -8.0, 8.0);
        FONT_OFFSET_Y = b.comment("TTF vertical shift in font units; positive moves down. Reload resources to apply.")
                .defineInRange("fontOffsetY", 1.0, -8.0, 8.0);
        COMPASS = b.comment("Show heading compass and enemy gunshot bearings above the player health bar.").define("compass", true);
        TEAM_HUD = b.define("teamHud", true);
        KILL_LOG = b.define("killLog", true);
        TEAM_SILHOUETTES = b.define("teamSilhouettes", true);
        SHOOTER_HUD = b.comment("Replace the vanilla hotbar and hearts with a bottom-left shooter HUD. Independent of acoustic processing.")
                .define("shooterHud", true);
        HUD_OFFSET_X = b.comment("HUD distance from the left edge, in fixed units (2 framebuffer pixels).")
                .defineInRange("hudOffsetX", 8, 0, 4096);
        HUD_OFFSET_Y = b.comment("HUD distance from the bottom edge, in fixed units (2 framebuffer pixels).")
                .defineInRange("hudOffsetY", 8, 0, 4096);
        MIN_EFFECT_VOLUME = b.comment("Minimum wall-transmission volume in percent. Distance, source volume and mute settings still apply.")
                .defineInRange("minimumEffectVolume", 16, 0, 100);
        ENABLED = b.comment("Enable acoustic processing. Changes take effect on active sounds too.").define("enabled", true);
        LOW_PASS = b.comment("Muffle high frequencies behind obstacles using OpenAL EFX.").define("lowPass", true);
        REFERENCE_DISTANCE = b.comment("Near-field distance in blocks; pressure falls approximately as 1/r farther away.")
                .defineInRange("referenceDistance", 2.0, 0.25, 16.0);
        ROLLOFF = b.defineInRange("rolloff", 1.0, 0.1, 4.0);
        WALL_STRENGTH = b.comment("Multiplier for material transmission loss. Coefficients are perceptual approximations.")
                .defineInRange("wallStrength", 1.0, 0.0, 4.0);
        SMOOTHING = b.comment("Response time in seconds for changing occlusion; initial sounds apply immediately.")
                .defineInRange("smoothingSeconds", 0.10, 0.01, 1.0);
        UPDATE_BUDGET = b.comment("Maximum active sounds retraced per game tick, using round-robin scheduling.")
                .defineInRange("updatesPerTick", 12, 1, 128);
        MAX_TRACE_DISTANCE = b.comment("Beyond this distance, only distance attenuation is applied.")
                .defineInRange("maxTraceDistance", 96, 16, 192);
        DIFFRACTION = b.comment("Search for a clear two-segment route around an obstacle; this is not a wave solver.")
                .define("diffraction", true);
        DIFFRACTION_RADIUS = b.defineInRange("diffractionRadius", 4, 1, 8);
        EXCLUDED_SOUNDS = b.comment("Exact sound IDs or namespace:* entries to leave untouched.")
                .defineListAllowEmpty("excludedSounds", List.of(), o -> o instanceof String s && s.matches("[a-z0-9_.-]+:([a-z0-9_./-]+|\\*)"));
        MATERIALS = b.comment("Block transmission overrides: namespace:block=decibels_per_block, 0..120.")
                .defineListAllowEmpty("materialOverrides", List.of("minecraft:glass=7", "minecraft:glass_pane=7"), AcousticConfig::validMaterial);
        SPEC = b.build();
    }

    private static boolean validMaterial(Object value) {
        if (!(value instanceof String s) || !s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+=[0-9]+(\\.[0-9]+)?")) return false;
        double db = Double.parseDouble(s.substring(s.indexOf('=') + 1));
        return Double.isFinite(db) && db <= 120;
    }

    private AcousticConfig() { }
}
