package dev.gungang.acoustics;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class AcousticConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, DIFFRACTION, LOW_PASS, SHOOTER_HUD;
    public static final ForgeConfigSpec.DoubleValue REFERENCE_DISTANCE, ROLLOFF, WALL_STRENGTH, SMOOTHING;
    public static final ForgeConfigSpec.IntValue UPDATE_BUDGET, MAX_TRACE_DISTANCE, DIFFRACTION_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_SOUNDS, MATERIALS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SHOOTER_HUD = b.comment("Replace the vanilla hotbar and hearts with a bottom-left shooter HUD. Independent of acoustic processing.")
                .define("shooterHud", true);
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
