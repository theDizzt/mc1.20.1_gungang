package dev.gungang.acoustics.server;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotLoadoutChoiceTest {
    @Test void rangeReloadAndRecentResultsAffectSelection() {
        var close=new BotLoadoutChoice.Option("smg",100,100,false,3,12,20,600,30,2,"");
        var far=new BotLoadoutChoice.Option("sniper",100,100,false,20,40,90,60,5,3,"");
        assertEquals("smg",BotLoadoutChoice.choose(List.of(close,far),"",null,new BotLoadoutChoice.Outcome(true,0,200,5),false));
        assertEquals("sniper",BotLoadoutChoice.choose(List.of(close,far),"",null,new BotLoadoutChoice.Outcome(true,0,200,35),false));
        var fast=new BotLoadoutChoice.Option("fast",100,100,false,3,20,20,600,10,1,"");
        var slow=new BotLoadoutChoice.Option("slow",100,100,false,3,20,20,600,10,8,"");
        assertTrue(fast.combatScore(10)>slow.combatScore(10));
        var history=java.util.Map.of("fast",List.of(new BotLoadoutChoice.Outcome(true,0,100,10)),
                "slow",List.of(new BotLoadoutChoice.Outcome(true,150,800,10)));
        assertEquals("slow",BotLoadoutChoice.choose(List.of(fast,slow),"",null,null,false,history));
    }
    @Test void fragileBotValuesRecoveryAndDistantBotValuesScanner() {
        var heal=new BotLoadoutChoice.Option("heal",100,100,false,0,0,0,0,0,0,"recovery_potion");
        var scan=new BotLoadoutChoice.Option("scan",100,100,false,0,0,0,0,0,0,"infrared_scanner");
        assertEquals("heal",BotLoadoutChoice.choose(List.of(heal,scan),"",null,poor,true));
        assertEquals("scan",BotLoadoutChoice.choose(List.of(heal,scan),"",null,new BotLoadoutChoice.Outcome(true,0,500,30),true));
    }
    private final List<BotLoadoutChoice.Option> guns=List.of(
            new BotLoadoutChoice.Option("fast",100,150,false),new BotLoadoutChoice.Option("tank",200,80,false));
    private final BotLoadoutChoice.Outcome poor=new BotLoadoutChoice.Outcome(true,0,100,12);
    @Test void failedFightChoosesAlternativeButFixedSlotWins(){
        assertEquals("tank",BotLoadoutChoice.choose(guns,"","fast",poor,false));
        assertEquals("fast",BotLoadoutChoice.choose(guns,"fast","fast",poor,false));
        assertNull(BotLoadoutChoice.choose(guns,"none","fast",poor,true));
    }
    @Test void productiveOrNonCombatLifeKeepsWeapon(){
        assertEquals("fast",BotLoadoutChoice.choose(guns,"","fast",new BotLoadoutChoice.Outcome(true,75,100,12),false));
        assertEquals("fast",BotLoadoutChoice.choose(guns,"","fast",new BotLoadoutChoice.Outcome(false,0,50,12),false));
        assertEquals("fast",BotLoadoutChoice.choose(guns,"","fast",new BotLoadoutChoice.Outcome(true,20,800,12),false));
    }
    @Test void closeFightCanFavorMeleeAndFarFightFavorsRanged(){
        var options=List.of(new BotLoadoutChoice.Option("old",100,100,false),
                new BotLoadoutChoice.Option("knife",100,100,true),new BotLoadoutChoice.Option("pistol",100,100,false));
        assertEquals("knife",BotLoadoutChoice.choose(options,"","old",new BotLoadoutChoice.Outcome(true,0,100,2),true));
        assertEquals("pistol",BotLoadoutChoice.choose(options,"","old",poor,true));
        assertEquals("old",BotLoadoutChoice.choose(options.subList(0,2),"","old",poor,true));
    }
    @Test void emptyAndSingleChoiceAreSafe(){
        assertNull(BotLoadoutChoice.choose(List.of(),"",null,poor,true));
        assertEquals("fast",BotLoadoutChoice.choose(guns.subList(0,1),"","fast",poor,false));
        assertThrows(java.util.NoSuchElementException.class,()->BotLoadoutChoice.choose(guns,"missing",null,poor,false));
    }
}
