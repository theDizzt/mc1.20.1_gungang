package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.ArrowFragmentEntity;
import dev.gungang.acoustics.server.ArrowGrenadeEntity;
import dev.gungang.acoustics.server.GadgetItems;
import dev.gungang.acoustics.server.GungangNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws arrow grenade bursts from one packet each: every shard flies out from the grenade at the server's speed and
 * stops where the server traced its block, or at the first body it meets here, drawn with the vanilla arrow model
 * (one detached fragment entity, never added to the world, posed per shard).
 */
@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class ArrowBursts {
    private static final double HIT_MARGIN = .45;
    private static final class Burst {
        final Vec3 origin; final float speed; final float[] stops; final long start; final boolean[] stopped;
        final double[] stoppedAt;
        Burst(GungangNetwork.ArrowBurst p, long start) {
            origin = new Vec3(p.x(), p.y(), p.z()); speed = Math.max(.01f, p.speed()); stops = p.stops(); this.start = start;
            stopped = new boolean[stops.length]; stoppedAt = new double[stops.length];
        }
        double longest() { float max = 0; for (float f : stops) max = Math.max(max, f); return max; }
    }
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static ArrowFragmentEntity model;

    public static void receive(GungangNetwork.ArrowBurst packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || packet.stops().length > ArrowGrenadeEntity.DIRECTIONS.size()) return;
        BURSTS.add(new Burst(packet, level.getGameTime()));
        while (BURSTS.size() > 32) BURSTS.remove(0);
    }

    /** Shards that run into a body stop there (the server hit it); finished bursts are dropped. */
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level;
        if (level == null) { BURSTS.clear(); model = null; return; }
        long now = level.getGameTime();
        BURSTS.removeIf(b -> now - b.start < 0 || (now - b.start) * b.speed > b.longest() + b.speed);
        for (var burst : BURSTS) {
            double flown = (now - burst.start + 1) * burst.speed;
            var area = new AABB(burst.origin, burst.origin).inflate(Math.min(flown, burst.longest()) + 1);
            var bodies = level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator());
            if (bodies.isEmpty()) continue;
            for (int i = 0; i < burst.stops.length; i++) {
                if (burst.stopped[i]) continue;
                var direction = ArrowGrenadeEntity.DIRECTIONS.get(i);
                double from = Math.min((now - burst.start) * burst.speed, burst.stops[i]), to = Math.min(flown, burst.stops[i]);
                var a = burst.origin.add(direction.scale(from)); var b = burst.origin.add(direction.scale(to));
                for (var body : bodies) {
                    var box = body.getBoundingBox().inflate(HIT_MARGIN);
                    var clip = box.contains(a) ? java.util.Optional.of(a) : box.clip(a, b);
                    if (clip.isPresent()) { burst.stopped[i] = true; burst.stoppedAt[i] = clip.get().distanceTo(burst.origin); break; }
                }
            }
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || BURSTS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (model == null || model.level() != mc.level) model = GadgetItems.ARROW_FRAGMENT.get().create(mc.level);
        if (model == null) return;
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var dispatcher = mc.getEntityRenderDispatcher();
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        for (var burst : BURSTS) {
            double flown = (now - burst.start + partial) * burst.speed;
            for (int i = 0; i < burst.stops.length; i++) {
                double limit = burst.stopped[i] ? burst.stoppedAt[i] : burst.stops[i];
                // Gone once it has reached its stop, as the fragment entity was discarded there.
                if (flown >= limit + (burst.stopped[i] ? 0 : burst.speed)) continue;
                var direction = ArrowGrenadeEntity.DIRECTIONS.get(i);
                var at = burst.origin.add(direction.scale(Math.min(flown, limit)));
                float yaw = (float) (Math.atan2(direction.x, direction.z) * 180 / Math.PI);
                float pitch = (float) (Math.atan2(direction.y, direction.horizontalDistance()) * 180 / Math.PI);
                model.setPos(at); model.setYRot(yaw); model.yRotO = yaw; model.setXRot(pitch); model.xRotO = pitch;
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
                dispatcher.render(model, at.x - camera.x, at.y - camera.y, at.z - camera.z, yaw, partial, event.getPoseStack(), buffers, light);
            }
        }
        buffers.endBatch();
    }

    private ArrowBursts() { }
}
