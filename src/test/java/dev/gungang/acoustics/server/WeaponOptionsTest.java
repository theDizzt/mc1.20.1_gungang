package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WeaponOptionsTest {
    @Test void legacyJsonGetsHealthSpeedAndUnlimitedDefaults() {
        var w=ServerFiles.GSON.fromJson("{\"id\":\"old\"}",Loadouts.Weapon.class);
        assertEquals(100,w.maxHealth());assertEquals(100,w.movementSpeed());assertEquals(0,w.cooldownSeconds());
    }
    @Test void wireViewKeepsStatsAndCooldownButNotNbt() {
        var w=new Loadouts.Weapon("test","private nbt","test","",null,"","",150d,85d,4.5d);
        var round=ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(w.view()),Loadouts.Weapon.class);
        assertEquals("",round.itemNbt());assertEquals(150,round.maxHealth());assertEquals(85,round.movementSpeed());assertEquals(4.5,round.cooldownSeconds());
    }
    @Test void invalidNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new Loadouts.Weapon("x","","","",null,"","",0d,100d,0d));
        assertThrows(IllegalArgumentException.class,()->new Loadouts.Weapon("x","","","",null,"","",100d,Double.NaN,0d));
        assertThrows(IllegalArgumentException.class,()->new Loadouts.Weapon("x","","","",null,"","",100d,100d,-1d));
    }
}
