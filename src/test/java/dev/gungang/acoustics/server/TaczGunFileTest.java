package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaczGunFileTest {
    private static final String SOURCE = """
            {
              // 弹容
              "ammo_amount": 100,
              "rpm": 550,
              /* bullet */
              "bullet": {
                "damage": 11,
                "extra_damage": {
                  "head_shot_multiplier": 1.25,
                  "damage_adjust": [
                    {"distance": 50, "damage": 12},
            \t\t{"distance": "infinite", "damage": 6}
                  ]
                }
                },
              "weight": 30,
              "movement_speed": {"base": 0.0, "aim": -0.2, "reload": -0.3}
            }""";

    @Test void scansPathsThroughComments() {
        var file = TaczGunFile.parse(Path.of("m2hb_data.json"), SOURCE);
        assertEquals("30", file.values().get("weight").text());
        assertEquals("-0.2", file.values().get("movement_speed.aim").text());
        assertEquals("\"infinite\"", file.values().get("bullet.extra_damage.damage_adjust.1.distance").text());
        var editable = file.editable();
        assertTrue(editable.containsKey("rpm"));
        assertEquals("거리별 피해 · 50블록까지", editable.get("bullet.extra_damage.damage_adjust.0.damage").label());
        assertEquals("거리별 피해 · 그 이후", editable.get("bullet.extra_damage.damage_adjust.1.damage").label());
    }
    @Test void replacesOnlyTheValuesAndKeepsComments() {
        var file = TaczGunFile.parse(Path.of("m2hb_data.json"), SOURCE);
        String out = file.patched(Map.of("weight", "0", "rpm", "600.0", "bullet.extra_damage.damage_adjust.1.damage", "7.50"));
        assertTrue(out.contains("// 弹容"));
        assertTrue(out.contains("/* bullet */"));
        assertTrue(out.contains("\"weight\": 0,"));
        assertTrue(out.contains("\"rpm\": 600,"));
        assertTrue(out.contains("{\"distance\": \"infinite\", \"damage\": 7.5}"));
        assertEquals("0", TaczGunFile.parse(Path.of("m2hb_data.json"), out).values().get("weight").text());
    }
    @Test void rejectsInvalidValuesBeforeWriting() {
        var file = TaczGunFile.parse(Path.of("m2hb_data.json"), SOURCE);
        assertThrows(IllegalArgumentException.class, () -> file.patched(Map.of("rpm", "550.5")));
        assertThrows(IllegalArgumentException.class, () -> file.patched(Map.of("weight", "-101")));
        assertEquals(SOURCE, file.patched(Map.of("movement_speed.base", "0", "rpm", "550.00")));
        assertThrows(IllegalArgumentException.class, () -> file.patched(Map.of("ammo", "1")));
        assertThrows(IllegalArgumentException.class, () -> file.patched(Map.of("bullet.damage", "abc")));
    }
}
