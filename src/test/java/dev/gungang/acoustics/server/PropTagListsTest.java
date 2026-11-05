package dev.gungang.acoustics.server;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropTagListsTest {
    /** Every id in a category list must be a catalog entry: a misspelled namespace silently drops the block's tag and E. */
    @Test void categoryListsNameRealCatalogBlocks() throws Exception {
        for(String list:new String[]{"SPECIAL","ATTACK","DEBUFF","SWIFT","STEALTH"}){
            var field=PropInteraction.class.getDeclaredField(list);field.setAccessible(true);
            @SuppressWarnings("unchecked") var ids=(Set<String>)field.get(null);
            for(String id:ids){
                // Colour types are listed by one variant (e.g. a shulker box colour).
                if(PropCatalog.colored(id))continue;
                assertNotEquals("??",PropCatalog.number(id),list+" "+id);
            }
        }
        assertEquals("187",PropCatalog.number("gungang:prettiest_block"));assertTrue(PropInteraction.special("gungang:prettiest_block"));
    }
}
