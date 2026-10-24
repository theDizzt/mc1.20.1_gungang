package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TeamPointStateTest {
    @Test void numberingAndCoordinatesAreStrictlyTeamLocal() {
        var s=new TeamPointState();UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        var roster=new LinkedHashMap<UUID,String>();roster.put(a,"ALPHA");roster.put(b,"BRAVO");roster.put(c,"ALPHA");s.roster(roster);
        s.mark(a,"overworld",1,2,3);s.mark(b,"overworld",99,2,3);s.mark(c,"overworld",4,2,3);
        var red=s.view("ALPHA",0);var blue=s.view("BRAVO",0);
        assertEquals(List.of(1,2),red.members().stream().map(TeamPointState.Member::number).toList());
        assertEquals(List.of(a,c),red.points().stream().map(TeamPointState.Point::owner).toList());
        assertEquals(1,blue.members().get(0).number());assertEquals(b,blue.points().get(0).owner());
        assertTrue(s.view("SPECTATOR",0).points().isEmpty());assertTrue(s.view("",0).members().isEmpty());
    }
    @Test void replacingPointKeepsNumberAndClearDoesNotRecycleSequence() {
        var s=new TeamPointState();UUID a=UUID.randomUUID();s.roster(Map.of(a,"ALPHA"));
        s.mark(a,"world",1,2,3);long old=s.view("ALPHA",0).points().get(0).sequence();
        s.mark(a,"world",4,5,6);var view=s.view("ALPHA",0);
        assertEquals(1,view.points().size());assertEquals(1,view.points().get(0).number());assertEquals(4,view.points().get(0).x());
        s.clear();assertTrue(s.view("ALPHA",0).points().isEmpty());assertEquals(1,s.view("ALPHA",0).members().size());
        s.mark(a,"world",7,8,9);assertTrue(s.view("ALPHA",0).points().get(0).sequence()>old);
    }
    @Test void teamChangesAndDisconnectsRemoveOldMarks() {
        var s=new TeamPointState();UUID a=UUID.randomUUID();s.roster(Map.of(a,"ALPHA"));s.mark(a,"world",1,2,3);
        s.roster(Map.of(a,"BRAVO"));assertTrue(s.view("ALPHA",0).members().isEmpty());assertTrue(s.view("BRAVO",0).points().isEmpty());
        s.mark(a,"world",1,2,3);s.roster(Map.of());assertTrue(s.view("BRAVO",0).points().isEmpty());
        assertFalse(s.mark(a,"world",1,2,3));
    }
    @Test void invalidCoordinatesAndUnknownTeamsCannotCreatePoints() {
        var s=new TeamPointState();UUID a=UUID.randomUUID();s.roster(Map.of(a,"ALPHA"));
        assertFalse(s.mark(a,"world",Double.NaN,0,0));assertFalse(s.mark(a,"world",0,Double.POSITIVE_INFINITY,0));
        s.roster(Map.of(a,"SPECTATOR"));assertFalse(s.mark(a,"world",0,0,0));
    }
}
