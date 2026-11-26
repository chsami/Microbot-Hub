package net.runelite.client.plugins.microbot.irkedmlm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the repair gate: pay-dirt only freezes when BOTH wheels are broken, so a repair is only
 * needed when no wheel is running. Previously the bot repaired on any broken strut, which meant it
 * repaired while one wheel was still running (and while another player was fixing the other wheel).
 */
public class WaterwheelRepairGateTest {

    @Test
    public void repairsOnlyWhenNoWheelRunning() {
        // Both wheels broken (no running wheel) + a strut to click -> repair.
        assertTrue(IrkedMLMScript.waterwheelNeedsRepair(0, 2));
        assertTrue(IrkedMLMScript.waterwheelNeedsRepair(0, 1));
    }

    @Test
    public void skipsRepairWhileAWheelStillRuns() {
        // One wheel running: water flows, hopper processes -> no repair even though a strut is broken.
        assertFalse(IrkedMLMScript.waterwheelNeedsRepair(1, 1));
        assertFalse(IrkedMLMScript.waterwheelNeedsRepair(2, 0));
    }

    @Test
    public void aLoadTheFrozenHopperRefusedIsNotOfferedAgainBeforeTheRepair() {
        assertTrue(IrkedMLMScript.depositBeforeRepair(26, false, false));
        assertFalse(IrkedMLMScript.depositBeforeRepair(26, false, true), "hopper already refused it");
        assertFalse(IrkedMLMScript.depositBeforeRepair(26, true, false), "sack full");
        assertFalse(IrkedMLMScript.depositBeforeRepair(0, false, false), "nothing to deposit");
    }

    @Test
    public void nothingBrokenNeverRepairs() {
        assertFalse(IrkedMLMScript.waterwheelNeedsRepair(0, 0));
    }
}
