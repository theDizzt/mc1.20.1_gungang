package dev.gungang.acoustics.client;

import com.tacz.guns.client.sound.GunSoundInstance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaczCompatibilityTest {
    static final class AddonTrackingSound extends GunSoundInstance { }

    @Test void recognizesBaseGunSoundsAndAddonSubclasses() {
        assertTrue(TaczCompatibility.managesOwnDistance(new GunSoundInstance()));
        assertTrue(TaczCompatibility.managesOwnDistance(new AddonTrackingSound()));
    }

    @Test void leavesUnrelatedAndNonSpatialSoundsAlone() {
        assertFalse(TaczCompatibility.managesOwnDistance(new Object()));
        assertFalse(TaczCompatibility.managesOwnDistance("tacz:gun"));
    }
}
