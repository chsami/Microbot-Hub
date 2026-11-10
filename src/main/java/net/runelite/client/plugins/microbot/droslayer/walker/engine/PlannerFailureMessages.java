package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import net.runelite.api.coords.WorldPoint;

final class PlannerFailureMessages {
   private PlannerFailureMessages() {
   }

   static String describePlanningFailure(WalkerEngine.PlanningFailure failure, WorldPoint start, WorldPoint destination) {
      if (failure == WalkerEngine.PlanningFailure.TARGET_NOT_STANDABLE) {
         return "The selected target tile is not standable.";
      } else if (failure == WalkerEngine.PlanningFailure.SEARCH_LIMIT) {
         return "Route search reached its safety limit before finding a path.";
      } else {
         return destination == null
            ? "No supported route was found."
            : String.format("No supported route was found to %d, %d, %d.", destination.getX(), destination.getY(), destination.getPlane());
      }
   }
}
