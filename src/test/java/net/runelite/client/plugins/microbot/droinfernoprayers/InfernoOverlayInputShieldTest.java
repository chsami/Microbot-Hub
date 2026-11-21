package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.awt.Canvas;
import java.awt.event.MouseEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InfernoOverlayInputShieldTest
{
    private static MouseEvent event(Canvas canvas, int id) {
        return new MouseEvent(canvas, id, 0L, MouseEvent.BUTTON1_DOWN_MASK, 10, 10, 1, false, MouseEvent.BUTTON1);
    }

    @Test public void anEventAlreadyInFlightAfterCloseIsDroppedInsteadOfThrowing() {
        Canvas canvas = new Canvas();
        InfernoOverlayInputShield shield = new InfernoOverlayInputShield(p -> false);
        shield.close();
        assertDoesNotThrow(() -> shield.mouseDragged(event(canvas, MouseEvent.MOUSE_DRAGGED)));
        assertDoesNotThrow(() -> shield.mouseMoved(event(canvas, MouseEvent.MOUSE_MOVED)));
        assertDoesNotThrow(() -> shield.mousePressed(event(canvas, MouseEvent.MOUSE_PRESSED)));
        assertDoesNotThrow(() -> shield.mouseReleased(event(canvas, MouseEvent.MOUSE_RELEASED)));
        assertDoesNotThrow(() -> shield.mouseEntered(event(canvas, MouseEvent.MOUSE_ENTERED)));
        assertDoesNotThrow(() -> shield.mouseExited(event(canvas, MouseEvent.MOUSE_EXITED)));
    }
}
