package dev.gungang.acoustics.client;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;

/**
 * The 35 classic blower is the BF1 pack's WEX flamethrower (bf1:wex) blowing air: its gun display's flame muzzle flash,
 * lava bullet particles and red tracer are swapped for a wind swirl, cloud puffs and a pale tracer. Done here, in the
 * client, so the shared fuel ammo (also the 14 flamethrower's) keeps its fire and no client pack change is needed.
 * The TaCZ display objects are built by reflection; when TaCZ changes them the original look stays.
 */
public final class WindBlowerVisuals {
    public static final ResourceLocation DISPLAY = new ResourceLocation("bf1", "wex_display");
    private static final ResourceLocation FLASH = new ResourceLocation("gungang", "textures/misc/wind_flash.png");
    private static final float[] TRACER = {0.86f, 0.94f, 1f};
    private static Object flash, particle;
    private static boolean failed;

    public static boolean blower(ResourceLocation display) { return DISPLAY.equals(display); }
    public static float[] tracer() { return TRACER.clone(); }
    /** A TaCZ MuzzleFlash showing the wind swirl, or null (keep the original) when it cannot be built. */
    public static Object muzzleFlash() { build(); return flash; }
    /** A TaCZ AmmoParticle of drifting cloud puffs, or null. */
    public static Object particle() { build(); return particle; }

    private static synchronized void build() {
        if (failed || flash != null) return;
        try {
            var flashType = Class.forName("com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash");
            var f = flashType.getConstructor().newInstance();
            set(flashType, f, "texture", FLASH); set(flashType, f, "scale", 2.4f);
            var particleType = Class.forName("com.tacz.guns.client.resource.pojo.display.ammo.AmmoParticle");
            var p = particleType.getConstructor().newInstance();
            set(particleType, p, "name", "minecraft:cloud");
            set(particleType, p, "delta", new org.joml.Vector3f(.08f, .08f, .08f));
            set(particleType, p, "speed", .02f); set(particleType, p, "lifeTime", 7); set(particleType, p, "count", 1);
            particleType.getMethod("setParticleOptions", net.minecraft.core.particles.ParticleOptions.class).invoke(p, ParticleTypes.CLOUD);
            flash = f; particle = p;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError changed) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("GunGang: classic blower wind visuals unavailable ({}); the flamethrower look stays.", changed.toString());
        }
    }
    private static void set(Class<?> type, Object target, String field, Object value) throws ReflectiveOperationException {
        var f = type.getDeclaredField(field); f.setAccessible(true); f.set(target, value);
    }
    private WindBlowerVisuals() { }
}
