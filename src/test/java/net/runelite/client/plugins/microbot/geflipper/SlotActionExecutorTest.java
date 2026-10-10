package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.FlipperConfig.SlotAction.COPILOT_LEFT_CLICK;
import static net.runelite.client.plugins.microbot.geflipper.FlipperConfig.SlotAction.MENU_OPTION;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Action.ABORT;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Action.MODIFY;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Result.ACTED;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Result.MENU_NOT_READY;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Result.SLOT_UNAVAILABLE;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Result.SWAP_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for executing the swapped slot action without opening View offer. */
public class SlotActionExecutorTest {
    private static final int SLOT_ID = (465 << 16) | 9;

    @Test
    public void modifyLeftClickWaitsForTheSwapAndClicksTheHoveredPoint() {
        FakeUi ui = new FakeUi();

        assertEquals(ACTED, SlotActionExecutor.execute(COPILOT_LEFT_CLICK, MODIFY, SLOT_ID, ui));

        assertEquals(Arrays.asList("hover", "await", "click"), ui.operations);
        assertSame(ui.point, ui.hoveredPoint);
        assertSame(ui.hoveredPoint, ui.clickedPoint);
        assertEquals(MODIFY, ui.lastAction);
        assertEquals(SLOT_ID, ui.lastSlotId);
        assertEquals(1, ui.clicks);
        assertEquals(0, ui.invocations);
    }

    @Test
    public void abortLeftClickUsesTheSameVerifiedSwapPath() {
        FakeUi ui = new FakeUi();

        assertEquals(ACTED, SlotActionExecutor.execute(COPILOT_LEFT_CLICK, ABORT, SLOT_ID, ui));

        assertEquals(Arrays.asList("hover", "await", "click"), ui.operations);
        assertEquals(ABORT, ui.lastAction);
        assertEquals(1, ui.clicks);
        assertEquals(0, ui.invocations);
    }

    @Test
    public void disabledSwapCannotFallBackToOpeningTheOffer() {
        for (SlotActionExecutor.Action action : SlotActionExecutor.Action.values()) {
            FakeUi ui = new FakeUi();
            ui.swapEnabled = false;

            assertEquals(SWAP_DISABLED,
                SlotActionExecutor.execute(COPILOT_LEFT_CLICK, action, SLOT_ID, ui));

            assertTrue(ui.operations.isEmpty());
            assertEquals(0, ui.clicks);
            assertEquals(0, ui.invocations);
        }
    }

    @Test
    public void menuOptionDoesNotDependOnCopilotSwapForEitherAction() {
        for (boolean swapEnabled : new boolean[]{false, true}) {
            for (SlotActionExecutor.Action action : SlotActionExecutor.Action.values()) {
                FakeUi ui = new FakeUi();
                ui.swapEnabled = swapEnabled;

                assertEquals(ACTED, SlotActionExecutor.execute(MENU_OPTION, action, SLOT_ID, ui));

                assertEquals(Arrays.asList("invoke"), ui.operations);
                assertEquals(action, ui.lastAction);
                assertEquals(SLOT_ID, ui.lastSlotId);
                assertEquals(0, ui.clicks);
                assertEquals(1, ui.invocations);
            }
        }
    }

    @Test
    public void missingSlotNeverClicksADefaultCanvasRectangle() {
        for (FlipperConfig.SlotAction mode : new FlipperConfig.SlotAction[]{COPILOT_LEFT_CLICK, MENU_OPTION}) {
            FakeUi ui = new FakeUi();
            ui.point = null;

            assertEquals(SLOT_UNAVAILABLE, SlotActionExecutor.execute(mode, MODIFY, SLOT_ID, ui));

            assertTrue(ui.operations.isEmpty());
            assertEquals(0, ui.clicks);
            assertEquals(0, ui.invocations);
        }
    }

    @Test
    public void unreadyMenuDoesNotClickOrInvokeAFallbackAction() {
        FakeUi ui = new FakeUi();
        ui.menuReady = false;

        assertEquals(MENU_NOT_READY,
            SlotActionExecutor.execute(COPILOT_LEFT_CLICK, MODIFY, SLOT_ID, ui));

        assertEquals(Arrays.asList("hover", "await"), ui.operations);
        assertEquals(0, ui.clicks);
        assertEquals(0, ui.invocations);
    }

    @Test
    public void cancelledHoverStopsBothSlotActionsBeforeWaitingOrClicking() {
        for (SlotActionExecutor.Action action : SlotActionExecutor.Action.values()) {
            FakeUi ui = new FakeUi();
            ui.hoverReady = false;

            assertEquals(MENU_NOT_READY, SlotActionExecutor.execute(COPILOT_LEFT_CLICK, action, SLOT_ID, ui));

            assertEquals(Arrays.asList("hover"), ui.operations);
            assertEquals(0, ui.clicks);
            assertEquals(0, ui.invocations);
        }
    }

    @Test
    public void changedMenuAtFinalValidationDoesNotReportSuccessOrInvokeFallback() {
        FakeUi ui = new FakeUi();
        ui.finalValidation = false;

        assertEquals(MENU_NOT_READY,
            SlotActionExecutor.execute(COPILOT_LEFT_CLICK, MODIFY, SLOT_ID, ui));

        assertEquals(Arrays.asList("hover", "await", "rejected click"), ui.operations);
        assertEquals(0, ui.clicks);
        assertEquals(0, ui.invocations);
    }

    @Test
    public void changedSuggestionPreventsTheMenuInvocation() {
        FakeUi ui = new FakeUi();
        ui.finalValidation = false;
        assertEquals(MENU_NOT_READY, SlotActionExecutor.execute(MENU_OPTION, MODIFY, SLOT_ID, ui));
        assertEquals(0, ui.clicks);
        assertEquals(0, ui.invocations);
    }

    @Test
    public void acceptsTheExactTopModifyAndAbortEntries() {
        assertTrue(matches(MODIFY, entry("Modify offer", 3, MenuAction.CC_OP, 2, SLOT_ID, false)));
        assertTrue(matches(ABORT, entry("Abort offer", 2, MenuAction.CC_OP, 2, SLOT_ID, false)));
    }

    @Test
    public void matchingActionBelowViewOfferIsNotTheDefaultAction() {
        assertFalse(SlotActionExecutor.matchesDefaultAction(new MenuEntry[]{
            entry("Modify offer", 3, MenuAction.CC_OP, 2, SLOT_ID, false),
            entry("View offer", 1, MenuAction.CC_OP, 2, SLOT_ID, false)
        }, SLOT_ID, MODIFY));
    }

    @Test
    public void modifyDoesNotAcceptAbortOrSimilarOptionText() {
        assertFalse(matches(MODIFY, entry("Abort offer", 2, MenuAction.CC_OP, 2, SLOT_ID, false)));
        assertFalse(matches(MODIFY, entry("Modify offer later", 3, MenuAction.CC_OP, 2, SLOT_ID, false)));
    }

    @Test
    public void refusesADifferentSlot() {
        assertFalse(matches(MODIFY, entry("Modify offer", 3, MenuAction.CC_OP, 2, SLOT_ID + 1, false)));
    }

    @Test
    public void refusesADifferentWidgetChild() {
        assertFalse(matches(MODIFY, entry("Modify offer", 3, MenuAction.CC_OP, 1, SLOT_ID, false)));
    }

    @Test
    public void refusesTheWrongWidgetOperationIdentifier() {
        assertFalse(matches(MODIFY, entry("Modify offer", 1, MenuAction.CC_OP, 2, SLOT_ID, false)));
        assertFalse(matches(ABORT, entry("Abort offer", 3, MenuAction.CC_OP, 2, SLOT_ID, false)));
    }

    @Test
    public void refusesNonWidgetActionsEvenWhenTheLabelMatches() {
        assertFalse(matches(MODIFY, entry("Modify offer", 3, MenuAction.RUNELITE, 2, SLOT_ID, false)));
    }

    @Test
    public void refusesAnActionThatIsStillDeprioritized() {
        assertFalse(matches(MODIFY, entry("Modify offer", 3, MenuAction.CC_OP, 2, SLOT_ID, true)));
    }

    @Test
    public void absentMenuOrTopEntryIsNotReady() {
        assertFalse(SlotActionExecutor.matchesDefaultAction(null, SLOT_ID, MODIFY));
        assertFalse(SlotActionExecutor.matchesDefaultAction(new MenuEntry[0], SLOT_ID, MODIFY));
        assertFalse(SlotActionExecutor.matchesDefaultAction(new MenuEntry[]{null}, SLOT_ID, MODIFY));
    }

    private static boolean matches(SlotActionExecutor.Action action, MenuEntry entry) {
        return SlotActionExecutor.matchesDefaultAction(new MenuEntry[]{entry}, SLOT_ID, action);
    }

    private static MenuEntry entry(String option, int identifier, MenuAction type,
                                   int child, int slotId, boolean deprioritized) {
        return (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(),
            new Class<?>[]{MenuEntry.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getOption": return option;
                    case "getIdentifier": return identifier;
                    case "getType": return type;
                    case "getParam0": return child;
                    case "getParam1": return slotId;
                    case "isDeprioritized": return deprioritized;
                    case "toString": return option + "@" + slotId;
                    default: throw new AssertionError("Unexpected MenuEntry call: " + method.getName());
                }
            });
    }

    private static final class FakeUi implements SlotActionExecutor.Ui {
        boolean hoverReady = true;
        final List<String> operations = new ArrayList<>();
        boolean swapEnabled = true;
        boolean menuReady = true;
        boolean finalValidation = true;
        Point point = new Point(240, 175);
        Point hoveredPoint;
        Point clickedPoint;
        SlotActionExecutor.Action lastAction;
        int lastSlotId;
        int clicks;
        int invocations;

        @Override
        public boolean slotSwapEnabled() {
            return swapEnabled;
        }

        @Override
        public Point actionPoint(int slotId, SlotActionExecutor.Action action) {
            lastSlotId = slotId;
            lastAction = action;
            return point;
        }

        @Override
        public boolean hover(Point point) {
            assertSame(this.point, point);
            hoveredPoint = point;
            operations.add("hover");
            return hoverReady;
        }

        @Override
        public boolean awaitDefaultAction(int slotId, SlotActionExecutor.Action action, Point point) {
            assertSame(hoveredPoint, point);
            assertEquals(SLOT_ID, slotId);
            assertEquals(lastAction, action);
            operations.add("await");
            return menuReady;
        }

        @Override
        public boolean clickDefaultAction(int slotId, SlotActionExecutor.Action action, Point point) {
            assertEquals(Arrays.asList("hover", "await"), operations);
            assertSame(hoveredPoint, point);
            assertTrue(menuReady);
            assertEquals(SLOT_ID, slotId);
            assertEquals(lastAction, action);
            if (!finalValidation) {
                operations.add("rejected click");
                return false;
            }
            clickedPoint = point;
            clicks++;
            operations.add("click");
            return true;
        }

        @Override
        public boolean invokeAction(int slotId, SlotActionExecutor.Action action, Point point) {
            assertSame(this.point, point);
            assertEquals(SLOT_ID, slotId);
            assertEquals(lastAction, action);
            if (!finalValidation) return false;
            invocations++;
            operations.add("invoke");
            return true;
        }
    }
}
