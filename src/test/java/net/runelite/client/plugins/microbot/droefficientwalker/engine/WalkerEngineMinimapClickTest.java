package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkerEngineMinimapClickTest {
   @Test
   void minimapPointOnAnOrbIsRejected() {
      Rectangle worldMapOrb = new Rectangle(1830, 150, 30, 30);
      Rectangle compass = new Rectangle(1720, 5, 35, 35);

      assertTrue(WalkerEngine.isPointInsideAny(new Point(1845, 165), Arrays.asList(compass, worldMapOrb)));
      assertFalse(WalkerEngine.isPointInsideAny(new Point(1790, 90), Arrays.asList(compass, worldMapOrb)));
      assertFalse(WalkerEngine.isPointInsideAny(new Point(1845, 165), Collections.singletonList(null)));
      assertFalse(WalkerEngine.isPointInsideAny(null, Collections.singletonList(worldMapOrb)));
   }

   @Test
   void repeatedClicksOnTheSameTargetWithoutMovingAreBounded() {
      int repeats = WalkerEngine.nextWalkClickRepeats(0, false, false);
      assertEquals(1, repeats);
      assertFalse(WalkerEngine.hasExhaustedWalkClickRetries(repeats));

      repeats = WalkerEngine.nextWalkClickRepeats(repeats, true, true);
      assertFalse(WalkerEngine.hasExhaustedWalkClickRetries(repeats));

      repeats = WalkerEngine.nextWalkClickRepeats(repeats, true, true);
      assertTrue(WalkerEngine.hasExhaustedWalkClickRetries(repeats));
   }

   @Test
   void movingOrChangingTargetResetsTheRepeatCount() {
      assertEquals(1, WalkerEngine.nextWalkClickRepeats(5, false, true));
      assertEquals(1, WalkerEngine.nextWalkClickRepeats(5, true, false));
   }
}
