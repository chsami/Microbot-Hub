package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

final class CanvasClickDispatcher {
   static void clickCanvas(Client client, Point point) {
      Canvas canvas = client.getCanvas();
      int canvasX = point.getX();
      int canvasY = point.getY();
      if (client.isStretchedEnabled()) {
         Dimension realDimensions = client.getRealDimensions();
         Dimension stretchedDimensions = client.getStretchedDimensions();
         if (realDimensions != null && stretchedDimensions != null && realDimensions.width > 0 && realDimensions.height > 0) {
            canvasX = (int)((long)canvasX * stretchedDimensions.width / realDimensions.width);
            canvasY = (int)((long)canvasY * stretchedDimensions.height / realDimensions.height);
         }
      }

      boolean restoreFocusable = canvas.isFocusable() && !canvas.isFocusOwner();
      if (restoreFocusable) {
         canvas.setFocusable(false);
      }

      BotEventGuard.begin();

      try {
         long eventTime = System.currentTimeMillis();
         if (client.getMouseCanvasPosition().getX() < 0) {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_ENTERED, eventTime, 0, canvasX, canvasY, 0, false));
         }

         canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, eventTime, 0, canvasX, canvasY, 0, false));
         canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, eventTime, 0, canvasX, canvasY, 1, false, MouseEvent.BUTTON1));
         canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, eventTime, 0, canvasX, canvasY, 1, false, MouseEvent.BUTTON1));
         canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, eventTime, 0, canvasX, canvasY, 1, false, MouseEvent.BUTTON1));
      } finally {
         BotEventGuard.end();
         if (restoreFocusable) {
            canvas.setFocusable(true);
         }
      }
   }
}
