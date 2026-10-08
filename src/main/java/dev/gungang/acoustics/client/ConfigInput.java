package dev.gungang.acoustics.client;

import java.util.Arrays;
import java.util.List;

/** Strict parsing shared by the screen and tests; invalid input is never silently clamped. */
public final class ConfigInput {
    public static double decimal(String text, double min, double max) {
        double value = Double.parseDouble(text.trim());
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException();
        return value;
    }
    public static int integer(String text, int min, int max) {
        int value = Integer.parseInt(text.trim());
        if (value < min || value > max) throw new IllegalArgumentException();
        return value;
    }
    public static List<String> entries(String text, boolean materials) {
        if (text.isBlank()) return List.of();
        List<String> entries = Arrays.stream(text.split(",", -1)).map(String::trim).toList();
        for (String entry : entries) {
            if (materials) {
                if (!entry.matches("[a-z0-9_.-]+:[a-z0-9_./-]+=[0-9]+(\\.[0-9]+)?")) throw new IllegalArgumentException();
                decimal(entry.substring(entry.indexOf('=') + 1), 0, 120);
            } else if (!entry.matches("[a-z0-9_.-]+:([a-z0-9_./-]+|\\*)")) throw new IllegalArgumentException();
        }
        return entries;
    }
    private ConfigInput() { }
}
