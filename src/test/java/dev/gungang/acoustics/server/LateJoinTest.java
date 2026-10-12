package dev.gungang.acoustics.server;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LateJoinTest {
    @Test void spectatorBecomesScoredParticipantAndRejoinKeepsStats() {
        var ledger=new MatchLedger();
        var alpha=UUID.randomUUID(); var bravo=UUID.randomUUID();
        ledger.add(alpha,"alpha","ALPHA"); ledger.add(bravo,"bravo","SPECTATOR");
        assertFalse(ledger.enemies(alpha,bravo));
        ledger.add(bravo,"bravo","BRAVO");
        assertTrue(ledger.enemies(alpha,bravo));
        ledger.death(bravo,alpha,100);
        ledger.add(bravo,"bravo","BRAVO");
        assertEquals(1,ledger.rows().stream().filter(r->r.id().equals(bravo.toString())).findFirst().orElseThrow().deaths());
    }
}
