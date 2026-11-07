package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Verifies the native Collect-all inventory operation on its dynamic child. */
final class FinishCollect {
    private static final String COLLECT_TO_INVENTORY = "Collect to inventory";

    static Rectangle bounds(Widget container, int canvasWidth, int canvasHeight) {
        return operation(container, canvasWidth, canvasHeight) < 0 ? null : boundsOf(container);
    }

    /**
     * Returns the 1-based menu operation of "Collect to inventory" or -1. The operation moves between
     * slots depending on the player's collect preferences, so it must not be assumed to be first.
     */
    static int operation(Widget container, int canvasWidth, int canvasHeight) {
        Widget button = verifiedButton(container);
        if (button == null) return -1;
        String[] actions = button.getActions();
        if (actions == null) return -1;
        Rectangle bounds = button.getBounds();
        if (bounds == null || bounds.width <= 1 || bounds.height <= 1 || bounds.x < 0 || bounds.y < 0
            || (long) bounds.x + bounds.width > canvasWidth
            || (long) bounds.y + bounds.height > canvasHeight) return -1;
        for (int index = 0; index < actions.length; index++) {
            if (actions[index] != null && COLLECT_TO_INVENTORY.equalsIgnoreCase(actions[index].trim())) {
                return index + 1;
            }
        }
        return -1;
    }

    /** The verified Collect-all button, or null. */
    static Widget button(Widget container) {
        return verifiedButton(container);
    }

    private static Rectangle boundsOf(Widget container) {
        Widget button = verifiedButton(container);
        Rectangle bounds = button == null ? null : button.getBounds();
        return bounds == null ? null : new Rectangle(bounds);
    }

    private static Widget verifiedButton(Widget container) {
        if (container == null || container.getId() != InterfaceID.GeOffers.COLLECTALL
            || container.isHidden()) return null;
        Widget button = container.getChild(0);
        if (button == null || button.getId() != container.getId() || button.getIndex() != 0
            || button.isHidden()) return null;
        return button;
    }
}
