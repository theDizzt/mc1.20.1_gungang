package dev.gungang.acoustics.client;

import java.util.TreeSet;

/**
 * Geometry of the weapon slot's clock-wipe cooldown ({@code ShooterHud.clockShade}). Angles are radians clockwise
 * from 12 o'clock (screen y points down); the shade still waiting runs from {@code (1 - fraction)} of a turn to the
 * full turn, so it clears clockwise.
 */
final class ClockWipe {
    /** Finest step between fan points; the box corners are always added so the edges stay straight. */
    static final double STEP = Math.PI / 60;

    /** Fan angles from where the shade starts to 12 o'clock, including every box corner in between. */
    static double[] angles(float fraction, float width, float height) {
        double start = (1 - Math.max(0, Math.min(1, fraction))) * Math.PI * 2, end = Math.PI * 2;
        var points = new TreeSet<Double>();
        points.add(start); points.add(end);
        for (double a = start; a < end; a += STEP) points.add(a);
        double corner = Math.atan2(width / 2, height / 2);
        for (double c : new double[]{corner, Math.PI - corner, Math.PI + corner, Math.PI * 2 - corner})
            if (c > start && c < end) points.add(c);
        return points.stream().mapToDouble(Double::doubleValue).toArray();
    }
    /** Where the ray from the box centre at {@code angle} meets the box edge, as an offset from the centre. */
    static float[] edge(double angle, float width, float height) {
        double dx = Math.sin(angle), dy = -Math.cos(angle);
        double tx = Math.abs(dx) < 1e-9 ? Double.POSITIVE_INFINITY : width / 2 / Math.abs(dx);
        double ty = Math.abs(dy) < 1e-9 ? Double.POSITIVE_INFINITY : height / 2 / Math.abs(dy);
        double t = Math.min(tx, ty);
        return new float[]{(float) (dx * t), (float) (dy * t)};
    }
    private ClockWipe() { }
}
