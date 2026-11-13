package dev.gungang.acoustics.server;

/** Per-source snapshot of TaCZ's distance/visibility result, before target-specific modifiers. */
public interface TaczExplosionDamage {
    void gungang$explosionHit(float amount);
    boolean gungang$isTaczExplosion();
    float gungang$explosionAmount(float fallback);
}
