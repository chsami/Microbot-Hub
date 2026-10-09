package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;

/** Executes supported slot operations without falling back to "View offer". */
final class SlotActionExecutor {
    enum Action {
        ABORT("Abort offer", 2),
        MODIFY("Modify offer", 3);

        final String option;
        final int identifier;
        Action(String option, int identifier) {
            this.option = option;
            this.identifier = identifier;
        }
    }

    enum Result {
        /** The action was issued. The caller must still confirm the expected screen opened. */
        ACTED,
        /** Slot swap is off, so a left click would open "View offer" instead of the slot action. */
        SWAP_DISABLED,
        /** The slot's button widget could not be resolved or is off screen. */
        SLOT_UNAVAILABLE,
        /** The click point was not ready in time. */
        MENU_NOT_READY
    }

    interface Ui {
        boolean slotSwapEnabled();
        /** Returns null unless the visible slot exposes this exact widget operation. */
        Point actionPoint(int slotId, Action action);
        boolean hover(Point point);
        boolean awaitDefaultAction(int slotId, Action action, Point point);
        /** Revalidates the suggestion and default action immediately before the click. */
        boolean clickDefaultAction(int slotId, Action action, Point point);
        boolean invokeAction(int slotId, Action action, Point point);
    }

    static Result execute(FlipperConfig.SlotAction mode, Action action, int slotId, Ui ui) {
        if (mode == FlipperConfig.SlotAction.COPILOT_LEFT_CLICK && !ui.slotSwapEnabled()) {
            return Result.SWAP_DISABLED;
        }
        Point point = ui.actionPoint(slotId, action);
        if (point == null) return Result.SLOT_UNAVAILABLE;
        if (mode == FlipperConfig.SlotAction.MENU_OPTION) {
            return ui.invokeAction(slotId, action, point) ? Result.ACTED : Result.MENU_NOT_READY;
        }
        if (!ui.hover(point)) return Result.MENU_NOT_READY;
        if (!ui.awaitDefaultAction(slotId, action, point)) return Result.MENU_NOT_READY;
        return ui.clickDefaultAction(slotId, action, point) ? Result.ACTED : Result.MENU_NOT_READY;
    }

    static boolean supportsAction(String[] actions, Action action) {
        return actions != null && action.identifier > 0 && actions.length >= action.identifier
            && action.option.equals(actions[action.identifier - 1]);
    }

    /** The last menu entry is the actual left-click operation after Copilot's swap. */
    static boolean matchesDefaultAction(MenuEntry[] entries, int slotId, Action action) {
        if (entries == null || entries.length == 0) return false;
        MenuEntry top = entries[entries.length - 1];
        return top != null
            && top.getType() == MenuAction.CC_OP
            && !top.isDeprioritized()
            && top.getParam1() == slotId && top.getParam0() == 2
            && top.getIdentifier() == action.identifier && action.option.equals(top.getOption());
    }
}
