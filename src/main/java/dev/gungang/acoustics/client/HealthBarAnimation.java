package dev.gungang.acoustics.client;

/** Retargetable, time-based easing; numeric health remains independent of this display value. */
public final class HealthBarAnimation {
    private boolean initialized;
    private float from, target, maximum;
    private double started, lastTime, duration;
    private float trailFrom;
    private double trailStarted;
    private boolean damageTrail;

    public float update(float health, float maxHealth, double seconds) {
        float next = HudHealth.fraction(health, maxHealth);
        if (!initialized || maximum != maxHealth || seconds < lastTime || seconds - lastTime > 1.0) {
            initialized = true;
            maximum = maxHealth;
            from = target = next;
            damageTrail = false;
            started = lastTime = seconds;
            return next;
        }
        float current = value(seconds);
        if (next != target) {
            if (next < target) {
                trailFrom = Math.max(current, trail(seconds));
                trailStarted = seconds;
                damageTrail = true;
            } else {
                damageTrail = false;
            }
            from = current;
            target = next;
            started = seconds;
            duration = next < current ? 0.12 : 0.35;
        }
        lastTime = seconds;
        return current;
    }

    public float trail(double seconds) {
        float displayed = value(seconds);
        if (!damageTrail) return displayed;
        double t = Math.max(0, Math.min(1, (seconds - trailStarted - 0.10) / 0.35));
        return Math.max(displayed, (float) (trailFrom + (target - trailFrom) * t * t * (3 - 2 * t)));
    }

    private float value(double seconds) {
        if (duration <= 0) return target;
        double t = Math.max(0, Math.min(1, (seconds - started) / duration));
        double eased = t * t * (3 - 2 * t);
        return (float) (from + (target - from) * eased);
    }
}
