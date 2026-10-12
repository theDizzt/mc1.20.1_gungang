package dev.gungang.acoustics.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommandLoadoutTest {
    @Test void emptyNbtUsesCommandAndItemsTakePriority() {
        var w=ServerFiles.GSON.fromJson("{\"id\":\"test\",\"nbt\":\"\",\"command\":\"/give @s minecraft:stick\"}",Loadouts.Weapon.class);
        assertTrue(w.commandOnly());assertEquals("give @s minecraft:stick",w.command());
        var item=ServerFiles.GSON.fromJson("{\"nbt\":\"{id:'minecraft:stick',Count:1b}\",\"command\":\"say unused\"}",Loadouts.Weapon.class);
        assertFalse(item.commandOnly());assertFalse(w.view().commandOnly());assertEquals("",w.view().command());
    }
    @Test void missingNbtAndEmptyCompoundAlsoUseCommand() {
        assertTrue(ServerFiles.GSON.fromJson("{\"command\":\"say ok\"}",Loadouts.Weapon.class).commandOnly());
        assertTrue(ServerFiles.GSON.fromJson("{\"itemNbt\":\"{}\",\"command\":\"say ok\"}",Loadouts.Weapon.class).commandOnly());
    }
}
