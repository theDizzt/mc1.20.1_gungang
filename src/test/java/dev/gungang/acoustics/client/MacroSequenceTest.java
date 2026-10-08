package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.gungang.acoustics.client.MacroSequence.Step.*;

class MacroSequenceTest {
    @Test void waitsForHandChangeThenRestoresAfterOneCompletedClick() {
        MacroSequence sequence = new MacroSequence();
        assertEquals(WAIT, sequence.next(false));
        assertEquals(WAIT, sequence.next(false));
        assertEquals(ACT, sequence.next(false));
        sequence.actionDone();
        assertEquals(RESTORE, sequence.next(false));
    }
    @Test void holdsWithoutRepeatingUseUntilKeyReleaseOrItemFinishes() {
        MacroSequence sequence = new MacroSequence();
        sequence.next(false); sequence.next(false); sequence.next(false); sequence.actionDone();
        for (int i = 0; i < 32; i++) assertEquals(HOLD, sequence.next(true));
        assertEquals(RESTORE, sequence.next(false));
    }
    @Test void blockedGunCannotLeaveHandsSwappedForever() {
        MacroSequence sequence = new MacroSequence();
        for (int i = 1; i <= 100; i++) assertEquals(i <= 2 ? WAIT : ACT, sequence.next(false));
        assertEquals(RESTORE, sequence.next(false));
    }
    @Test void continuousUseHasASafetyTimeout() {
        MacroSequence sequence = new MacroSequence();
        sequence.next(false); sequence.next(false); sequence.next(false); sequence.actionDone();
        for (int i = 4; i <= 1200; i++) assertEquals(HOLD, sequence.next(true));
        assertEquals(RESTORE, sequence.next(true));
    }
}
