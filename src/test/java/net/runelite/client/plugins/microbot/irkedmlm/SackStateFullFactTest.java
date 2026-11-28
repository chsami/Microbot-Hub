package net.runelite.client.plugins.microbot.irkedmlm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** A full sack learnt from the game outranks a varbit still catching up, but not one that is emptying. */
class SackStateFullFactTest {

    @Test
    void aRefusalStaysFullWhileTheVarbitLags() {
        SackState s = new SackState();
        s.observe(90, 0, 1L);
        s.onHopperRefusal(2L);
        s.observe(95, 0, 3L);
        assertTrue(s.definitelyFull());
        assertEquals(SackState.SACK_SIZE, s.countOrUnknown());
    }

    @Test
    void theFullFactClearsWhenTheSackStartsEmptying() {
        SackState s = new SackState();
        s.observe(108, 0, 1L);
        s.onSackIsFullChat(2L);
        s.observe(108, 0, 3L);
        s.observe(80, 0, 4L);
        assertFalse(s.definitelyFull());
        assertEquals(80, s.countOrUnknown());
    }

    @Test
    void invalidatingForgetsTheFullFact() {
        SackState s = new SackState();
        s.observe(50, 0, 1L);
        s.onHopperRefusal(2L);
        s.invalidate("test");
        s.observe(50, 0, 3L);
        assertEquals(50, s.countOrUnknown());
    }
}
