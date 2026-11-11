package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gungang.acoustics.server.EffectColors;
import dev.gungang.acoustics.server.RapierItem;
import dev.gungang.acoustics.server.SurvivalGadgets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Weapon visuals everyone sees:
 * <ul>
 * <li>28 rapier combo: one glowing orb per combo tier circling the wielder's waist, in a colour per tier (white-blue,
 * cyan, yellow, orange, red), read from the combo the server writes onto the held blade.</li>
 * <li>28 rapier dash flash: a bright streak along the dash and a flash at its start, in the owner's "rapier" colours.</li>
 * <li>93 jetpack: a glowing ribbon from each nozzle while flying, in the owner's "jetpack" colours. The damaging
 * flame trail is separate and unchanged.</li>
 * </ul>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = dev.gungang.acoustics.GungangAcoustics.ID, value = Dist.CLIENT)
public final class WeaponFx {
    /** Orb colour per combo tier 1..5: fixed, so every player reads the same tier from it. */
    static final int[] TIER_COLORS = {0x9FD8FF, 0x4DE1FF, 0xFFD84D, 0xFF8A3D, 0xFF3D7A};
    private record Flash(Vec3 from, Vec3 to, long born, int[] colors) {}
    private record Point(Vec3 pos, long tick) {}
    private static final List<Flash> FLASHES = new ArrayList<>();
    /** Recent nozzle positions per jetpack wearer (entity id): left and right trails. */
    private static final Map<Integer, Deque<Point>[]> JETS = new HashMap<>();
    private static final int FLASH_LIFE = 8, JET_LIFE = 12;

    /** Rapier dash start (RapierTrail.receive): streak over the dash path and a flash where it begins. */
    static void rapierDash(Player p, float yaw, float distance) {
        double angle = Math.toRadians(yaw);
        var from = p.position().add(0, p.getBbHeight() * .55, 0);
        var to = from.add(-Math.sin(angle) * distance, 0, Math.cos(angle) * distance);
        int[] colors = EffectColors.client(p.getUUID(), "rapier");
        FLASHES.add(new Flash(from, to, p.level().getGameTime(), colors));
        if (FLASHES.size() > 32) FLASHES.remove(0);
        p.level().addParticle(ParticleTypes.FLASH, from.x, from.y, from.z, 0, 0, 0);
        var random = p.getRandom();
        for (int i = 0; i < 14; i++) {
            int color = EffectColors.at(colors, random.nextFloat());
            p.level().addParticle(new DustParticleOptions(EffectColors.vector(color), 1.1f), from.x + random.nextGaussian() * .25,
                    from.y + random.nextGaussian() * .35, from.z + random.nextGaussian() * .25, 0, 0, 0);
        }
    }

    static int comboTier(Player p) {
        var blade = p.getMainHandItem();
        if (!(blade.getItem() instanceof RapierItem) || !blade.hasTag()) return 0;
        var tag = blade.getTag();
        return tag.getLong(RapierItem.COMBO_END) > p.level().getGameTime() ? Math.max(0, Math.min(5, tag.getInt(RapierItem.COMBO))) : 0;
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (var p : mc.level.players()) {
            if (p.isSpectator() || p.isInvisible()) continue;
            if (SurvivalGadgets.jetting(p)) {
                var trails = JETS.computeIfAbsent(p.getId(), id -> new Deque[]{new ArrayDeque<Point>(), new ArrayDeque<Point>()});
                double yaw = Math.toRadians(p.yBodyRot);
                var back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(.38);
                var side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(.2);
                var base = p.position().add(0, p.getBbHeight() * .42, 0).add(back);
                trails[0].addFirst(new Point(base.add(side), now));
                trails[1].addFirst(new Point(base.subtract(side), now));
            }
            // Tier 5 also throws off sparks so it reads from afar.
            if (comboTier(p) == 5 && now % 3 == 0)
                mc.level.addParticle(ParticleTypes.END_ROD, p.getX() + (p.getRandom().nextDouble() - .5) * .8, p.getY() + .4 + p.getRandom().nextDouble() * 1.2,
                        p.getZ() + (p.getRandom().nextDouble() - .5) * .8, 0, .02, 0);
        }
        for (var trails : JETS.values()) for (var trail : trails) trail.removeIf(point -> now - point.tick() > JET_LIFE);
        JETS.values().removeIf(trails -> trails[0].isEmpty() && trails[1].isEmpty());
        FLASHES.removeIf(f -> now - f.born() > FLASH_LIFE || now < f.born());
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        double now = mc.level.getGameTime() + partial;
        var cameraPos = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        Matrix4f matrix = pose.last().pose();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        boolean drew = false;
        // Rapier dash streaks: a wide glow and a pale core, fading out.
        var eye = new Vector3f();
        for (var flash : FLASHES) {
            float fade = (float) Math.max(0, 1 - (now - flash.born()) / FLASH_LIFE);
            if (fade <= 0) continue;
            int segments = 8;
            for (int i = 0; i < segments; i++) {
                var a = flash.from().lerp(flash.to(), i / (double) segments).subtract(cameraPos);
                var b = flash.from().lerp(flash.to(), (i + 1) / (double) segments).subtract(cameraPos);
                Vector3f from = new Vector3f((float) a.x, (float) a.y, (float) a.z), to = new Vector3f((float) b.x, (float) b.y, (float) b.z);
                var dir = new Vector3f(to).sub(from);
                if (dir.lengthSquared() < 1e-6f) continue;
                dir.normalize();
                int glow = EffectColors.at(flash.colors(), (i + .5f) / segments);
                int core = EffectColors.at(new int[]{glow, 0xFFFFFF}, .7f);
                PropObjectRenderer.strip(buffer, matrix, from, to, dir, eye, .32f * fade, EffectColors.red(glow), EffectColors.green(glow), EffectColors.blue(glow), .35f * fade);
                PropObjectRenderer.strip(buffer, matrix, from, to, dir, eye, .08f * fade, EffectColors.red(core), EffectColors.green(core), EffectColors.blue(core), .85f * fade);
                drew = true;
            }
        }
        // Rapier combo orbs.
        var left = event.getCamera().getLeftVector();
        var up = event.getCamera().getUpVector();
        for (var p : mc.level.players()) {
            int tier = comboTier(p);
            if (tier == 0 || p.isInvisible() || p.isSpectator()) continue;
            // Your own orbs would circle the camera in first person; the HUD shows your combo there.
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            var center = p.getPosition(partial).add(0, p.getBbHeight() * .5, 0);
            int color = TIER_COLORS[tier - 1];
            float pulse = tier == 5 ? (float) (1 + .25 * Math.sin(now * .6)) : 1;
            for (int i = 0; i < tier; i++) {
                double angle = now * .14 + i * Math.PI * 2 / tier;
                var orb = center.add(Math.cos(angle) * .78, Math.sin(now * .1 + i) * .08, Math.sin(angle) * .78).subtract(cameraPos);
                orb(buffer, matrix, orb, left, up, .16f * pulse, color, .35f);
                orb(buffer, matrix, orb, left, up, .06f * pulse, EffectColors.at(new int[]{color, 0xFFFFFF}, .6f), .9f);
                drew = true;
            }
        }
        // Jetpack nozzle ribbons.
        for (var entry : JETS.entrySet()) {
            if (!(mc.level.getEntity(entry.getKey()) instanceof Player p)) continue;
            int[] colors = EffectColors.client(p.getUUID(), "jetpack");
            for (var trail : entry.getValue()) {
                if (trail.size() < 2) continue;
                var points = new ArrayList<Vec3>();
                for (var point : trail) points.add(point.pos());
                ShurikenRenderer.ribbon(buffer, matrix, points, cameraPos, cameraPos, .2f, colors, 0, .6f);
                ShurikenRenderer.ribbon(buffer, matrix, points, cameraPos, cameraPos, .07f, colors, .65f, .8f);
                drew = true;
            }
        }
        if (drew) buffers.endBatch(RenderType.lightning());
    }

    /** A camera-facing diamond of half size {@code size} at {@code at} (camera-relative). */
    private static void orb(VertexConsumer buffer, Matrix4f matrix, Vec3 at, Vector3f left, Vector3f up, float size, int color, float alpha) {
        float r = EffectColors.red(color), g = EffectColors.green(color), b = EffectColors.blue(color);
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        float lx = left.x() * size, ly = left.y() * size, lz = left.z() * size, ux = up.x() * size, uy = up.y() * size, uz = up.z() * size;
        // Both windings, so it shows whichever way it faces after the camera moves within a frame.
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1 : -1;
            buffer.vertex(matrix, x + ux, y + uy, z + uz).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, x + lx * s, y + ly * s, z + lz * s).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, x - ux, y - uy, z - uz).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, x - lx * s, y - ly * s, z - lz * s).color(r, g, b, alpha).endVertex();
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { FLASHES.clear(); JETS.clear(); }
    private WeaponFx() {}
}
