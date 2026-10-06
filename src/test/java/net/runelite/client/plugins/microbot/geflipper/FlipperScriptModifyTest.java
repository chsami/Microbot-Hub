package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Action.MODIFY;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Result.*;
import static org.junit.jupiter.api.Assertions.*;

/** Covers real script failure handling, complementing the swap-on/off executor tests. */
public class FlipperScriptModifyTest {
    @Test
    public void swapOffPausesWithAnActionableSettingName() throws Exception {
        FlipperScript script = new FlipperScript();
        assertTrue(script.handleSlotActionFailure(SWAP_DISABLED, MODIFY, "same-suggestion"));
        assertEquals("same-suggestion", blockedKey(script));
        assertTrue(script.getSlotActionStatus().contains("Enable Copilot slot swap"));
        assertTrue(script.getSlotActionStatus().contains("Slot menu action"));
    }

    @Test
    public void menuTimeoutConsumesTheTickButDoesNotPermanentlyPause() throws Exception {
        FlipperScript script = new FlipperScript();
        assertTrue(script.handleSlotActionFailure(MENU_NOT_READY, MODIFY, "same-suggestion"));
        assertNull(blockedKey(script));
        assertTrue(script.getSlotActionStatus().contains("Will retry"));
        assertFalse(script.handleSlotActionFailure(ACTED, MODIFY, "same-suggestion"));
        assertNull(blockedKey(script));
    }

    @Test
    public void unavailableActionDoesNotBlockTheSuggestionAndOffersRecovery() throws Exception {
        FlipperScript script = new FlipperScript();
        assertTrue(script.handleSlotActionFailure(SLOT_UNAVAILABLE, MODIFY, "same-suggestion"));
        assertNull(blockedKey(script));
        assertTrue(script.getSlotActionStatus().contains("refresh Copilot suggestions"));
        assertTrue(script.getSlotActionStatus().contains("handle the offer manually"));
    }

    @Test
    public void successfulModifyDoesNotEnterTheFailurePath() throws Exception {
        FlipperScript script = new FlipperScript();
        assertFalse(script.handleSlotActionFailure(ACTED, MODIFY, "same-suggestion"));
        assertNull(blockedKey(script));
        assertEquals("", script.getSlotActionStatus());
    }

    @Test
    public void modifyRequiresTheActualSupportedWidgetOperation() {
        assertTrue(SlotActionExecutor.supportsAction(new String[]{"View offer", "Abort offer", "Modify offer"}, MODIFY));
        assertFalse(SlotActionExecutor.supportsAction(new String[]{"View offer", "Abort offer"}, MODIFY));
        assertFalse(SlotActionExecutor.supportsAction(new String[]{"View offer", "Abort offer", "View offer"}, MODIFY));
        assertFalse(SlotActionExecutor.supportsAction(new String[]{"Modify offer", "Abort offer"}, MODIFY));
    }

    @Test
    public void missingWidgetActionsNeverCauseAnInvalidIndexAccess() {
        assertFalse(SlotActionExecutor.supportsAction(null, MODIFY));
        assertFalse(SlotActionExecutor.supportsAction(new String[0], MODIFY));
        assertFalse(SlotActionExecutor.supportsAction(new String[]{"View offer", "Abort offer", null}, MODIFY));
    }

    private static Object blockedKey(FlipperScript script) throws Exception {
        Field field = FlipperScript.class.getDeclaredField("blockedSlotActionKey");
        field.setAccessible(true);
        return field.get(script);
    }
}
