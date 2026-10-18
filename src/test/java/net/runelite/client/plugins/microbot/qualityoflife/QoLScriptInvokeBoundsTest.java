package net.runelite.client.plugins.microbot.qualityoflife;

import java.awt.Rectangle;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QoLScriptInvokeBoundsTest {
    @Test
    void recordedEntryWithoutWidgetFallsBackToUnitRectangle() {
        NewMenuEntry entry = new NewMenuEntry("Walk here", "", 0, MenuAction.WALK, 0, 0, false);

        assertEquals(new Rectangle(1, 1), QoLScript.invokeBounds(entry));
    }

    @Test
    void recordedEntryWithWidgetUsesWidgetBounds() {
        Rectangle bounds = new Rectangle(40, 60, 36, 32);
        Widget widget = mock(Widget.class);
        when(widget.getBounds()).thenReturn(bounds);
        NewMenuEntry entry = new NewMenuEntry("Withdraw-1", "Coins", 1, MenuAction.CC_OP, 3, 786445, false);
        entry.setWidget(widget);

        assertEquals(bounds, QoLScript.invokeBounds(entry));
    }
}
