package dev.gungang.acoustics.physics;

/** Pure math, independent of Minecraft and OpenAL, so numerical properties can be tested. */
public final class AcousticMath {
    public static double amplitude(double decibels) { return Math.pow(10, -Math.max(0, decibels) / 20); }

    /** Smooth approach to the user floor; additional walls remain distinguishable rather than hard-clipping. */
    public static double wallGain(double lossDb, double minimumPercent) {
        return withVolumeFloor(amplitude(lossDb), minimumPercent);
    }

    public static double withVolumeFloor(double gain, double minimumPercent) {
        double floor = Math.max(0, Math.min(1, minimumPercent / 100));
        return floor + (1 - floor) * Math.max(0, Math.min(1, gain));
    }

    public static double distanceGain(double distance, double range, double reference, double rolloff) {
        if (range <= 0 || distance >= range) return 0;
        double pressure = reference / (reference + rolloff * Math.max(0, distance - reference));
        // Smoothly reach silence at Minecraft's original audible boundary.
        double t = Math.max(0, Math.min(1, (range - distance) / (range * 0.25)));
        return pressure * t * t * (3 - 2 * t);
    }

    public static double smooth(double previous, double target, double seconds, double timeConstant) {
        return previous + (target - previous) * -Math.expm1(-Math.max(0, seconds) / timeConstant);
    }

    /** Segment intersection interval [0,1] with an axis-aligned box; null for no positive-length intersection. */
    public static double[] intersect(double[] start, double[] end, double[] min, double[] max) {
        double enter = 0, exit = 1;
        for (int axis = 0; axis < 3; axis++) {
            double delta = end[axis] - start[axis];
            if (Math.abs(delta) < 1e-12) {
                if (start[axis] <= min[axis] || start[axis] >= max[axis]) return null;
            } else {
                double a = (min[axis] - start[axis]) / delta;
                double b = (max[axis] - start[axis]) / delta;
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (exit <= enter) return null;
            }
        }
        return new double[]{enter, exit};
    }

    private AcousticMath() { }
}
