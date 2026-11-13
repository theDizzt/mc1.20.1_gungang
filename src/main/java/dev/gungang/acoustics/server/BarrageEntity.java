package dev.gungang.acoustics.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 73 경계 감시 포격 ({@link ExtraGadgetConfig.Barrage}): a red smoke flare drops on the called spot and red smoke marks
 * the area; after the delay, every volley interval {@code volleySize} missiles are aimed at the most crowded spots
 * inside the area ({@link BarrageTargets}). A missile falls straight down through any roof onto its aim point and
 * blasts everyone within its radius, the caller and allies included, unless a block stands between the blast and
 * them (unlike the orbital strike, cover protects). A called barrage outlives its caller's death and weapon changes.
 */
public final class BarrageEntity extends Entity {
    private static final DustParticleOptions RED_SMOKE = new DustParticleOptions(new org.joml.Vector3f(.85f, .08f, .06f), 3.5f);
    private static final DustParticleOptions RED_RING = new DustParticleOptions(new org.joml.Vector3f(1f, .15f, .1f), 1.6f);
    /** Height a missile starts from above its aim point, in blocks. */
    private static final double DROP = 40;
    private record Missile(Vec3 target, long launched, long impact) { }
    private final List<Missile> missiles = new ArrayList<>();
    private UUID owner;
    /** Game ticks: the barrage was called, the first volley, and the volley count fired so far. */
    private long calledAt = -1, startAt;
    private int fired;
    private ExtraGadgetConfig.Barrage config = ExtraGadgetConfig.barrage;

    public BarrageEntity(EntityType<? extends BarrageEntity> type, Level level) { super(type, level); setNoGravity(true); noPhysics = true; }
    @Override protected void defineSynchedData() { }

    public void setup(Entity owner) {
        this.owner = owner == null ? null : owner.getUUID();
        config = ExtraGadgetConfig.barrage;
        calledAt = level().getGameTime();
        startAt = calledAt + ExtraGadgetConfig.ticks(config.delaySeconds());
    }
    public double radius() { return config.radius(); }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        // A barrage reloaded from disk is stale: never deliver it later.
        if (calledAt < 0) { discard(); return; }
        long now = level.getGameTime(), age = now - calledAt;
        int volleys = config.volleys(), interval = Math.max(1, ExtraGadgetConfig.ticks(config.volleyIntervalSeconds()));
        int flight = Math.max(1, ExtraGadgetConfig.ticks(config.flightSeconds()));
        long end = startAt + (long) (volleys - 1) * interval + flight;
        smoke(level, age, now <= end);
        if (fired < volleys && now >= startAt + (long) fired * interval) { volley(level, now, flight); fired++; }
        for (var it = missiles.iterator(); it.hasNext(); ) {
            var missile = it.next();
            if (now >= missile.impact()) { it.remove(); explode(level, missile.target()); }
            else trail(level, missile, now);
        }
        if (fired >= volleys && missiles.isEmpty() && now > end + 40) discard();
    }

    /** The flare falling for the first second, then red smoke at the centre and a red ring along the area's edge. */
    private void smoke(ServerLevel level, long age, boolean running) {
        double x = getX(), y = getY(), z = getZ();
        if (age < 20) {
            double h = y + 24 * (1 - age / 20.0);
            level.sendParticles(RED_SMOKE, x, h, z, 4, .15, .3, .15, 0);
            level.sendParticles(ParticleTypes.FLAME, x, h, z, 1, 0, 0, 0, 0);
            if (age == 0) level.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 4, .6f);
            if (age == 19) level.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 3, .7f);
            return;
        }
        if (!running) return;
        if (age % 2 == 0) {
            level.sendParticles(RED_SMOKE, x, y + .6, z, 10, 1.2, .6, 1.2, .02);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + .4, z, 2, .5, .2, .5, .02);
        }
        if (age % 10 == 0) for (int i = 0; i < 120; i++) {
            double angle = i * Math.PI * 2 / 120;
            level.sendParticles(RED_RING, x + Math.cos(angle) * config.radius(), y + .2, z + Math.sin(angle) * config.radius(), 1, 0, .1, 0, 0);
        }
    }
    /** Aims this volley's missiles at the most crowded spots inside the area. */
    private void volley(ServerLevel level, long now, int flight) {
        double r = config.radius();
        var people = new ArrayList<Vec3>();
        for (var e : level.getEntitiesOfClass(LivingEntity.class, new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r), BotEntity::participant))
            if (e.isAlive() && !e.isSpectator() && Math.pow(e.getX() - getX(), 2) + Math.pow(e.getZ() - getZ(), 2) <= r * r) people.add(e.position());
        for (var target : BarrageTargets.pick(people, config.volleySize(), config.blastRadius(), position(), r, new java.util.Random(random.nextLong()))) {
            // Empty ground: drop onto the surface below the random point, not mid-air at the centre's height.
            var aim = people.isEmpty() ? ground(level, target) : target;
            missiles.add(new Missile(aim, now, now + flight));
            level.playSound(null, aim.x, aim.y + 8, aim.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3, .5f);
        }
        level.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 4, .6f);
    }
    private Vec3 ground(ServerLevel level, Vec3 at) {
        var top = new Vec3(at.x, getY() + 6, at.z);
        var hit = level.clip(new ClipContext(top, top.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }
    /** The falling missile: fire and smoke at its current height, straight through anything above the target. */
    private void trail(ServerLevel level, Missile missile, long now) {
        double t = (double) (now - missile.launched()) / Math.max(1, missile.impact() - missile.launched());
        var at = missile.target().add(0, DROP * (1 - t), 0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 4, .08, .5, .08, .01);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 2, .1, .5, .1, .01);
        level.sendParticles(RED_SMOKE, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }
    /** Hurts everyone in the blast with a clear line to it (owner and allies too); cover blocks the damage. */
    private void explode(ServerLevel level, Vec3 target) {
        var center = target.add(0, .3, 0);
        double radius = config.blastRadius();
        var ownerEntity = owner == null ? null : level.getEntity(owner);
        var source = level.damageSources().explosion(this, ownerEntity);
        for (var victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1), BotEntity::participant)) {
            if (!victim.isAlive() || victim.isSpectator()) continue;
            var body = victim.getBoundingBox();
            var closest = new Vec3(Math.max(body.minX, Math.min(center.x, body.maxX)), Math.max(body.minY, Math.min(center.y, body.maxY)), Math.max(body.minZ, Math.min(center.z, body.maxZ)));
            if (closest.distanceToSqr(center) > radius * radius) continue;
            if (!exposed(level, center, victim) || SpawnGateProtection.shields(victim, center)) continue;
            OrdnanceDamage.hurt(victim, source, (float) config.damage());
        }
        GimmickEntity.blast(level, center, radius, (float) config.damage());
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + .5, center.z, 8, radius * .4, .6, radius * .4, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + .5, center.z, 20, radius * .4, .5, radius * .4, .03);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4, .9f + random.nextFloat() * .2f);
    }
    /** A block between the blast and every part of the body (feet, middle, eyes) counts as cover. */
    static boolean exposed(Level level, Vec3 center, LivingEntity victim) {
        for (double h : new double[]{.1, victim.getBbHeight() / 2, victim.getEyeHeight()}) {
            var point = victim.position().add(0, h, 0);
            if (level.clip(new ClipContext(center, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { calledAt = -1; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() { return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this); }
}
