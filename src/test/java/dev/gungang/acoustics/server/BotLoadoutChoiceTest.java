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
    @Test void weaponsOtherBotsCarryScoreLower(){
        var a=new BotLoadoutChoice.Option("a",100,100,false);var b=new BotLoadoutChoice.Option("b",100,100,false);
        assertEquals("b",BotLoadoutChoice.choose(List.of(a,b),"",null,null,false,java.util.Map.of(),java.util.Map.of("a",1),null));
        assertEquals("a",BotLoadoutChoice.choose(List.of(a,b),"",null,null,false,java.util.Map.of(),java.util.Map.of("b",2),null));
    }
    @Test void weightedDrawSpreadsEqualBotsButSkipsFarWorseOptions(){
        var options=List.of(new BotLoadoutChoice.Option("a",100,100,false),new BotLoadoutChoice.Option("b",100,100,false),
                new BotLoadoutChoice.Option("c",100,100,false),new BotLoadoutChoice.Option("bad",0,0,false,30,40,0,0,0,0,""));
        var random=new java.util.Random(7);var seen=new java.util.HashSet<String>();
        for(int i=0;i<200;i++)seen.add(BotLoadoutChoice.choose(options,"",null,poor,false,java.util.Map.of(),java.util.Map.of(),random));
        assertEquals(java.util.Set.of("a","b","c"),seen);
        // A productive previous weapon is still kept, random or not.
        assertEquals("c",BotLoadoutChoice.choose(options,"","c",new BotLoadoutChoice.Outcome(true,75,100,12),false,java.util.Map.of(),java.util.Map.of("c",3),random));
    }
    @Test void firstSpawnIsUniformAmongLeastCarriedWeapons(){
        var minigun=new BotLoadoutChoice.Option("minigun",125,80,false,3,24,8,1200,200,6,"");
        var options=List.of(minigun,new BotLoadoutChoice.Option("pistol",100,110,false,0,16,6,300,12,2,""),
                new BotLoadoutChoice.Option("knife",100,120,true),new BotLoadoutChoice.Option("bow",100,100,false,10,40,10,40,1,1,""));
        var random=new java.util.Random(3);var counts=new java.util.HashMap<String,Integer>();
        for(int i=0;i<400;i++)counts.merge(BotLoadoutChoice.choose(options,"",null,null,false,java.util.Map.of(),java.util.Map.of(),random),1,Integer::sum);
        assertEquals(4,counts.size());
        counts.values().forEach(n->assertTrue(n>60,"uneven first picks: "+counts));
        // Weapons other bots already carry are left out while an uncarried one remains.
        for(int i=0;i<50;i++)assertEquals("bow",BotLoadoutChoice.choose(options,"",null,null,false,java.util.Map.of(),
                java.util.Map.of("minigun",1,"pistol",1,"knife",2),random));
        // Without a random source (tests, fixed policy) the first pick stays the deterministic best score.
        assertEquals("minigun",BotLoadoutChoice.choose(options,"",null,null,false,java.util.Map.of(),java.util.Map.of(),null));
    }
    @Test void emptyAndSingleChoiceAreSafe(){
        assertNull(BotLoadoutChoice.choose(List.of(),"",null,poor,true));
        assertEquals("fast",BotLoadoutChoice.choose(guns.subList(0,1),"","fast",poor,false));
        assertThrows(java.util.NoSuchElementException.class,()->BotLoadoutChoice.choose(guns,"missing",null,poor,false));
    }
}
