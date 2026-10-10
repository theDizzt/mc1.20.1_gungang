package dev.gungang.acoustics.client;

import java.util.Set;

/** Match event-name tokens rather than substrings such as footsteps in a music title. */
public final class SoundVolume {
    private static final Set<String> WEAPONS = Set.of("gun", "guns", "gunfire", "gunshot", "shoot", "shot",
            "reload", "bow", "crossbow", "trident", "attack");

    public static double scale(String path, boolean gunSound, int footsteps, int weapons) {
        if (gunSound) return weapons / 100.0;
        if (isFootstep(path)) return footsteps / 100.0;
        String[] tokens = path.split("[./_\\-]");
        for (String token : tokens) if (WEAPONS.contains(token)) return weapons / 100.0;
        return 1;
    }

    public static boolean isFootstep(String path) {
        for (String token : path.split("[./_\\-]"))
            if (token.equals("step") || token.equals("footstep") || token.equals("footsteps")) return true;
        return false;
    }

    private SoundVolume() { }
}
