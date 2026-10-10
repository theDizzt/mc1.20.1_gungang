package dev.gungang.acoustics.client;

import java.util.Set;

final class CompassMath {
    static double normalize(double degrees) { return (degrees % 360 + 360) % 360; }
    static int heading(double yaw) { return (int) Math.round(normalize(yaw + 180)) % 360; }
    static double bearing(double dx, double dz) { return normalize(Math.toDegrees(Math.atan2(dx, -dz))); }
    static double difference(double bearing, double heading) { return normalize(bearing - heading + 180) - 180; }
    static boolean gunshot(String path) {
        Set<String> tokens = new java.util.HashSet<>(java.util.Arrays.asList(path.toLowerCase(java.util.Locale.ROOT).split("[./_\\-]")));
        if (tokens.contains("reload") || tokens.contains("dry") || tokens.contains("dryfire") || tokens.contains("empty")) return false;
        return tokens.stream().anyMatch(Set.of("shoot", "shot", "fire", "gunshot", "gunfire", "suppressed", "silenced")::contains);
    }
    private CompassMath() { }
}
