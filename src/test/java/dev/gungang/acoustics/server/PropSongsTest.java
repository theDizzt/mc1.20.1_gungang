package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropSongsTest {
    @Test void noteNamesAreSemitonesFromTheHarpsFSharp4(){
        assertArrayEquals(new int[]{0,6,-6,3,18,0,5,-24,24},PropAbilities.notes("F#4 C5 C4 A4 C6 Gb4 Cb5 F#2 F#6"));
        assertThrows(IllegalArgumentException.class,()->PropAbilities.notes("H4"));
    }
    @Test void everyPieceFitsTheSixOctaveNote(){
        assertEquals(7,PropAbilities.SONGS.size());
        for(var song:PropAbilities.SONGS){
            assertTrue(song.notes().length>=16,song.name());
            for(int n:song.notes())assertTrue(n>=-36&&n<=36,song.name()+" "+n);
        }
    }
}
