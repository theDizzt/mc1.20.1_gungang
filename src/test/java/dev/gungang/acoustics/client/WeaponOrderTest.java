package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.Loadouts;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponOrderTest {
    private static Loadouts.Weapon weapon(String id, String category) {
        return new Loadouts.Weapon(id, "", id, "", null, "", category, null, null, null, "say hi");
    }
    @Test void weaponsSortByLeadingNumberThenNamesWithoutOne() {
        var sorted = LoadoutScreen.byNumber(List.of(weapon("96_infrared-scanner", "지원"), weapon("landmine", "설치형"), weapon("74", "지원"),
                weapon("09", "돌격"), weapon("barrier", "설치형"), weapon("28", "근접")));
        assertEquals(List.of("09", "28", "74", "96_infrared-scanner", "barrier", "landmine"), sorted.stream().map(Loadouts.Weapon::id).toList());
    }
}
