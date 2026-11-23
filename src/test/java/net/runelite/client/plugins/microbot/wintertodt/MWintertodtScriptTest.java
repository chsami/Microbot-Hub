package net.runelite.client.plugins.microbot.wintertodt;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.wintertodt.enums.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MWintertodtScriptTest {

    private final State originalState = MWintertodtScript.state;

    @AfterEach
    void restoreState() {
        MWintertodtScript.state = originalState;
    }

    private State runAlmostDeadLoop(State startState, boolean hasKindling, boolean hasRoot, boolean inventoryFull) {
        MWintertodtScript script = new MWintertodtScript();
        script.wintertodtHp = 10;
        MWintertodtScript.state = startState;
        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class)) {
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.BRUMA_KINDLING)).thenReturn(hasKindling);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.BRUMA_ROOT)).thenReturn(hasRoot);
            inventory.when(Rs2Inventory::isFull).thenReturn(inventoryFull);
            script.handleMainLoop();
        }
        return MWintertodtScript.state;
    }

    @Test
    void almostDeadWithNothingToBurnChopsRoots() {
        assertEquals(State.CHOP_ROOTS, runAlmostDeadLoop(State.BURN_LOGS, false, false, false));
    }

    @Test
    void almostDeadWithRootsFeedsBrazier() {
        assertEquals(State.BURN_LOGS, runAlmostDeadLoop(State.CHOP_ROOTS, false, true, false));
    }

    @Test
    void almostDeadWithKindlingFeedsBrazierInsteadOfFletching() {
        assertEquals(State.BURN_LOGS, runAlmostDeadLoop(State.FLETCH_LOGS, true, true, false));
    }
}
