package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuxBarTest {
    @Test void healthBarTurnsOrangeAtHalfAndRedAtQuarter() {
        var health = AuxBar.Kind.HEALTH;
        assertEquals(health.color, AuxBar.barColor(health, .51f));
        assertEquals(AuxBar.WARNING, AuxBar.barColor(health, .5f));
        assertEquals(AuxBar.WARNING, AuxBar.barColor(health, .26f));
        assertEquals(AuxBar.DANGER, AuxBar.barColor(health, .25f));
        assertEquals(AuxBar.TEXT, AuxBar.textColor(health, .1f));
    }
    @Test void ammoKeepsItsBarAndWarnsInTheText() {
        var ammo = AuxBar.Kind.AMMO;
        assertEquals(ammo.color, AuxBar.barColor(ammo, .1f));
        assertEquals(AuxBar.TEXT, AuxBar.textColor(ammo, .6f));
        assertEquals(AuxBar.TEXT_WARNING, AuxBar.textColor(ammo, .5f));
        assertEquals(AuxBar.TEXT_DANGER, AuxBar.textColor(ammo, .25f));
    }
    @Test void cooldownAndOtherKeepTheirColours() {
        for (var kind : new AuxBar.Kind[]{AuxBar.Kind.COOLDOWN, AuxBar.Kind.OTHER}) {
            assertEquals(kind.color, AuxBar.barColor(kind, .05f));
            assertEquals(AuxBar.TEXT, AuxBar.textColor(kind, .05f));
        }
    }
    @Test void timesUseOneDecimalAndSeconds() {
        assertEquals("2.4s", AuxBar.seconds(48));
        assertEquals("0.0s", AuxBar.seconds(-5));
        assertEquals(0, AuxBar.fraction(5, 0));
        assertEquals(1, AuxBar.fraction(9, 4));
    }
    @Test void iconsAreNineByNine() {
        for (var kind : AuxBar.Kind.values()) {
            var rows = AuxBar.pattern(kind);
            assertEquals(9, rows.length);
            for (var row : rows) assertEquals(9, row.length(), kind + ": " + row);
        }
    }
}
