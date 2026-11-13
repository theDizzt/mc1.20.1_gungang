package dev.gungang.acoustics.server;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcidSprayerTest {
    private static Loadouts.Weapon weapon(String id, String category) {
        return new Gson().fromJson("{\"id\":\"" + id + "\",\"itemNbt\":\"{}\",\"name\":\"w" + id + "\",\"category\":\"" + category + "\"}", Loadouts.Weapon.class);
    }
    @Test void defaultsMatchTheRequestAndTheLiveSettings() throws Exception {
        var c = ExtraGadgetConfig.ACID_SPRAYER_DEFAULTS;
        assertEquals(60, c.magazine()); assertEquals(3, c.cloudRadius()); assertEquals(3, c.cloudSeconds());
        assertEquals(15, c.damage() / c.damageIntervalSeconds(), 1e-9);
        assertEquals(1, c.lingerPoisonSeconds()); assertEquals(3, c.pushAmmoCost()); assertEquals(16, c.pushRange());
        assertEquals(3, c.regenDelaySeconds()); assertEquals(.2, c.regenIntervalSeconds());
        var live = new Gson().fromJson(Files.readString(Path.of("server_resource/config/gungang/item/acid_sprayer.json")), ExtraGadgetConfig.AcidSprayer.class);
        assertEquals(c, live);
    }
    @Test void weaponFileIsAHiddenPrimaryOnTheGasGun() throws Exception {
        var w = new Gson().fromJson(Files.readString(Path.of("server_resource/config/gungang/server/weapons/36_acid-gas-sprayer.json")), Loadouts.Weapon.class);
        assertEquals("36", w.id());
        assertTrue(w.hidden());
        assertEquals(111d, w.maxHealth()); assertEquals(111d, w.movementSpeed());
        assertEquals("2026-11-16", w.releaseDate());
        assertTrue(w.itemNbt().contains("GunId:\"" + AcidSprayer.GUN_ID + "\""));
    }
    @Test void hiddenCategoryIsListedLast() {
        var list = dev.gungang.acoustics.client.LoadoutScreenOrder.order(List.of(
                weapon("36", Loadouts.Weapon.HIDDEN), weapon("01", "돌격소총"), weapon("14", "특수화기"), weapon("02", "돌격소총")));
        assertEquals(List.of("01", "02", "14", "36"), list.stream().map(Loadouts.Weapon::id).toList());
        assertFalse(weapon("01", "돌격소총").hidden());
    }
}
