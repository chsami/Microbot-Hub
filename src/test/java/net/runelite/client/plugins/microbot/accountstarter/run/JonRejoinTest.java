package net.runelite.client.plugins.microbot.accountstarter.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JonRejoinTest {
    @Test
    void rejoinDoneOnceVarbitReadsParticipating() {
        assertEquals(Outcome.done(JonActivity.REJOINED), JonActivity.confirmRejoin(true, 1000, 1000));
    }

    @Test
    void rejoinWaitsForVarbitThenStops() {
        assertEquals(Outcome.Kind.CONTINUE, JonActivity.confirmRejoin(false, 1000, 1000 + JonActivity.REJOIN_CONFIRM_MS - 1).getKind());
        assertEquals(Outcome.failed(JonActivity.NOT_REJOINED), JonActivity.confirmRejoin(false, 1000, 1000 + JonActivity.REJOIN_CONFIRM_MS));
    }
}
