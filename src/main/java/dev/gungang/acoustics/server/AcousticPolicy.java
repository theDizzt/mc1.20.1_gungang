package dev.gungang.acoustics.server;

import com.google.gson.*;
import dev.gungang.acoustics.AcousticConfig;
import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server overrides never modify or persist the client's personal config. */
public final class AcousticPolicy {
    public static final List<ForgeConfigSpec.ConfigValue<?>> VALUES = List.of(
            AcousticConfig.ENABLED, AcousticConfig.LOW_PASS, AcousticConfig.DIFFRACTION,
            AcousticConfig.WATER_EFFECTS, AcousticConfig.FOOTSTEP_OCCLUSION,
            AcousticConfig.FOOTSTEP_VOLUME, AcousticConfig.WEAPON_VOLUME,
            AcousticConfig.REFERENCE_DISTANCE, AcousticConfig.ROLLOFF, AcousticConfig.WALL_STRENGTH,
            AcousticConfig.SMOOTHING, AcousticConfig.UPDATE_BUDGET, AcousticConfig.MAX_TRACE_DISTANCE,
            AcousticConfig.DIFFRACTION_RADIUS, AcousticConfig.MIN_EFFECT_VOLUME,
            AcousticConfig.EXCLUDED_SOUNDS, AcousticConfig.MATERIALS);
    private static volatile Map<String, Object> override = Map.of();
    public static String key(ForgeConfigSpec.ConfigValue<?> value) { return String.join(".", value.getPath()); }
    public static boolean locked(ForgeConfigSpec.ConfigValue<?> value) { return override.containsKey(key(value)); }
    @SuppressWarnings("unchecked")
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value) {
        Object effective = override.get(key(value));
        return effective == null ? value.get() : (T) effective;
    }
    public static void receive(JsonObject json) { override = parse(json); }
    public static void clear() { override = Map.of(); }
    public static JsonObject defaults() {
        JsonObject json = new JsonObject();
        Gson gson = new Gson();
        for (var value : VALUES) json.add(key(value), gson.toJsonTree(value.getDefault()));
        return json;
    }
    public static Map<String, Object> parse(JsonObject json) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : json.keySet())
            if (VALUES.stream().noneMatch(value -> key(value).equals(key))) throw new IllegalArgumentException("Unknown acoustic option: " + key);
        for (var config : VALUES) {
            String key = key(config);
            JsonElement element = json.get(key);
            Object value = config.getDefault();
            if (element != null) {
                if (value instanceof Boolean) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key);
                    value = element.getAsBoolean();
                } else if (value instanceof Number) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key);
                    double number = element.getAsDouble();
                    if (!Double.isFinite(number)) throw new IllegalArgumentException(key);
                    if (value instanceof Integer) {
                        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalArgumentException(key);
                        value = (int) number;
                    } else value = number;
                } else {
                    if (!element.isJsonArray() || element.getAsJsonArray().size() > 128) throw new IllegalArgumentException(key);
                    List<String> entries = new ArrayList<>();
                    for (JsonElement entry : element.getAsJsonArray()) {
                        if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString() || entry.getAsString().length() > 256) throw new IllegalArgumentException(key);
                        entries.add(entry.getAsString());
                    }
                    value = List.copyOf(entries);
                }
            }
            ForgeConfigSpec.ValueSpec spec = AcousticConfig.SPEC.getSpec().get(config.getPath());
            if (!spec.test(value)) throw new IllegalArgumentException("Invalid acoustic option: " + key);
            result.put(key, value);
        }
        return Map.copyOf(result);
    }
    private AcousticPolicy() { }
}
