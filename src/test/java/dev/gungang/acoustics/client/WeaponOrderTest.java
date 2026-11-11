package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.Loadouts;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponOrderTest {
    private static Loadouts.Weapon weapon(String id, String category) {
        return new Loadouts.Weapon(id, "", id, "", null, "", category, null, null, null, "say hi");
    }
    @Test void categoriesFollowTheirLowestNumberAndWeaponsSortByNumberInside() {
        var sorted = LoadoutScreen.byNumber(List.of(weapon("96_infrared-scanner", "지원"), weapon("landmine", "설치형"), weapon("74", "지원"),
                weapon("09", "돌격"), weapon("barrier", "설치형"), weapon("28", "근접"), weapon("03", "돌격"), weapon("90", "설치형")));
        assertEquals(List.of("03", "09", "28", "74", "96_infrared-scanner", "90", "barrier", "landmine"), sorted.stream().map(Loadouts.Weapon::id).toList());
    }
    @Test void englishNameReachesTheClientViewAndIsValidated() {
        var weapon = new Loadouts.Weapon("74", "", "헤르메스의 부츠", "", null, "", "지원", null, null, null, "say hi", false, "", List.of(), null, null, " Hermes' Boots ");
        assertEquals("Hermes' Boots", weapon.view().nameEn());
        assertEquals("", weapon("01", "돌격").nameEn());
        assertThrows(IllegalArgumentException.class, () -> new Loadouts.Weapon("74", "", "x", "", null, "", "지원", null, null, null, "say hi", false, "", List.of(), null, null, "a\nb"));
    }
}
