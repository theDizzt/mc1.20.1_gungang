package dev.gungang.acoustics.client;

/**
 * Eased display value for a HUD bar (0..1) plus a fading flash over the part that just changed:
 * on a drop the lost piece lingers bright and fades, on a gain the new piece glows as it fills in.
 * Wall-clock based, so it keeps moving while the game is paused or entities are not ticked.
 */
final class BarTween {
    private static final double FLASH = 0.45;
    private final double up, down;
    private boolean initialized, rising;
    private float from, target, flashFrom;
    private double started, flashAt = -10;

    /** Seconds a rise and a drop take to settle. */
    BarTween(double up, double down) { this.up = up; this.down = down; }

    private static double now() { return System.nanoTime() / 1e9; }

    /** Feeds the real value and returns the one to draw. */
    float update(float next) {
        double now = now();
        if (!initialized) { initialized = true; from = target = next; started = now; return next; }
        if (next != target) {
            float current = value(now);
            rising = next > target;
            // A drop keeps the old edge of the flash; a run of quick drops (automatic fire) extends one flash.
            flashFrom = rising ? current : now - flashAt < FLASH * .5 ? Math.max(flashFrom, current) : current;
            flashAt = now;
            from = current;
            target = next;
            started = now;
        }
        return value(now);
    }

    /** Snap without animating, e.g. on a weapon switch. */
    void reset() { initialized = false; flashAt = -10; }

    float value() { return value(now()); }

    private float value(double now) {
        double duration = target > from ? up : down;
        if (duration <= 0) return target;
        double t = Math.max(0, Math.min(1, (now - started) / duration));
        double eased = 1 - Math.pow(1 - t, 3);
        return (float) (from + (target - from) * eased);
    }

    /** Flash strength, 1 right after a change fading to 0. */
    float flash() {
        double t = (now() - flashAt) / FLASH;
        return t < 0 || t >= 1 ? 0 : (float) ((1 - t) * (1 - t));
    }

    boolean rising() { return rising; }

    /** The changed span: [low, high] in 0..1. */
    float flashLow() { return rising ? flashFrom : value(); }
    float flashHigh() { return rising ? value() : flashFrom; }

    /** Seconds since the last change (large when none happened yet). */
    double since() { return now() - flashAt; }
}
