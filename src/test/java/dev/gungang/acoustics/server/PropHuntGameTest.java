package dev.gungang.acoustics.server;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropHuntGameTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),hunter=UUID.randomUUID();
    private PropHuntGame game(){return new PropHuntGame(IntStream.range(0,96).mapToObj(Integer::toString).toList(),List.of(a,b),new Random(1));}
    @Test void deckNeverRepeatsAcrossPlayersOrChanges(){var g=game();var used=new HashSet<String>();for(int i=0;i<96;i++)assertTrue(used.add(g.draw()));assertThrows(IllegalStateException.class,g::draw);}
    @Test void onlyThePickedOfferLeavesTheDeck(){
        var g=game();var offer=g.offer(a,3,new Random(2));assertEquals(3,offer.size());
        // Another hider never sees blocks on someone else's offer.
        var other=g.offer(b,3,new Random(3));for(String t:other)assertFalse(offer.contains(t));
        assertEquals(offer.get(1),g.take(a,1,new Random()));assertTrue(g.offers(a).isEmpty());
        // The two blocks not picked come back into play; the picked one never does.
        var seen=new HashSet<String>();g.release(b);for(int i=0;i<95;i++)seen.add(g.draw());
        assertTrue(seen.contains(offer.get(0)));assertTrue(seen.contains(offer.get(2)));assertFalse(seen.contains(offer.get(1)));
        assertThrows(IllegalStateException.class,g::draw);
    }
    @Test void unpickedOfferTakesARandomOfferedBlock(){var g=game();var offer=g.offer(a,3,new Random(4));assertTrue(offer.contains(g.take(a,-1,new Random(5))));}
    @Test void botsDrawAroundOpenOffers(){var g=game();var offer=g.offer(a,3,new Random(6));for(int i=0;i<93;i++)assertFalse(offer.contains(g.draw()));assertThrows(IllegalStateException.class,g::draw);}
    @Test void duplicateKindsDoNotIncreaseCapacity(){assertThrows(IllegalArgumentException.class,()->new PropHuntGame(List.of("iron","iron","gold"),List.of(a),new Random()));}
    @Test void rejectsOversizedTeamBeforeStarting(){assertThrows(IllegalArgumentException.class,()->new PropHuntGame(List.of("a","b","c","d","e"),List.of(a,b),new Random()));}
    @Test void oneMinuteHideTenMinuteHuntAndNoRerollInTheFinalMinute(){
        var g=game();int rerolls=0;for(int i=0;i<PropHuntGame.HIDE;i++){assertFalse(g.tick());assertEquals(PropHuntGame.HUNT,g.huntRemaining());}
        assertEquals(PropHuntGame.Phase.HUNTING,g.phase());assertEquals(600*20,g.remaining());assertEquals(0,g.hidden(a));
        for(int i=0;i<PropHuntGame.HUNT;i++){
            if(g.tick())rerolls++;
            if(i+1==PropHuntGame.HUNT-PropHuntGame.FINAL){assertTrue(g.finalMinute());assertEquals(0,g.nextChange());}
            if(i+1<PropHuntGame.HUNT-PropHuntGame.FINAL)assertFalse(g.finalMinute());
        }
        assertEquals(2,rerolls);assertEquals("ALPHA",g.winner());assertEquals(600,g.hidden(a));assertFalse(g.finalMinute());
    }
    @Test void suicideAndFoundPlayersCountOnlyOnce(){var g=game();for(int i=0;i<125;i++)g.tick();assertTrue(g.eliminate(a,hunter));assertFalse(g.eliminate(a,hunter));assertEquals(1,g.found(hunter));assertEquals(0,g.hidden(a));g.eliminate(b,null);assertEquals("BRAVO",g.winner());assertEquals(1,g.found(hunter));}
    @Test void reviewLastsThirtySecondsAndFreezesStatistics(){var g=game();g.eliminate(a,hunter);g.eliminate(b,hunter);for(int i=0;i<599;i++){assertEquals(PropHuntGame.Phase.REVIEW,g.phase());g.tick();}assertEquals(1,g.remaining());g.tick();assertEquals(PropHuntGame.Phase.FINISHED,g.phase());assertEquals(0,g.hidden(a));}
    @Test void jumpChargeIsCapped(){assertEquals(.42,PropHuntGame.jumpVelocity(-5),1e-9);assertEquals(3*PropHuntGame.jumpHeight(.42),PropHuntGame.jumpHeight(PropHuntGame.jumpVelocity(20)),1e-9);assertEquals(PropHuntGame.jumpVelocity(20),PropHuntGame.jumpVelocity(Integer.MAX_VALUE),1e-9);}
    @Test void sixteenMinuteMapChangesFourTimesAndKeepsTheFinalMinute(){
        int hunt=16*60*20;var g=new PropHuntGame(IntStream.range(0,96).mapToObj(Integer::toString).toList(),List.of(a,b),new Random(1),hunt);
        int changes=0;for(int i=0;i<PropHuntGame.HIDE+hunt;i++){if(g.tick())changes++;}
        assertEquals(4,changes);assertEquals("ALPHA",g.winner());
        assertEquals(5,PropHuntGame.draws(hunt));assertEquals(3,PropHuntGame.draws(PropHuntGame.HUNT));
    }
    @Test void customHideTimeAndEvenlySpacedChanges(){
        // 5 minutes, 4 changes: every (300-60)/4 = 60 s after a 20 s hiding time; the last one as the final minute starts.
        int hunt=300*20,hide=20*20;var g=new PropHuntGame(IntStream.range(0,96).mapToObj(Integer::toString).toList(),List.of(a,b),new Random(1),hunt,hide,4);
        assertEquals(hide+60*20,g.nextChange());
        var at=new ArrayList<Integer>();for(int i=1;i<=hide+hunt;i++){if(g.tick())at.add(i);if(i==hide)assertEquals(PropHuntGame.Phase.HUNTING,g.phase());}
        assertEquals(List.of(hide+1200,hide+2400,hide+3600,hide+4800),at);assertEquals("ALPHA",g.winner());
        var none=new PropHuntGame(IntStream.range(0,96).mapToObj(Integer::toString).toList(),List.of(a),new Random(1),hunt,hide,0);
        for(int i=0;i<hide+hunt;i++)assertFalse(none.tick());assertEquals(0,none.changeInterval());
        // A hider needs one block per change plus the first: 1 + 4 for two hiders is 10 blocks.
        assertThrows(IllegalArgumentException.class,()->new PropHuntGame(IntStream.range(0,9).mapToObj(Integer::toString).toList(),List.of(a,b),new Random(),hunt,hide,4));
    }
    @Test void firstComeListGoesToWhoeverClaimsFirst(){
        var g=game();var list=g.openShared(2,new Random(7));assertEquals(2,list.size());
        assertEquals(list,g.offers(a));assertEquals(list,g.offers(b));
        assertTrue(g.claim(a,1));assertFalse(g.claim(b,1));assertEquals(List.of(1),g.takenByOthers(b));assertEquals(List.of(),g.takenByOthers(a));
        // Nobody else draws an entry of the open list.
        for(int i=0;i<94;i++)assertFalse(list.contains(g.draw()));
        assertEquals(list.get(1),g.take(a,-1,new Random()));assertTrue(g.offers(a).isEmpty());
        // Time ran out for b: the one block left on the list.
        assertEquals(list.get(0),g.take(b,-1,new Random()));assertTrue(g.offers(b).isEmpty());
        g.closeShared();assertFalse(g.sharing());assertThrows(IllegalStateException.class,g::draw);
    }
}
