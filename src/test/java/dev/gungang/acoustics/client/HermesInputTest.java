package dev.gungang.acoustics.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HermesInputTest {
    private static final int W = 0, S = 1, A = 2, D = 3;
    private static boolean[] held(int... keys) { var held = new boolean[4]; for (int k : keys) held[k] = true; return held; }

    @Test void singleKeysDashStraight() {
        assertEquals(0, HermesInput.offset(W, held(W)));
        assertEquals(180, HermesInput.offset(S, held(S)));
        assertEquals(-90, HermesInput.offset(A, held(A)));
        assertEquals(90, HermesInput.offset(D, held(D)));
    }
    @Test void aHeldKeyOnTheOtherAxisMakesADiagonal() {
        assertEquals(-45, HermesInput.offset(W, held(W, A)));
        assertEquals(45, HermesInput.offset(W, held(W, D)));
        assertEquals(-135, HermesInput.offset(S, held(S, A)));
        assertEquals(135, HermesInput.offset(S, held(S, D)));
        assertEquals(45, HermesInput.offset(D, held(D, W)));
        assertEquals(-135, HermesInput.offset(A, held(A, S)));
    }
    @Test void theTappedKeyWinsItsAxisAndOppositesCancelOnTheOther() {
        assertEquals(0, HermesInput.offset(W, held(W, S)));
        assertEquals(0, HermesInput.offset(W, held(W, A, D)));
        assertEquals(90, HermesInput.offset(D, held(D, A)));
    }
    @Test void everyOffsetIsOneTheServerAccepts() {
        for (int tapped = 0; tapped < 4; tapped++) for (int mask = 0; mask < 16; mask++) {
            var held = new boolean[4];
            for (int k = 0; k < 4; k++) held[k] = (mask >> k & 1) == 1;
            int offset = HermesInput.offset(tapped, held);
            assertTrue(offset % 45 == 0 && offset >= -135 && offset <= 180, "offset " + offset);
        }
    }
}
