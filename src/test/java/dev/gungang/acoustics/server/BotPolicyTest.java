package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotPolicyTest {
    private static BotPolicy policy(String weights){return BotPolicy.parse("{\"schema\":1,\"version\":\"test\",\"modes\":{\"CONTROL\":"+weights+"}}");}
    @Test void wrongSchemaUnsafeActionAndNonfiniteWeightsAreRejected(){
        assertThrows(RuntimeException.class,()->BotPolicy.parse("{\"schema\":2}"));
        assertThrows(RuntimeException.class,()->policy("{\"EVADE\":{\"bias\":1}}"));
        assertThrows(RuntimeException.class,()->policy("{\"PUSH\":{\"bias\":\"NaN\"}}"));
        assertThrows(RuntimeException.class,()->policy("{\"PUSH\":{\"enemyThroughWall\":1}}"));
    }
    @Test void absentModeFallsBackAndOffsetsStayBounded(){
        var p=policy("{\"PUSH\":{\"bias\":100}}");
        assertTrue(p.offsets("BOMB",Map.of("bias",1d)).isEmpty());
        assertEquals(12f,p.offsets("CONTROL",Map.of("bias",1d)).get(BotTactics.Action.PUSH).floatValue());
    }
    @Test void residualCannotOverrideHazardLowAmmoOrLowHealth(){
        var extreme=Map.of(BotTactics.Action.PUSH,10000f,BotTactics.Action.COVER,-10000f,BotTactics.Action.RETREAT,-10000f);
        for(var s:new BotTactics.Situation[]{
                new BotTactics.Situation(1,1,false,1,0,1,false,12,false,false,false,0,false,true),
                new BotTactics.Situation(1,.1f,false,1,0,1,false,12,false,false,false,0,false),
                new BotTactics.Situation(.2f,1,false,1,0,1,false,40,true,false,false,0,false)}) {
            assertEquals(new BotTactics().choose(s,100),new BotTactics().choose(s,100,false,false,false,0,extreme));
        }
    }
    @Test void residualDoesNotCreateObjectiveWithoutMission(){
        var s=new BotTactics.Situation(1,1,false,0,0,1,false,Double.POSITIVE_INFINITY,false,false,false,Long.MAX_VALUE,false);
        assertEquals(BotTactics.Action.PATROL,new BotTactics().choose(s,100,false,false,false,0,Map.of(BotTactics.Action.OBJECTIVE,10000f)));
    }
}
