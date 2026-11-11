package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroSlayerHoldTest {

    @BeforeEach
    @AfterEach
    void reset() {
        DroSlayerHold.clear();
        DroSlayerPlugin.setState(DroSlayerState.IDLE);
    }

    @Test
    void anIdleHoldIsShownUntilTheStateChanges() {
        String line = DroSlayerHold.raise(DroSlayerState.IDLE, "No Wilderness escape item in your bank", "Bank a seed pod", 0);
        assertNotNull(line);
        assertTrue(line.contains("No Wilderness escape item in your bank") && line.contains("Bank a seed pod"));
        assertEquals("No Wilderness escape item in your bank", DroSlayerHold.current(DroSlayerState.IDLE, 0));
        assertEquals("No Wilderness escape item in your bank", DroSlayerHold.current(DroSlayerState.IDLE, 3_600_000),
                "an idle script does nothing else, so the reason stays up");
        assertEquals("Bank a seed pod", DroSlayerHold.currentAction(DroSlayerState.IDLE, 3_600_000));

        DroSlayerPlugin.setState(DroSlayerState.BANKING);
        assertNull(DroSlayerHold.current(DroSlayerState.BANKING, 1));
        DroSlayerPlugin.setState(DroSlayerState.IDLE);
        assertNull(DroSlayerHold.current(DroSlayerState.IDLE, 2), "a later idle for another reason does not show the old hold");
    }

    @Test
    void theChatLineIsGivenOncePerReason() {
        assertNotNull(DroSlayerHold.raise(DroSlayerState.DETECTING_TASK, "Auto Travel is off", "Walk to Fremennik Slayer Dungeon", 0));
        assertNull(DroSlayerHold.raise(DroSlayerState.DETECTING_TASK, "Auto Travel is off", "Walk to Fremennik Slayer Dungeon", 600));
        assertNotNull(DroSlayerHold.raise(DroSlayerState.DETECTING_TASK, "Auto Travel is off", "Walk to Stronghold Slayer Cave", 1_200),
                "a different thing to do is a new message");
    }

    @Test
    void aHoldInAWorkingStateEndsWhenItIsNotRaisedAgain() {
        DroSlayerPlugin.setState(DroSlayerState.BANKING);
        DroSlayerHold.raise(DroSlayerState.BANKING, "Bank supplies missing: cannonballs", "Add them to your bank", 0);
        // Setting the same state again, as the banking retry does, keeps the hold.
        DroSlayerPlugin.setState(DroSlayerState.BANKING);
        assertEquals("Bank supplies missing: cannonballs", DroSlayerHold.current(DroSlayerState.BANKING, 10_000));
        assertNull(DroSlayerHold.current(DroSlayerState.BANKING, DroSlayerHold.REFRESH_MILLIS + 1),
                "banking went on without raising it again");
        DroSlayerHold.raise(DroSlayerState.BANKING, "Bank supplies missing: cannonballs", "Add them to your bank", 20_000);
        assertEquals("Bank supplies missing: cannonballs", DroSlayerHold.current(DroSlayerState.BANKING, 25_000));
    }

    @Test
    void nothingIsShownWhileTheScriptIsNotWaiting() {
        assertNull(DroSlayerHold.current(DroSlayerState.IDLE, 0));
        assertNull(DroSlayerHold.currentAction(DroSlayerState.FIGHTING, 0));
    }
}
