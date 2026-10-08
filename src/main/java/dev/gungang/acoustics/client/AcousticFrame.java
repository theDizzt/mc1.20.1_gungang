package dev.gungang.acoustics.client;

/** Immutable transfer from world/client thread to OpenAL's sound executor. */
public record AcousticFrame(boolean enabled, boolean manageDistance, double distance, double transmission, double highFrequency,
                            double reference, double rolloff, double smoothing) {
    public static final AcousticFrame BYPASS = new AcousticFrame(false, false, 0, 1, 1, 2, 1, 0.1);
}
