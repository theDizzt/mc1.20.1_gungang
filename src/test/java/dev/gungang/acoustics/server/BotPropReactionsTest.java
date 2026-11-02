package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotPropReactionsTest {
    @Test void friendlyRepliesMatchTheGesture(){
        assertEquals(1,BotPropReactions.response(1,true,false));
        assertEquals(2,BotPropReactions.response(2,true,false));
        assertEquals(6,BotPropReactions.response(6,true,false));
        assertEquals(5,BotPropReactions.response(7,true,false));
    }
    @Test void threatOverridesFriendlyGestures(){
        for(int slot=0;slot<=7;slot++){
            assertEquals(3,BotPropReactions.response(slot,false,false));
            assertEquals(3,BotPropReactions.response(slot,true,true));
        }
    }
}
