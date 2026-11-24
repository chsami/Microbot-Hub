package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkerEngineMinimapClickTest {
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

   @Test
   void eachStalledTargetHalvesTheFurthestClickIndex() {
      assertEquals(52, WalkerEngine.stalledClickIndexLimit(52, 0));
      assertEquals(26, WalkerEngine.stalledClickIndexLimit(52, 1));
      assertEquals(13, WalkerEngine.stalledClickIndexLimit(52, 2));
      assertEquals(3, WalkerEngine.stalledClickIndexLimit(52, 4));
      assertEquals(0, WalkerEngine.stalledClickIndexLimit(1, 1));
      assertEquals(0, WalkerEngine.stalledClickIndexLimit(52, 40));
   }

   @Test
   void walkingGivesUpAfterFiveStalledTargetsFromTheSameTile() {
      assertFalse(WalkerEngine.hasExhaustedStalledWalkTargets(4));
      assertTrue(WalkerEngine.hasExhaustedStalledWalkTargets(5));
   }

   @Test
   void stalledWalkClicksExplainTheWorldMapInsteadOfTheMinimap() {
      String message = TravelFailureMessages.describeTravelFailure("walk-clicks-not-moving", null, null);
      assertTrue(message.startsWith("Your character did not move after repeated walk clicks."));
      assertTrue(message.contains("world map"));
      assertTrue(message.contains("no longer frozen or stunned"));
      assertTrue(TravelFailureMessages.describeTravelFailure("no-clickable-walk-target", null, null).contains("minimap is visible"));
   }

   @Test
   void recoveringFromAStallRestoresTheEarlierStatus() {
      assertEquals("Walking to Varrock", WalkerEngine.statusAfterWalkStall(WalkerEngine.WALK_STALL_STATUS, "Walking to Varrock"));
      assertEquals("Waiting for the bank to open.", WalkerEngine.statusAfterWalkStall("Waiting for the bank to open.", "Walking to Varrock"));
      assertTrue(WalkerEngine.WALK_STALL_STATUS.startsWith("Efficient Walker: Character is not moving"));
   }
}
