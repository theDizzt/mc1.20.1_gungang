package dev.gungang.acoustics.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.Vec3;

/**
 * Aim points of one 경계 감시 포격 volley: the spots where the most people stand together. Each person is a candidate
 * centre, scored by how many people its blast would reach; the best are taken in turn, skipping any candidate whose
 * blast would overlap a spot already chosen, so a volley spreads over separate crowds. When fewer separate crowds than
 * missiles exist, the best spots are aimed at again; with nobody in the area, missiles land at random inside it.
 */
public final class BarrageTargets {
    /**
     * @param people positions (feet) of everyone inside the area
     * @param center the area's centre; {@code radius} its horizontal radius (random fallback points stay inside)
     */
    public static List<Vec3> pick(List<Vec3> people, int count, double blastRadius, Vec3 center, double radius, Random random) {
        var picks = new ArrayList<Vec3>();
        if (count <= 0) return picks;
        if (people.isEmpty()) {
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * radius;
                picks.add(new Vec3(center.x + Math.cos(angle) * r, center.y, center.z + Math.sin(angle) * r));
            }
            return picks;
        }
        double reach = blastRadius * blastRadius;
        // Most people within one blast first; equal crowds in random order.
        var ranked = new ArrayList<Vec3>(people);
        java.util.Collections.shuffle(ranked, random);
        ranked.sort(Comparator.comparingLong((Vec3 p) -> people.stream().filter(o -> o.distanceToSqr(p) <= reach).count()).reversed());
        var separate = new ArrayList<Vec3>();
        for (var spot : ranked) {
            if (separate.size() >= count) break;
            // Two blasts overlap when their centres are closer than twice the blast radius.
            if (separate.stream().noneMatch(s -> s.distanceToSqr(spot) < 4 * reach)) separate.add(spot);
        }
        for (int i = 0; picks.size() < count; i++) picks.add(separate.get(i % separate.size()));
        return picks;
    }
    private BarrageTargets() { }
}
