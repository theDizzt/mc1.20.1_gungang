package dev.gungang.acoustics.client;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class WeaponStatScaleTest {
    @Test void comparesOnlyMatchingUnits(){assertEquals(.5f,WeaponStatScale.fraction("30m",List.of("60m","999rpm","설정 필요")));}
    @Test void preservesUnknownAndZero(){assertEquals(-1,WeaponStatScale.fraction("High",List.of("100")));assertEquals(0,WeaponStatScale.fraction("0",List.of("0")));}
}
