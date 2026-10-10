package dev.gungang.acoustics.client;

import dev.gungang.acoustics.AcousticConfig;
import dev.gungang.acoustics.physics.AcousticMath;
import dev.gungang.acoustics.physics.BodyVisibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;

/** Bounded geometric acoustics approximation. All level access must stay on the client thread. */
public final class WorldAcoustics {
    public record Trace(double lossDb, Vec3 firstObstacle) { }
    public record Transmission(double gain, double highFrequency) { }
    private record BodyPath(long tick, Vec3 listener, Vec3 camera, AABB bounds, Trace trace) { }
    private static final Map<Player, BodyPath> BODY_PATHS = new WeakHashMap<>();

    public static Transmission evaluate(Level level, Vec3 source, Vec3 listener, Vec3 viewPosition, Player emitter, boolean allowDiffraction) {
        double distance = source.distanceTo(listener);
        if (distance < 0.05 || distance > dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.MAX_TRACE_DISTANCE)) return new Transmission(1, 1);
        Trace direct = trace(level, source, listener);
        if (emitter != null) {
            Trace body = bodyTrace(level, emitter, listener, viewPosition);
            if (body.lossDb() <= 0.001) return new Transmission(1, 1);
            if (body.lossDb() < direct.lossDb()) direct = body;
        }
        double directGain = AcousticMath.amplitude(direct.lossDb());
        double diffracted = 0;
        if (allowDiffraction && direct.firstObstacle() != null && direct.lossDb() > 0.1 && dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.DIFFRACTION)) {
            Vec3 direction = listener.subtract(source).normalize();
            Vec3 side = direction.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
            side = side.normalize();
            Vec3 up = side.cross(direction).normalize();
            // A single bend is intentionally conservative: both legs must be completely unobstructed.
            for (int radius = 1; radius <= dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.DIFFRACTION_RADIUS); radius *= 2) {
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    Vec3 bend = direct.firstObstacle().add(side.scale(Math.cos(angle) * (radius + 0.25)))
                            .add(up.scale(Math.sin(angle) * (radius + 0.25)));
                    if (trace(level, source, bend).firstObstacle() != null) continue;
                    if (trace(level, bend, listener).firstObstacle() != null) continue;
                    double path = source.distanceTo(bend) + bend.distanceTo(listener);
                    double excess = Math.max(0, path - distance);
                    double loss = 12 + 10 * Math.log10(1 + 40 * excess);
                    diffracted = Math.max(diffracted, AcousticMath.amplitude(loss) * (distance + 2) / (path + 2));
                }
            }
        }
        // Incoherent energy sum, not a phase-accurate interference calculation.
        double energy = directGain * directGain + diffracted * diffracted;
        double gain = Math.min(1, Math.sqrt(energy));
        double directHf = 0.35 + 0.65 * AcousticMath.amplitude(direct.lossDb() * 0.35);
        double hf = energy < 1e-12 ? 0.02 :
                Math.sqrt((directGain * directGain * directHf * directHf + diffracted * diffracted * 0.5 * 0.5) / energy);
        return new Transmission(AcousticMath.withVolumeFloor(gain, dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.MIN_EFFECT_VOLUME)), Math.max(0.35, Math.min(1, hf)));
    }

    private static Trace bodyTrace(Level level, Player emitter, Vec3 listener, Vec3 camera) {
        AABB body = emitter.getBoundingBox();
        BodyPath cached = BODY_PATHS.get(emitter);
        if (cached != null && cached.tick() == level.getGameTime() && cached.listener().equals(listener)
                && cached.camera().equals(camera) && cached.bounds().equals(body)) return cached.trace();
        // Shared by that player's footsteps and gunfire in this tick; never read a level on the sound thread.
        double visible = BodyVisibility.leastLoss(point -> {
            Vec3 target = bodyPoint(body, point);
            return clear(level, listener, target, emitter)
                    || (listener.distanceToSqr(camera) > 0.01 && clear(level, camera, target, emitter)) ? 0 : 1;
        });
        Trace result;
        if (visible == 0) result = new Trace(0, null);
        else {
            final Trace[] best = {new Trace(120, body.getCenter())};
            BodyVisibility.leastLoss(point -> {
                Trace candidate = trace(level, bodyPoint(body, point), listener);
                if (candidate.lossDb() < best[0].lossDb()) best[0] = candidate;
                return candidate.lossDb();
            });
            result = best[0];
        }
        BODY_PATHS.put(emitter, new BodyPath(level.getGameTime(), listener, camera, body, result));
        return result;
    }

    private static Vec3 bodyPoint(AABB body, double[] point) {
        return new Vec3(body.minX + body.getXsize() * point[0], body.minY + body.getYsize() * point[1],
                body.minZ + body.getZsize() * point[2]);
    }

    private static boolean clear(Level level, Vec3 eye, Vec3 target, Player emitter) {
        return level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, emitter))
                .getType() == HitResult.Type.MISS;
    }

    public static Trace trace(Level level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1e-6) return new Trace(0, null);
        int x = floor(start.x), y = floor(start.y), z = floor(start.z);
        int sx = sign(delta.x), sy = sign(delta.y), sz = sign(delta.z);
        double tx = boundary(start.x, delta.x), ty = boundary(start.y, delta.y), tz = boundary(start.z, delta.z);
        double dx = step(delta.x), dy = step(delta.y), dz = step(delta.z);
        double loss = 0;
        Vec3 first = null;
        double[] a = {start.x, start.y, start.z}, b = {end.x, end.y, end.z};
        // Max configured ray length is 192 blocks; a diagonal can cross up to sqrt(3)*length cells.
        for (int cells = 0; cells < 768; cells++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) return new Trace(120, first == null ? Vec3.atCenterOf(pos) : first);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                var intervals = new ArrayList<double[]>();
                for (AABB box : state.getCollisionShape(level, pos).toAabbs()) {
                    double[] interval = AcousticMath.intersect(a, b,
                            new double[]{x + box.minX, y + box.minY, z + box.minZ},
                            new double[]{x + box.maxX, y + box.maxY, z + box.maxZ});
                    if (interval != null) intervals.add(interval);
                }
                intervals.sort(Comparator.comparingDouble(v -> v[0]));
                double covered = 0, last = -1;
                for (double[] interval : intervals) {
                    covered += Math.max(0, interval[1] - Math.max(last, interval[0]));
                    last = Math.max(last, interval[1]);
                }
                if (covered > 1e-8) {
                    if (first == null) first = start.add(delta.scale(intervals.get(0)[0]));
                    loss += covered * length * materialLoss(state) * dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.WALL_STRENGTH);
                    if (loss >= 120) return new Trace(120, first);
                }
            }
            double next = Math.min(tx, Math.min(ty, tz));
            if (next >= 1) return new Trace(loss, first);
            // Advance all tied axes: edge/corner touching has zero physical thickness.
            if (tx <= next + 1e-10) { x += sx; tx += dx; }
            if (ty <= next + 1e-10) { y += sy; ty += dy; }
            if (tz <= next + 1e-10) { z += sz; tz += dz; }
        }
        return new Trace(120, first == null ? start : first);
    }

    private static double materialLoss(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for (String entry : dev.gungang.acoustics.server.AcousticPolicy.get(AcousticConfig.MATERIALS)) {
            int equals = entry.indexOf('=');
            if (id.equals(entry.substring(0, equals))) return Double.parseDouble(entry.substring(equals + 1));
        }
        if (state.is(BlockTags.WOOL)) return 8;
        if (state.is(BlockTags.LEAVES)) return 2;
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS)) return 4;
        SoundType sound = state.getSoundType();
        if (sound == SoundType.GLASS) return 3;
        if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD) return 4;
        if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.COPPER) return 9;
        if (sound == SoundType.SAND || sound == SoundType.GRAVEL || sound == SoundType.SNOW) return 4;
        return 6;
    }

    private static int floor(double d) { return (int) Math.floor(d); }
    private static int sign(double d) { return d > 0 ? 1 : d < 0 ? -1 : 0; }
    private static double step(double d) { return d == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / d); }
    private static double boundary(double origin, double delta) {
        if (delta == 0) return Double.POSITIVE_INFINITY;
        return ((delta > 0 ? Math.floor(origin) + 1 : Math.floor(origin)) - origin) / delta;
    }

    private WorldAcoustics() { }
}
