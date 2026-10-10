package dev.gungang.acoustics.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Bounded water-path sampling with perceptually compressed interface transmission loss. */
final class WaterAcoustics {
    static WorldAcoustics.Transmission evaluate(Level level, Vec3 source, Vec3 listener) {
        double distance = source.distanceTo(listener);
        int samples = Math.max(1, Math.min(96, (int) Math.ceil(distance * 2)));
        boolean previous = submerged(level, source);
        int boundaries = 0;
        double water = 0;
        for (int i = 1; i <= samples; i++) {
            boolean current = submerged(level, source.lerp(listener, (double) i / samples));
            if (current != previous) boundaries++;
            water += (current ? (previous ? 1 : 0.5) : (previous ? 0.5 : 0)) * distance / samples;
            previous = current;
        }
        return transmission(water, boundaries, previous);
    }

    static WorldAcoustics.Transmission transmission(double waterDistance, int boundaries, boolean listenerUnderwater) {
        // Air/water impedance mismatch strongly reflects sound; compress the loss for gameplay.
        double interfaceAmplitude = Math.sqrt(4.0 * 3600 / (3601.0 * 3601));
        double gain = Math.pow(interfaceAmplitude, 0.35 * Math.min(4, Math.max(0, boundaries)))
                * Math.exp(-0.0015 * Math.max(0, waterDistance));
        double hf = Math.pow(0.55, Math.min(4, Math.max(0, boundaries)))
                * Math.exp(-0.012 * Math.max(0, waterDistance)) * (listenerUnderwater ? 0.45 : 1);
        return new WorldAcoustics.Transmission(gain, hf);
    }

    private static boolean submerged(Level level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        if (!level.hasChunkAt(pos)) return false;
        var fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && point.y < pos.getY() + fluid.getHeight(level, pos);
    }
    private WaterAcoustics() { }
}
