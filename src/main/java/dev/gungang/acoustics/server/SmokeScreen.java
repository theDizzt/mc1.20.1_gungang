package dev.gungang.acoustics.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side model of LR Tactical smoke clouds, which exist only as client particles. The grenade releases its cloud
 * 40 ticks after the throw and keeps spawning puffs within about 5.5 blocks across and 4.5 up/down of itself until
 * it expires; the puffs then linger about a second. Bots treat a sightline through that volume as blocked.
 */
final class SmokeScreen {
    private static final ResourceLocation GRENADE = new ResourceLocation("lrtactical", "smoke_grenade_entity");
    private static final int RELEASE = 40, LINGER = 20;
    /** Dense core of the cloud; the triangular puff spread thins out toward 5.5 / 4.5 blocks. */
    private static final double RADIUS = 4.5, HALF_HEIGHT = 3.5;
    /** At arm's length a figure is still visible through the haze. */
    private static final double SEE_THROUGH = 2.5;

    private record Cloud(Vec3 center, int seen) { }
    private static final Map<ServerLevel, Map<UUID, Cloud>> CLOUDS = new java.util.WeakHashMap<>();
    private static final Map<ServerLevel, Integer> SCANNED = new java.util.WeakHashMap<>();

    private static Map<UUID, Cloud> clouds(ServerLevel level) {
        int now = level.getServer().getTickCount();
        var clouds = CLOUDS.computeIfAbsent(level, l -> new HashMap<>());
        Integer last = SCANNED.put(level, now);
        if (last == null || last != now) {
            for (Entity entity : level.getAllEntities())
                if (entity.tickCount >= RELEASE && !entity.isRemoved() && GRENADE.equals(EntityType.getKey(entity.getType())))
                    clouds.put(entity.getUUID(), new Cloud(entity.position(), now));
            clouds.values().removeIf(c -> now - c.seen() > LINGER);
        }
        return clouds;
    }

    /** Whether smoke hides {@code to} from an eye at {@code from}. */
    static boolean blocks(ServerLevel level, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) <= SEE_THROUGH * SEE_THROUGH) return false;
        for (var cloud : clouds(level).values())
            if (crosses(from, to, cloud.center())) return true;
        return false;
    }

    static boolean blocks(Entity viewer, Entity target) {
        return viewer.level() instanceof ServerLevel level && (blocks(level, viewer.getEyePosition(), target.getEyePosition())
                && blocks(level, viewer.getEyePosition(), target.position().add(0, target.getBbHeight() * .5, 0)));
    }

    /** Segment vs. ellipsoid: scale y so the cloud is a sphere, then find the closest point on the segment. */
    private static boolean crosses(Vec3 from, Vec3 to, Vec3 center) {
        double k = RADIUS / HALF_HEIGHT;
        double ax = from.x - center.x, ay = (from.y - center.y) * k, az = from.z - center.z;
        double dx = to.x - from.x, dy = (to.y - from.y) * k, dz = to.z - from.z;
        double length = dx * dx + dy * dy + dz * dz;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy + az * dz) / length));
        double px = ax + dx * t, py = ay + dy * t, pz = az + dz * t;
        return px * px + py * py + pz * pz <= RADIUS * RADIUS;
    }

    private SmokeScreen() { }
}
