package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoadoutRulesTest {
    @Test void outsideMatchDoesNotRequireTeamOrSpawn() {
        assertTrue(LoadoutRules.allowed("LOBBY",true,false,false));
        assertTrue(LoadoutRules.allowed("PREPARING",true,true,false));
        assertFalse(LoadoutRules.allowed("LOBBY",false,false,false));
    }
    @Test void runningMatchRequiresLivingTeamMemberInsideSpawn() {
        assertTrue(LoadoutRules.allowed("RUNNING",true,true,true));
        assertFalse(LoadoutRules.allowed("RUNNING",true,true,false));
        assertFalse(LoadoutRules.allowed("RUNNING",true,false,true));
        assertFalse(LoadoutRules.allowed("RUNNING",false,true,true));
        assertFalse(LoadoutRules.allowed("FINISHING",true,true,true));
    }
    @Test void floorColorFollowsScoringOwnershipNotOccupancy() {
        var game=new CaptureGame();
        game.tick(true,false,2,30);assertEquals(0,game.scoringOwner());
        for(int i=0;i<199;i++)game.tick(true,false,2,30);
        assertEquals(1,game.scoringOwner());
        game.tick(true,true,2,30);assertEquals(1,game.scoringOwner());
        game.tick(false,true,2,30);assertEquals(0,game.scoringOwner());
        for(int i=0;i<400;i++)game.tick(false,true,2,30);
        assertEquals(-1,game.scoringOwner());
    }
}
