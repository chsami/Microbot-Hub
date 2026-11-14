package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MagicAxesDoorActionTest {
    @Test public void livePickLockSlotIsUsedAndOrdinaryActionsAreRejected() {
        NewMenuEntry entry = new NewMenuEntry(1, 2, MenuAction.GAME_OBJECT_FIRST_OPTION.getId(),
            12345, -1, "Open", "Door", null, 7);
        NewMenuEntry result = MagicAxesDoorAction.pickLock(entry, new String[]{"Open", null, "Pick-lock"});
        assertEquals(MenuAction.GAME_OBJECT_THIRD_OPTION, result.getType());
        assertEquals("Pick-lock", result.getOption());
        assertEquals(7, result.getWorldViewId());
        assertEquals("Open", entry.getOption());
        assertNull(MagicAxesDoorAction.pickLock(entry, new String[]{"Open"}));
    }

    @Test public void onlyHutDoorsAndEligibleAccountsAreHandled() {
        assertTrue(MagicAxesDoorAction.isHutDoor(new WorldPoint(3191, 3960, 0)));
        assertFalse(MagicAxesDoorAction.isHutDoor(new WorldPoint(3038, 3956, 0)));
        assertFalse(MagicAxesDoorAction.isHutDoor(new WorldPoint(3191, 3960, 1)));
        assertNotNull(MagicAxesDoorAction.accessProblem(false, 99));
        assertNotNull(MagicAxesDoorAction.accessProblem(true, 22));
        assertNull(MagicAxesDoorAction.accessProblem(true, 23));
    }
}
