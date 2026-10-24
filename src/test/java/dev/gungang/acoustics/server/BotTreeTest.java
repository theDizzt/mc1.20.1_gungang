package dev.gungang.acoustics.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.gungang.acoustics.server.BotTree.Status;
import org.junit.jupiter.api.Test;

class BotTreeTest {
    @Test void sequenceWaitsAdvancesAndAborts() {
        int[] waits={2};
        var tree=BotTree.sequence(BotTree.leaf(()->Status.SUCCESS),BotTree.leaf(()->waits[0]-->0?Status.RUNNING:Status.SUCCESS));
        assertEquals(Status.RUNNING,tree.tick());
        assertEquals(Status.RUNNING,tree.tick());
        assertEquals(Status.SUCCESS,tree.tick());
        var broken=BotTree.sequence(BotTree.leaf(()->Status.SUCCESS),BotTree.leaf(()->Status.FAILURE),BotTree.leaf(()->{throw new AssertionError("never reached");}));
        assertEquals(Status.FAILURE,broken.tick());
    }
    @Test void selectorFallsBackToTheNextOption() {
        var tree=BotTree.selector(BotTree.leaf(()->Status.FAILURE),BotTree.leaf(()->Status.SUCCESS));
        assertEquals(Status.SUCCESS,tree.tick());
        assertEquals(Status.FAILURE,BotTree.selector(BotTree.leaf(()->Status.FAILURE)).tick());
    }
}
