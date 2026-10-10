package dev.gungang.acoustics.physics;

import java.util.function.ToDoubleFunction;

/** Normalized interior samples: head, shoulders, torso and legs, including exposed side edges. */
public final class BodyVisibility {
    private static final double[][] SAMPLES = {
            {0.5, 0.96, 0.5}, {0.5, 0.75, 0.5}, {0.5, 0.48, 0.5}, {0.5, 0.12, 0.5},
            {0.05, 0.90, 0.05}, {0.95, 0.90, 0.05}, {0.05, 0.90, 0.95}, {0.95, 0.90, 0.95},
            {0.05, 0.65, 0.05}, {0.95, 0.65, 0.05}, {0.05, 0.65, 0.95}, {0.95, 0.65, 0.95},
            {0.05, 0.30, 0.05}, {0.95, 0.30, 0.05}, {0.05, 0.30, 0.95}, {0.95, 0.30, 0.95}
    };

    /** Any clear body sample completely releases wall occlusion, while distance attenuation remains separate. */
    public static double leastLoss(ToDoubleFunction<double[]> sampleLoss) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] point : SAMPLES) {
            double loss = sampleLoss.applyAsDouble(point);
            if (loss <= 0.001) return 0;
            if (Double.isFinite(loss)) best = Math.min(best, loss);
        }
        return best;
    }
    private BodyVisibility() { }
}
