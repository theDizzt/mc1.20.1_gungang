package dev.gungang.acoustics.server;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcidSprayerTest {
    private static Loadouts.Weapon weapon(String id, String category) {
        return new Gson().fromJson("{\"id\":\"" + id + "\",\"itemNbt\":\"{}\",\"name\":\"w" + id + "\",\"category\":\"" + category + "\"}", Loadouts.Weapon.class);
    }
    @Test void defaultsRemainStableWhileMagazineCanBeConfigured() {
        var c = ExtraGadgetConfig.ACID_SPRAYER_DEFAULTS;
        assertEquals(60, c.magazine()); assertEquals(3, c.cloudRadius()); assertEquals(3, c.cloudSeconds());
        assertEquals(15, c.damage() / c.damageIntervalSeconds(), 1e-9);
        assertEquals(1, c.lingerPoisonSeconds()); assertEquals(3, c.pushAmmoCost()); assertEquals(16, c.pushRange());
        assertEquals(3, c.regenDelaySeconds()); assertEquals(.2, c.regenIntervalSeconds());
        // Operator-owned server_resource files may be edited or absent on another checkout.
        var gson = new Gson();
        var json = gson.toJsonTree(c).getAsJsonObject();
        json.addProperty("magazine", 90);
        var configured = gson.fromJson(json, ExtraGadgetConfig.AcidSprayer.class);
        assertEquals(90, configured.magazine());
        assertEquals(c.cloudRadius(), configured.cloudRadius());
        assertEquals(c.pushAmmoCost(), configured.pushAmmoCost());
        assertEquals(60, ExtraGadgetConfig.ACID_SPRAYER_DEFAULTS.magazine());
    }
    @Test void hiddenGasGunAcceptsConfiguredHealth() {
        var json = new com.google.gson.JsonObject();
        json.addProperty("id", "36");
        json.addProperty("category", Loadouts.Weapon.HIDDEN);
        json.addProperty("name", "산성가스분사기");
        json.addProperty("itemNbt", "{Count:1b,id:\"tacz:modern_kinetic_gun\",tag:{GunId:\"hlm:dis\"}}");
        json.addProperty("maxHealth", 123);
        json.addProperty("movementSpeed", 111);
        json.addProperty("releaseDate", "2026-11-16");
        var w = new Gson().fromJson(json, Loadouts.Weapon.class);
        assertEquals("36", w.id());
        assertTrue(w.hidden());
        assertEquals(123d, w.maxHealth()); assertEquals(111d, w.movementSpeed());
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
