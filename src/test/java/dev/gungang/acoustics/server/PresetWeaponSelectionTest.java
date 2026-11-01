package dev.gungang.acoustics.server;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresetWeaponSelectionTest {
    @Test void keepsAvailablePreset(){
        assertEquals("AWN",LobbyRules.preferredOrRandom(List.of("AMK","AWN"),"AWN",new Random(1)));
    }
    @Test void conflictDrawsOnlyFromUnusedWeaponsAndIsNotAlwaysFirst(){
        var seen=new HashSet<String>();var random=new Random(42);
        for(int i=0;i<100;i++)seen.add(LobbyRules.preferredOrRandom(List.of("AWN","SHOTGUN"),"AMK",random));
        assertEquals(Set.of("AWN","SHOTGUN"),seen);
    }
    @Test void simultaneousConflictingPresetsStayUnique(){
        var available=new ArrayList<>(List.of("AMK","AWN","SHOTGUN"));var allocated=new HashSet<String>();
        for(int i=0;i<3;i++){
            var choice=LobbyRules.preferredOrRandom(available,"AMK",new Random(i));
            assertTrue(allocated.add(choice));available.remove(choice);
        }
        assertThrows(IllegalStateException.class,()->LobbyRules.preferredOrRandom(available,"AMK",new Random(0)));
    }
    @Test void duplicateAllowedKeepsPresetAndMissingPresetGetsAvailableWeapon(){
        var catalog=List.of("AMK","AWN");
        assertEquals("AMK",LobbyRules.preferredOrRandom(catalog,"AMK",new Random(0)));
        assertEquals("AMK",LobbyRules.preferredOrRandom(catalog,"AMK",new Random(1)));
        assertEquals("AWN",LobbyRules.preferredOrRandom(List.of("AWN"),"removed_weapon",new Random(0)));
    }
}
