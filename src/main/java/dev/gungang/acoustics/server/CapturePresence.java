package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.world.phys.AABB;

/** Small exit margin prevents movement on the boundary from alternating presence every tick. */
final class CapturePresence {
    private final Set<UUID> inside=new HashSet<>();
    boolean update(UUID id,AABB player,AABB zone) {
        boolean present=player.intersects(inside.contains(id)?zone.inflate(0.15,0,0.15):zone);
        if(present)inside.add(id);else inside.remove(id);
        return present;
    }
    void retain(Set<UUID> eligible) { inside.retainAll(eligible); }
    void clear() { inside.clear(); }
}
