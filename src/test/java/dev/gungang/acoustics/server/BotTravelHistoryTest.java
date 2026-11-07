package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotTravelHistoryTest {
    @Test void detectsBackAndForthWithoutMistakingForwardTravel(){
        var loop=new BotTravelHistory();var forward=new BotTravelHistory();boolean detected=false;
        for(int i=0;i<30;i++){
            detected|=loop.sample(i%4<2?0:3,64,0,i*10);
            assertFalse(forward.sample(i,64,0,i*10));
        }
        assertTrue(detected);
    }
    @Test void pausesAndResetsDoNotCountAsLoops(){
        var history=new BotTravelHistory();
        for(int i=0;i<30;i++)assertFalse(history.sample(0,64,0,i*10));
        history.reset();
        for(int i=0;i<12;i++)assertFalse(history.sample(i%2*3,64,0,i*10));
        assertFalse(history.sample(0,64,0,200));
    }
}
