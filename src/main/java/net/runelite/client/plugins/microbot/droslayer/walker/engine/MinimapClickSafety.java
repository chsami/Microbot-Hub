package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.util.BitSet;

/**
 * A minimap click is only accurate to about one tile. When the route hugs a fence or wall, the
 * click can land on the far side and the game walks into the wrong enclosure (observed west of
 * the Ectofuntus, where it entered the fenced field instead of passing along its north side).
 * Route tiles touching a fence are therefore clicked one step further away, on the route's side.
 */
final class MinimapClickSafety {
   private static final int BLOCK_MOVEMENT_FULL = 2359552;
   private static final int[] DELTA_X = {0, 0, -1, 1};
   private static final int[] DELTA_Y = {1, -1, 0, 0};

   private MinimapClickSafety() {
   }

   /** Scene tile to click on the minimap for route tile (x, y); the route tile itself when it is already safe. */
   static int[] safeTarget(int[][] flags, BitSet overrides, int x, int y) {
      if (!inBounds(flags, x, y) || !fenceAdjacent(flags, overrides, x, y)) {
         return new int[]{x, y};
      }

      for (int i = 0; i < DELTA_X.length; i++) {
         int nx = x + DELTA_X[i];
         int ny = y + DELTA_Y[i];
         if (inBounds(flags, nx, ny) && standable(flags, overrides, nx, ny)
            && LocalCollisionGrid.canStep(flags, overrides, x, y, nx, ny)
            && !fenceAdjacent(flags, overrides, nx, ny)) {
            return new int[]{nx, ny};
         }
      }

      return new int[]{x, y};
   }

   /** A standable neighbour that cannot be stepped to directly lies across a fence or wall. */
   static boolean fenceAdjacent(int[][] flags, BitSet overrides, int x, int y) {
      for (int i = 0; i < DELTA_X.length; i++) {
         int nx = x + DELTA_X[i];
         int ny = y + DELTA_Y[i];
         if (inBounds(flags, nx, ny) && standable(flags, overrides, nx, ny)
            && !LocalCollisionGrid.canStep(flags, overrides, x, y, nx, ny)) {
            return true;
         }
      }

      return false;
   }

   private static boolean standable(int[][] flags, BitSet overrides, int x, int y) {
      return (flags[x][y] & BLOCK_MOVEMENT_FULL) == 0
         || overrides.get(LocalCollisionGrid.tileOverrideIndex(flags.length, flags[0].length, x, y));
   }

   private static boolean inBounds(int[][] flags, int x, int y) {
      return flags != null && flags.length > 0 && flags[0] != null
         && x >= 0 && x < flags.length && y >= 0 && y < flags[0].length;
   }
}
