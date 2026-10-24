package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;

import dev.gungang.acoustics.server.BotTactics.Action;
import dev.gungang.acoustics.server.BotTactics.Situation;
import org.junit.jupiter.api.Test;

class BotTacticsTest {
    @Test void distantEnemyCanTriggerRetreatBeforeTakingDamage() {
        var healthy=new Situation(1,1,false,1,0,1,false,40,false,false,false,Long.MAX_VALUE,false);
        assertEquals(Action.ENGAGE,new BotTactics().choose(healthy,100));
        for(var bad:new Situation[]{
                new Situation(.3f,1,false,1,0,1,false,40,false,false,false,Long.MAX_VALUE,false),
                new Situation(1,.1f,false,1,0,1,false,40,false,false,false,Long.MAX_VALUE,false),
                new Situation(1,1,false,3,0,1,false,40,false,false,false,Long.MAX_VALUE,false)})
            assertEquals(Action.RETREAT,new BotTactics().choose(bad,100));
    }
    @Test void teamPointGuidesIdleBotsButNeverOverridesCombatOrHazards() {
        var idle=new Situation(1,1,false,0,0,1,false,Double.POSITIVE_INFINITY,false,false,false,Long.MAX_VALUE,false);
        assertEquals(Action.RALLY,new BotTactics().choose(idle,100,false,false,true));
        var enemy=new Situation(1,1,false,1,0,1,false,20,false,false,false,Long.MAX_VALUE,false);
        assertEquals(Action.ENGAGE,new BotTactics().choose(enemy,100,false,false,true));
        var danger=new Situation(1,1,false,0,0,1,false,Double.POSITIVE_INFINITY,false,false,false,Long.MAX_VALUE,false,true);
        assertEquals(Action.EVADE,new BotTactics().choose(danger,100,false,false,true));
    }
    @Test void hazardsInterruptCommitmentsEvenWhenWoundedReloadingOrFailed() {
        for(float hp:new float[]{.1f,.3f,1f})for(boolean reload:new boolean[]{false,true})
        for(boolean cover:new boolean[]{false,true})for(boolean shield:new boolean[]{false,true}) {
            var t=new BotTactics();
            var calm=new Situation(hp,0,reload,4,0,0,false,12,true,false,true,0,false,false);
            t.choose(calm,100);
            t.failed(Action.EVADE,100);
            var danger=new Situation(hp,0,reload,4,0,0,false,12,true,false,true,0,false,true);
            assertEquals(Action.EVADE,t.choose(danger,101,cover,shield));
            t.completed(Action.EVADE,102,60);
            assertEquals(Action.EVADE,t.choose(danger,103,cover,shield));
            assertNotEquals(Action.EVADE,t.choose(calm,104));
        }
    }
    @Test void nearlyEmptyMagazineSeeksCoverBeforeItRunsDry() {
        var s=new Situation(1,.1f,false,1,0,1,false,15,false,false,false,0,false);
        assertEquals(Action.COVER,new BotTactics().choose(s,0));
    }
    private static final long NONE=Long.MAX_VALUE;
    private static float score(Action a,Situation s){return BotTactics.score(a,s);}

    @Test void hurtAndOutnumberedPrefersCoverThenAlliesThenRetreat() {
        // 30% health, full ammo, two enemies shooting at us, allies alive but far away.
        var s=new Situation(.3f,1,false,2,0,2,false,15,true,false,false,NONE,true);
        float cover=score(Action.COVER,s),regroup=score(Action.REGROUP,s),retreat=score(Action.RETREAT,s),
                engage=score(Action.ENGAGE,s),push=score(Action.PUSH,s);
        assertTrue(cover>regroup&&regroup>retreat&&retreat>engage&&engage>push,cover+" "+regroup+" "+retreat+" "+engage+" "+push);
        assertEquals(Action.COVER,new BotTactics().choose(s,0));
    }
    @Test void healthyDuelEngagesAndMeleeCharges() {
        var duel=new Situation(1,1,false,1,0,1,false,12,false,false,false,NONE,false);
        assertEquals(Action.ENGAGE,new BotTactics().choose(duel,0));
        var knife=new Situation(1,1,false,1,0,1,false,6,false,true,false,NONE,false);
        assertEquals(Action.PUSH,new BotTactics().choose(knife,0));
    }
    @Test void reloadingInSightTakesCover() {
        var s=new Situation(.9f,0,true,1,0,1,false,15,false,false,false,NONE,false);
        assertEquals(Action.COVER,new BotTactics().choose(s,0));
    }
    @Test void quietTimeInvestigatesFreshCluesThenPatrolsOrRejoins() {
        var fresh=new Situation(1,1,false,0,0,1,false,Double.POSITIVE_INFINITY,false,false,false,20,false);
        assertEquals(Action.INVESTIGATE,new BotTactics().choose(fresh,0));
        var stale=new Situation(1,1,false,0,0,1,false,Double.POSITIVE_INFINITY,false,false,false,NONE,false);
        assertEquals(Action.PATROL,new BotTactics().choose(stale,0));
        var alone=new Situation(1,1,false,0,0,1,false,Double.POSITIVE_INFINITY,false,false,false,NONE,true);
        assertEquals(Action.REGROUP,new BotTactics().choose(alone,0));
    }
    @Test void allyUnderFireGetsSupportAndWoundedBotHeals() {
        var help=new Situation(1,1,false,0,1,1,true,Double.POSITIVE_INFINITY,false,false,false,30,false);
        assertEquals(Action.SUPPORT,new BotTactics().choose(help,0));
        var wounded=new Situation(.3f,1,false,0,0,1,false,Double.POSITIVE_INFINITY,false,false,true,NONE,false);
        assertEquals(Action.HEAL,new BotTactics().choose(wounded,0));
    }
    @Test void strikeZoneOrFireIsLeftBeforeAnythingElse() {
        var burning=new Situation(1,1,false,1,0,1,false,8,true,false,false,0,false,true);
        assertEquals(Action.EVADE,new BotTactics().choose(burning,0));
        assertEquals(0,score(Action.EVADE,new Situation(1,1,false,1,0,1,false,8,true,false,false,0,false)));
    }
    @Test void lastSurvivorPlaysSafer() {
        var team=new Situation(.45f,1,false,1,0,1,false,15,true,false,false,NONE,false);
        var alone=new Situation(.45f,1,false,1,0,0,false,15,true,false,false,NONE,false);
        assertTrue(score(Action.COVER,alone)>score(Action.COVER,team));
        assertTrue(score(Action.PUSH,alone)<score(Action.PUSH,team));
    }
    @Test void hysteresisHoldsButUrgentChangesSwitchAndFailuresAreAvoided() {
        var tactics=new BotTactics();
        var duel=new Situation(1,1,false,1,0,1,false,12,false,false,false,NONE,false);
        assertEquals(Action.ENGAGE,tactics.choose(duel,0));
        // Slightly hurt a moment later: still fighting, no flicker.
        assertEquals(Action.ENGAGE,tactics.choose(new Situation(.8f,1,false,1,0,1,false,12,true,false,false,NONE,false),2));
        // Out of ammo in sight: urgent, switches inside the commitment window.
        assertEquals(Action.COVER,tactics.choose(new Situation(.8f,0,true,1,0,1,false,12,true,false,false,NONE,false),3));
        tactics.failed(Action.COVER,4);
        assertNotEquals(Action.COVER,tactics.choose(new Situation(.8f,0,true,1,0,1,false,12,true,false,false,NONE,false),5));
    }
}
