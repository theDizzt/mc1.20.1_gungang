package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropCatalogNumbersTest {
    @Test void replacedAndAddedTypesKeepTheirNumbers(){
        assertEquals("07",PropCatalog.number("minecraft:iron_door"));assertEquals("08",PropCatalog.number("minecraft:stripped_oak_log"));
        assertEquals("12",PropCatalog.number("minecraft:stone_bricks"));assertEquals("29",PropCatalog.number("minecraft:emerald_block"));
        assertEquals("30",PropCatalog.number("minecraft:amethyst_block"));assertEquals("45",PropCatalog.number("minecraft:stone_brick_slab"));
        assertEquals("112",PropCatalog.number("minecraft:oak_sign"));assertEquals("153",PropCatalog.number("minecraft:bamboo_mosaic_stairs"));
        assertEquals("200",PropCatalog.number("minecraft:blackstone"));assertEquals("209",PropCatalog.number("minecraft:sponge"));
        assertEquals("214",PropCatalog.number("minecraft:cartography_table"));assertEquals("218",PropCatalog.number("minecraft:piston"));
        assertEquals("220",PropCatalog.number("minecraft:red_mushroom"));
        assertEquals("221",PropCatalog.number("gungang:desk_clock"));assertEquals("222",PropCatalog.number("gungang:money_cart"));
        // Removed types are gone from the list.
        for(String gone:new String[]{"minecraft:dark_oak_door","minecraft:cherry_planks","minecraft:chiseled_sandstone","minecraft:crimson_planks","minecraft:warped_planks","minecraft:mossy_stone_bricks","minecraft:bamboo_planks"})
            assertEquals("??",PropCatalog.number(gone),gone);
    }
    @Test void assortedTypesListTheirLooks(){
        assertTrue(PropCatalog.assorted("minecraft:oak_planks"));assertEquals(11,PropCatalog.kinds("minecraft:oak_planks").size());
        assertTrue(PropCatalog.kinds("minecraft:oak_log").contains("minecraft:bamboo_block"));
        assertEquals(java.util.List.of("minecraft:sandstone","minecraft:smooth_sandstone","minecraft:chiseled_sandstone"),PropCatalog.kinds("minecraft:sandstone"));
        // 211: full-block variants only; 212 has none. The nether brick fence is one of 5's fences, the walls among 210's.
        assertEquals(java.util.List.of("minecraft:nether_bricks","minecraft:cracked_nether_bricks","minecraft:chiseled_nether_bricks"),PropCatalog.kinds("minecraft:nether_bricks"));
        assertFalse(PropCatalog.assorted("minecraft:red_nether_bricks"));
        assertTrue(PropCatalog.kinds("minecraft:oak_fence").contains("minecraft:nether_brick_fence"));
        assertTrue(PropCatalog.kinds("minecraft:cobblestone_wall").containsAll(java.util.List.of("minecraft:nether_brick_wall","minecraft:red_nether_brick_wall")));
        assertFalse(PropCatalog.assorted("minecraft:sponge"));assertEquals(java.util.List.of("minecraft:sponge"),PropCatalog.kinds("minecraft:sponge"));
    }
}
