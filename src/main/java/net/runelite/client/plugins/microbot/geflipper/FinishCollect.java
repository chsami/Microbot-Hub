package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Verifies the native Collect-all inventory operation on its dynamic child. */
final class FinishCollect {
    static Rectangle bounds(Widget container, int canvasWidth, int canvasHeight) {
        if (container == null || container.getId() != InterfaceID.GeOffers.COLLECTALL
            || container.isHidden()) return null;
        Widget button = container.getChild(0);
        if (button == null || button.getId() != container.getId() || button.getIndex() != 0
            || button.isHidden()) return null;
        String[] actions = button.getActions();
        if (actions == null || actions.length == 0 || !"Collect to inventory".equals(actions[0])) return null;
        Rectangle bounds = button.getBounds();
        if (bounds == null || bounds.width <= 1 || bounds.height <= 1 || bounds.x < 0 || bounds.y < 0
            || (long) bounds.x + bounds.width > canvasWidth
            || (long) bounds.y + bounds.height > canvasHeight) return null;
        return new Rectangle(bounds);
    }
}
