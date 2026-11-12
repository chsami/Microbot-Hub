package net.runelite.client.plugins.microbot.karambwans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FairyRingDialTest {
    private static final String[] WANTED = {"Last-destination (DKP)", "Ring-last-destination (DKP)"};
    private static final String[] CONFIGURE = {"Configure", "Ring-configure"};

    @Test
    void lastDestinationIsUsedOnlyWhenItIsTheKarambwanCode() {
        String[] ring = {null, "Configure", "Last-destination (DKP)", null, null};
        String[] otherCode = {null, "Configure", "Last-destination (AKR)", null, null};

        assertEquals("Last-destination (DKP)", FairyRingDial.findAction(ring, WANTED));
        assertNull(FairyRingDial.findAction(otherCode, WANTED));
        assertEquals("Configure", FairyRingDial.findAction(otherCode, CONFIGURE));
    }

    @Test
    void spiritualFairyTreeActionsAreRecognised() {
        String[] tree = {"Tree", "Ring-configure", "<col=ffffff>Ring-last-destination (DKP)</col>", null, null};

        assertEquals("Ring-last-destination (DKP)", FairyRingDial.findAction(tree, WANTED));
        assertEquals("Ring-configure", FairyRingDial.findAction(tree, CONFIGURE));
    }

    @Test
    void missingActionsGiveNoMatch() {
        assertNull(FairyRingDial.findAction(null, WANTED));
        assertNull(FairyRingDial.findAction(new String[]{null, "Zanaris", null}, CONFIGURE));
    }

    @Test
    void lettersMapToDialRotations() {
        assertEquals(1536, FairyRingDial.rotationFor('D'));
        assertEquals(1024, FairyRingDial.rotationFor('K'));
        assertEquals(0, FairyRingDial.rotationFor('P'));
        assertEquals(512, FairyRingDial.rotationFor('j'));
        assertEquals(-1, FairyRingDial.rotationFor('X'));
    }

    @Test
    void dialTurnsTakeTheShortestDirection() {
        assertEquals(0, FairyRingDial.clockwiseTurns(1536, 1536));
        assertEquals(1, FairyRingDial.clockwiseTurns(512, 0));
        assertEquals(-1, FairyRingDial.clockwiseTurns(0, 512));
        assertEquals(-1, FairyRingDial.clockwiseTurns(1536, 0));
        assertEquals(1, FairyRingDial.clockwiseTurns(0, 1536));
        assertEquals(2, FairyRingDial.clockwiseTurns(0, 1024));
    }
}
