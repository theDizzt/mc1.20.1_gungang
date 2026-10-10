package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompassMathTest {
    @Test void minecraftYawMapsToCompassDegrees() {
        assertEquals(0, CompassMath.heading(-180));
        assertEquals(90, CompassMath.heading(-90));
        assertEquals(180, CompassMath.heading(0));
        assertEquals(270, CompassMath.heading(90));
        assertEquals(0, CompassMath.heading(179.9));
        assertEquals(359, CompassMath.heading(179));
    }
    @Test void bearingsWrapAcrossNorth() {
        assertEquals(0, CompassMath.bearing(0, -1));
        assertEquals(90, CompassMath.bearing(1, 0));
        assertEquals(180, CompassMath.bearing(0, 1));
        assertEquals(270, CompassMath.bearing(-1, 0));
        assertEquals(2, CompassMath.difference(1, 359));
        assertEquals(-2, CompassMath.difference(359, 1));
    }
    @Test void firingNamesExcludeReloadsAndDryFire() {
        assertTrue(CompassMath.gunshot("rifle/shot"));
        assertTrue(CompassMath.gunshot("gun.shoot.silenced"));
        assertFalse(CompassMath.gunshot("gun.reload"));
        assertFalse(CompassMath.gunshot("gun.dry_fire"));
        assertFalse(CompassMath.gunshot("footstep"));
    }
}
