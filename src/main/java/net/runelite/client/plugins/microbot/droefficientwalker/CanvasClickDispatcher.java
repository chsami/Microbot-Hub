package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * <p>This helper dispatches one guarded left-click directly to the RuneLite canvas.</p>
 */
final class CanvasClickDispatcher
{
    private CanvasClickDispatcher()
    {
        // Utility class.
    }

    static void clickCanvas(Client client, Point point)
    {
        final Canvas canvas = client.getCanvas();
        int x = point.getX();
        int y = point.getY();

        if (client.isStretchedEnabled())
        {
            final Dimension real = client.getRealDimensions();
            final Dimension stretched = client.getStretchedDimensions();

            if (real != null && stretched != null && real.width > 0 && real.height > 0)
            {
                x = (int) ((long) x * stretched.width / real.width);
                y = (int) ((long) y * stretched.height / real.height);
            }
        }

        final boolean restoreFocusable = canvas.isFocusable() && !canvas.isFocusOwner();
        if (restoreFocusable)
        {
            canvas.setFocusable(false);
        }

        BotEventGuard.begin();
        try
        {
            final long when = System.currentTimeMillis();

            if (client.getMouseCanvasPosition().getX() < 0)
            {
                canvas.dispatchEvent(new MouseEvent(
                    canvas,
                    MouseEvent.MOUSE_ENTERED,
                    when,
                    0,
                    x,
                    y,
                    0,
                    false));
            }

            canvas.dispatchEvent(new MouseEvent(
                canvas,
                MouseEvent.MOUSE_MOVED,
                when,
                0,
                x,
                y,
                0,
                false));

            canvas.dispatchEvent(new MouseEvent(
                canvas,
                MouseEvent.MOUSE_PRESSED,
                when,
                0,
                x,
                y,
                1,
                false,
                MouseEvent.BUTTON1));

            canvas.dispatchEvent(new MouseEvent(
                canvas,
                MouseEvent.MOUSE_RELEASED,
                when,
                0,
                x,
                y,
                1,
                false,
                MouseEvent.BUTTON1));

            canvas.dispatchEvent(new MouseEvent(
                canvas,
                MouseEvent.MOUSE_CLICKED,
                when,
                0,
                x,
                y,
                1,
                false,
                MouseEvent.BUTTON1));
        }
        finally
        {
            BotEventGuard.end();
            if (restoreFocusable)
            {
                canvas.setFocusable(true);
            }
        }
    }
}
