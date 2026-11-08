package dev.gungang.acoustics.server;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageFalloffTest {
    @Test void linearWritesOneBandPerBlockAndAThreshold() {
        var curve = new DamageFalloff("linear", DamageFalloff.DAMAGE, 10, 30, 14, 10, 1, "");
        var bands = curve.bands(30);
        // base until 10 (start value equals base, merged), then 25, 20, 15 for blocks 11..13, then the 10 threshold.
        assertEquals(10 + 1, bands.get(0)[0]); assertEquals(30, bands.get(0)[1]);
        assertEquals(25, bands.get(1)[1]); assertEquals(20, bands.get(2)[1]); assertEquals(15, bands.get(3)[1]);
        assertTrue(Double.isInfinite(bands.get(bands.size() - 1)[0])); assertEquals(10, bands.get(bands.size() - 1)[1]);
        assertEquals(30, curve.damageAt(5, 30)); assertEquals(20, curve.damageAt(12.7, 30)); assertEquals(10, curve.damageAt(99, 30));
    }
    @Test void reductionModeCutsAPercentageOfBase() {
        var curve = new DamageFalloff("linear", DamageFalloff.REDUCTION, 0, 0, 10, 50, 1, "");
        assertEquals(40, curve.damageAt(0, 40)); assertEquals(20, curve.damageAt(10, 40)); assertEquals(30, curve.damageAt(5, 40));
    }
    @Test void shapesStartAndEndOnTheirValues() {
        for (String shape : DamageFalloff.CURVES) {
            if (shape.equals("custom")) continue;
            var curve = new DamageFalloff(shape, DamageFalloff.DAMAGE, 0, 100, 50, 0, 4, "");
            assertEquals(1e-9 > Math.abs(curve.shape(0)) ? 0 : curve.shape(0), 0, 1e-9, shape);
            assertEquals(1, curve.shape(1), 1e-9, shape);
            assertTrue(curve.shape(.5) > 0 && curve.shape(.5) < 1, shape);
        }
    }
    @Test void customEquationUsesVariables() {
        var curve = new DamageFalloff("custom", DamageFalloff.DAMAGE, 0, 50, 10, 10, 1, "vs - (vs-ve)*t^2");
        assertEquals(50, curve.damageAt(0, 50)); assertEquals(40, curve.damageAt(5, 50));
        assertThrows(IllegalArgumentException.class, () -> new DamageFalloff("custom", DamageFalloff.DAMAGE, 0, 50, 10, 10, 1, "foo(d)").validate());
        assertEquals(3, FalloffExpression.compile("clamp(d, 1, 3)").eval(new double[]{9, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals(-8, FalloffExpression.compile("-2^3").eval(new double[8]));
    }
    @Test void invalidRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DamageFalloff("linear", DamageFalloff.DAMAGE, 20, 10, 10, 5, 1, "").validate());
        assertThrows(IllegalArgumentException.class, () -> new DamageFalloff("linear", DamageFalloff.REDUCTION, 0, 0, 10, 150, 1, "").validate());
    }
    @Test void gunFileListIsReplacedOrCreated() {
        String withList = "{\n  // comment\n  \"bullet\": {\n    \"damage\": 20,\n    \"extra_damage\": {\n      \"armor_ignore\": 0,\n      \"damage_adjust\": [\n        {\"distance\": 8, \"damage\": 20},\n        {\"distance\": \"infinite\", \"damage\": 5}\n      ]\n    }\n  }\n}";
        var file = TaczGunFile.parse(java.nio.file.Path.of("x.json"), withList);
        assertEquals(2, file.falloff().size()); assertEquals(-1, file.falloff().get(1)[0]);
        var bands = new DamageFalloff("linear", DamageFalloff.DAMAGE, 4, 20, 6, 10, 1, "").bands(20);
        var out = TaczGunFile.parse(file.path(), file.withFalloff(bands));
        assertTrue(out.source().contains("// comment"));
        assertEquals(bands.size(), out.falloff().size());
        assertEquals(10, out.falloff().get(out.falloff().size() - 1)[1]);
        String bare = "{\"bullet\": {\"damage\": 20}}";
        var created = TaczGunFile.parse(file.path(), TaczGunFile.parse(file.path(), bare).withFalloff(bands));
        JsonParser.parseString(created.source());
        assertEquals(bands.size(), created.falloff().size());
    }
    @Test void maxHealthRoundsUp() {
        assertEquals(188, LoadoutAttributes.maxHealth(187.25, 1));
        assertEquals(150, LoadoutAttributes.maxHealth(100, 1.5));
        assertEquals(115, LoadoutAttributes.maxHealth(115, 1));
    }
}
