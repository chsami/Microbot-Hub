package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntConsumer;

final class LocalCollisionGrid {
   private static final int[] DELTA_X = {-1, 1, 0, 0, -1, 1, -1, 1};
   private static final int[] DELTA_Y = {0, 0, -1, 1, -1, -1, 1, 1};

   static int[] findPath(int[][] flags, BitSet overrides, int startX, int startY, int targetX, int targetY) {
      if (isWithinGridBounds(flags, startX, startY) && isWithinGridBounds(flags, targetX, targetY)) {
         int width = flags.length;
         int height = flags[0].length;
         int startIndex = tileIndex(startX, startY, width);
         int targetIndex = tileIndex(targetX, targetY, width);
         int[] predecessors = new int[width * height];
         int[] queue = new int[width * height];
         Arrays.fill(predecessors, -2);
         predecessors[startIndex] = -1;
         int queueHead = 0;
         int queueTail = 0;
         queue[queueTail++] = startIndex;

         while (queueHead < queueTail && predecessors[targetIndex] == -2) {
            int currentIndex = queue[queueHead++];
            int currentX = unpackX(currentIndex, width);
            int currentY = unpackY(currentIndex, width);

            for (int direction = 0; direction < DELTA_X.length; direction++) {
               int neighborX = currentX + DELTA_X[direction];
               int neighborY = currentY + DELTA_Y[direction];
               if (isWithinGridBounds(flags, neighborX, neighborY)) {
                  int neighborIndex = tileIndex(neighborX, neighborY, width);
                  if (predecessors[neighborIndex] == -2 && canStep(flags, overrides, currentX, currentY, neighborX, neighborY)) {
                     predecessors[neighborIndex] = currentIndex;
                     queue[queueTail++] = neighborIndex;
                  }
               }
            }
         }

         if (predecessors[targetIndex] == -2) {
            return new int[0];
         }

         int pathLength = 1;

         for (int ancestorIndex = targetIndex; ancestorIndex != startIndex; ancestorIndex = predecessors[ancestorIndex]) {
            pathLength++;
         }

         int[] path = new int[pathLength];
         int pathIndex = targetIndex;

         for (int step = pathLength - 1; step >= 0; step--) {
            path[step] = pathIndex;
            pathIndex = predecessors[pathIndex];
         }

         return path;
      } else {
         return new int[0];
      }
   }

   static boolean canStep(int[][] flags, BitSet overrides, int fromX, int fromY, int toX, int toY) {
      int deltaX = toX - fromX;
      int deltaY = toY - fromY;
      if (Math.abs(deltaX) <= 1 && Math.abs(deltaY) <= 1 && (deltaX != 0 || deltaY != 0)) {
         if (deltaX != 0 && deltaY != 0) {
            int diagonalCollisionMask;
            if (deltaX < 0) {
               diagonalCollisionMask = deltaY < 0 ? 19136782 : 19136824;
            } else {
               diagonalCollisionMask = deltaY < 0 ? 19136899 : 19136992;
            }

            int horizontalEdge = cardinalEdgeIndex(flags.length, flags[0].length, fromX, fromY, fromX + deltaX, fromY);
            int verticalEdge = cardinalEdgeIndex(flags.length, flags[0].length, fromX, fromY, fromX, fromY + deltaY);
            return !overrides.get(horizontalEdge) && !overrides.get(verticalEdge)
               ? (flags[toX][toY] & diagonalCollisionMask) == 0 && canTraverseCardinalEdge(flags, overrides, fromX, fromY, fromX + deltaX, fromY) && canTraverseCardinalEdge(flags, overrides, fromX, fromY, fromX, fromY + deltaY)
               : false;
         } else {
            return canTraverseCardinalEdge(flags, overrides, fromX, fromY, toX, toY);
         }
      } else {
         return false;
      }
   }

   static int cardinalEdgeIndex(int width, int height, int fromX, int fromY, int toX, int toY) {
      int deltaX = toX - fromX;
      int deltaY = toY - fromY;
      if (Math.abs(deltaX) + Math.abs(deltaY) != 1) {
         return -1;
      } else {
         int lowerX = deltaX < 0 ? toX : fromX;
         int lowerY = deltaY < 0 ? toY : fromY;
         if (lowerX >= 0 && lowerY >= 0 && lowerX < width && lowerY < height) {
            return (deltaX == 0 || lowerX < width - 1) && (deltaY == 0 || lowerY < height - 1) ? tileIndex(lowerX, lowerY, width) << 1 | (deltaY != 0 ? 1 : 0) : -1;
         } else {
            return -1;
         }
      }
   }

   static int tileOverrideIndex(int width, int height, int x, int y) {
      return width * height * 2 + tileIndex(x, y, width);
   }

   static void forEachDirectionalEdge(int width, int height, int x, int y, int directionMask, IntConsumer consumer) {
      for (short direction = 1; direction <= 128; direction <<= 1) {
         if ((directionMask & direction) != 0) {
            switch (direction) {
               case 1:
                  emitValidCardinalEdge(width, height, x, y, x - 1, y, consumer);
                  break;
               case 2:
                  emitValidCardinalEdge(width, height, x, y, x, y + 1, consumer);
                  break;
               case 4:
                  emitValidCardinalEdge(width, height, x, y, x + 1, y, consumer);
                  break;
               case 8:
                  emitValidCardinalEdge(width, height, x, y, x, y - 1, consumer);
                  break;
               case 16:
                  emitValidCardinalEdge(width, height, x, y, x - 1, y, consumer);
                  emitValidCardinalEdge(width, height, x, y, x, y + 1, consumer);
                  break;
               case 32:
                  emitValidCardinalEdge(width, height, x, y, x, y + 1, consumer);
                  emitValidCardinalEdge(width, height, x, y, x + 1, y, consumer);
                  break;
               case 64:
                  emitValidCardinalEdge(width, height, x, y, x + 1, y, consumer);
                  emitValidCardinalEdge(width, height, x, y, x, y - 1, consumer);
                  break;
               case 128:
                  emitValidCardinalEdge(width, height, x, y, x, y - 1, consumer);
                  emitValidCardinalEdge(width, height, x, y, x - 1, y, consumer);
            }
         }
      }
   }

   static int tileIndex(int x, int y, int width) {
      return y * width + x;
   }

   static int unpackX(int tileIndex, int width) {
      return tileIndex % width;
   }

   static int unpackY(int tileIndex, int width) {
      return tileIndex / width;
   }

   private static boolean canTraverseCardinalEdge(int[][] flags, BitSet overrides, int fromX, int fromY, int toX, int toY) {
      if (!isWithinGridBounds(flags, fromX, fromY) || !isWithinGridBounds(flags, toX, toY)) {
         return false;
      }

      int mask;
      if (toX > fromX) {
         mask = 19136896;
      } else if (toX < fromX) {
         mask = 19136776;
      } else if (toY > fromY) {
         mask = 19136800;
      } else {
         mask = 19136770;
      }

      if ((flags[toX][toY] & mask) == 0) {
         return true;
      }

      int edge = cardinalEdgeIndex(flags.length, flags[0].length, fromX, fromY, toX, toY);
      return edge >= 0 && overrides.get(edge)
         && isStandableWithOverrides(flags, overrides, fromX, fromY)
         && isStandableWithOverrides(flags, overrides, toX, toY);
   }

   private static boolean isStandableWithOverrides(int[][] flags, BitSet overrides, int x, int y) {
      return (flags[x][y] & 2359552) == 0 || overrides.get(tileOverrideIndex(flags.length, flags[0].length, x, y));
   }

   private static void emitValidCardinalEdge(int width, int height, int fromX, int fromY, int toX, int toY, IntConsumer consumer) {
      int edge = cardinalEdgeIndex(width, height, fromX, fromY, toX, toY);
      if (edge >= 0) {
         consumer.accept(edge);
      }
   }

   private static boolean isWithinGridBounds(int[][] flags, int x, int y) {
      // The original bounds policy uses the first row, including for ragged grids.
      return flags != null && flags.length > 0 && flags[0] != null
         && x >= 0 && x < flags.length && y >= 0 && y < flags[0].length;
   }
}
