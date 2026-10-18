package dev.gungang.acoustics.server;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropHuntGameTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),hunter=UUID.randomUUID();
    private PropHuntGame game(){return new PropHuntGame(IntStream.range(0,96).mapToObj(Integer::toString).toList(),List.of(a,b),new Random(1));}
    @Test void deckNeverRepeatsAcrossPlayersOrChanges(){var g=game();var used=new HashSet<String>();for(int i=0;i<96;i++)assertTrue(used.add(g.draw()));assertThrows(IllegalStateException.class,g::draw);}
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
}
