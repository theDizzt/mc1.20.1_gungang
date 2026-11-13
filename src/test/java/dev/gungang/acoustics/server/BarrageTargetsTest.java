package dev.gungang.acoustics.server;

import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarrageTargetsTest {
    private static final Vec3 CENTER = new Vec3(0, 64, 0);

    @Test void defaultsFireTwentyFourMissilesOfThirtyTwo() {
        var c = ExtraGadgetConfig.BARRAGE_DEFAULTS;
        assertEquals(8, c.volleys());
        assertEquals(24, c.volleys() * c.volleySize());
        assertEquals(32, c.radius()); assertEquals(32, c.damage()); assertEquals(4, c.blastRadius());
        assertEquals(3, c.delaySeconds()); assertEquals(210, c.cooldownSeconds());
    }
    @Test void aimsAtTheThreeBiggestSeparateCrowds() {
        var people = List.of(
                new Vec3(10, 64, 10), new Vec3(11, 64, 10), new Vec3(10, 64, 11), new Vec3(11, 64, 11), // 4 together
                new Vec3(-15, 64, 0), new Vec3(-14, 64, 1), new Vec3(-15, 64, 1),                       // 3 together
                new Vec3(0, 64, -20), new Vec3(1, 64, -20),                                             // 2 together
                new Vec3(25, 64, -5));                                                                  // alone
        var picks = BarrageTargets.pick(people, 3, 4, CENTER, 32, new Random(1));
        assertEquals(3, picks.size());
        assertTrue(picks.get(0).distanceTo(new Vec3(10.5, 64, 10.5)) < 2, "biggest crowd first: " + picks);
        assertTrue(picks.get(1).distanceTo(new Vec3(-14.7, 64, .7)) < 2, "second crowd: " + picks);
        assertTrue(picks.get(2).distanceTo(new Vec3(.5, 64, -20)) < 2, "third crowd: " + picks);
        // Separate crowds: no two blasts overlap.
        for (int i = 0; i < 3; i++) for (int j = i + 1; j < 3; j++) assertTrue(picks.get(i).distanceTo(picks.get(j)) >= 8);
    }
    @Test void fewerCrowdsThanMissilesRepeatTheBestSpots() {
        var picks = BarrageTargets.pick(List.of(new Vec3(3, 64, 3), new Vec3(4, 64, 3)), 3, 4, CENTER, 32, new Random(2));
        assertEquals(3, picks.size());
        assertEquals(1, picks.stream().distinct().count());
    }
    @Test void anEmptyAreaGetsRandomPointsInsideIt() {
        var picks = BarrageTargets.pick(List.of(), 3, 4, CENTER, 32, new Random(3));
        assertEquals(3, picks.size());
        for (var p : picks) assertTrue(Math.hypot(p.x, p.z) <= 32);
        assertTrue(BarrageTargets.pick(List.of(new Vec3(0, 64, 0)), 0, 4, CENTER, 32, new Random(4)).isEmpty());
    }
    @Test void liveServerFilesMatchTheDefaults() throws Exception {
        var item = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(java.nio.file.Path.of("server_resource/config/gungang/item/perimeter_barrage.json")), ExtraGadgetConfig.Barrage.class);
        assertEquals(ExtraGadgetConfig.BARRAGE_DEFAULTS, item);
        var weapon = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(java.nio.file.Path.of("server_resource/config/gungang/server/sub_weapons/73_perimeter-barrage.json")), Loadouts.Weapon.class);
        assertEquals("73", weapon.id());
        assertEquals("2026-12-19", weapon.releaseDate());
        assertEquals(210d, weapon.cooldownSeconds());
        assertTrue(weapon.itemNbt().contains("gungang:perimeter_barrage"));
    }
}
