package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PatchHelpTest {
    @Test void everyPatchableTaczFieldIsExplained() {
        for (var field : TaczGunFile.FIELDS) {
            String key = (field.path().startsWith("data.attack.") ? "melee:" : "tacz:") + field.path();
            assertTrue(PatchHelp.of(key, null).startsWith("용도: ") && !PatchHelp.of(key, null).contains("값."), key);
        }
    }
    @Test void itemFieldsUseTheirSettingWhenTheMeaningDiffers() {
        assertTrue(PatchHelp.of("item:delaySeconds", "health_regeneration").contains("자연 재생"));
        assertTrue(PatchHelp.of("item:delaySeconds", "orbital_strike").contains("폭격"));
        assertTrue(PatchHelp.of("item:delaySeconds", "orbital_strike").endsWith("item/orbital_strike.json · delaySeconds"));
        assertTrue(PatchHelp.of("gg:stats.3.value", null).contains("표시 전용"));
        assertTrue(PatchHelp.of("item:health", "cracked_red_concrete").contains("금 간 블록"));
        assertTrue(PatchHelp.of("item:damage", "uxo").contains("불발탄"));
        assertTrue(PatchHelp.of("item:heal", "small_health_pack").contains("힐팩"));
    }
    @Test void estimatesShotgunDamage() {
        String text = PatchHelp.estimate(Map.of("tacz:bullet.damage", 30.0, "tacz:rpm", 240.0, "tacz:bullet.bullet_amount", 15.0,
                "tacz:bullet.extra_damage.head_shot_multiplier", 1.25, "tacz:ammo_amount", 20.0, "tacz:reload.cooldown.empty", 3.0), 100);
        assertTrue(text.contains("30 × 15갈래 = 450 · 헤드샷 562.5"), text);
        assertTrue(text.contains("450 × 240 / 60 = 1800"), text);
        assertTrue(text.contains("몸샷 1발"), text);
        assertTrue(text.contains("소진 5초 · 탄창당 9000"), text);
        assertTrue(text.contains("1125"), text); // 9000 / (5 + 3)
        assertEquals("", PatchHelp.estimate(Map.of("tacz:rpm", 600.0), 100));
    }
}
