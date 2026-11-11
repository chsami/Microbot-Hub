package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

final class SceneInteractionTiles {
   private static final int[] DELTA_X;
   private static final int[] DELTA_Y;
   private static final int[] DIRECTION_MASKS;

   // Exact historical XOR key for non-ASCII-compatible default charsets.
   private static byte[] legacyXorKey = Base64.getDecoder().decode(
      "ZFcdbhhJAXRORUl5eRd6Z2pjSVZfUhAEGElZXF8wSBswekAmb1F3T38wcRc0ESsJCFIeSX4zPz4/Fn8zansvcW1CLUA="
   );

   static List<WorldPoint> tilesAround(WorldPoint center, int radius) {
      ArrayList<WorldPoint> tiles = new ArrayList<>();
      tiles.add(center);

      for (int deltaX = -radius; deltaX <= radius; deltaX++) {
         for (int deltaY = -radius; deltaY <= radius; deltaY++) {
            if (deltaX != 0 || deltaY != 0) {
               tiles.add(center.dx(deltaX).dy(deltaY));
            }
         }
      }

      return tiles;
   }

   static List<WorldPoint> findApproachTiles(Client client, TileObject target) {
      WorldView worldView = client.getTopLevelWorldView();
      WorldPoint location = target.getWorldLocation();
      if (target.getWorldView() != worldView || location == null) {
         return Collections.emptyList();
      }

      if (target instanceof WallObject) {
         return findApproachTiles(client, location, target);
      }

      Point sceneMin = target instanceof GameObject
         ? ((GameObject)target).getSceneMinLocation()
         : new Point(location.getX() - worldView.getBaseX(), location.getY() - worldView.getBaseY());
      Point sceneMax = target instanceof GameObject ? ((GameObject)target).getSceneMaxLocation() : sceneMin;
      if (sceneMin != null
         && sceneMax != null
         && sceneMin.getX() >= 0
         && sceneMin.getY() >= 0
         && sceneMax.getX() >= sceneMin.getX()
         && sceneMax.getY() >= sceneMin.getY()
         && sceneMax.getX() < worldView.getSizeX()
         && sceneMax.getY() < worldView.getSizeY()) {
         LinkedHashSet<WorldPoint> approaches = new LinkedHashSet<>();

         for (int x = sceneMin.getX(); x <= sceneMax.getX(); x++) {
            for (int y = sceneMin.getY(); y <= sceneMax.getY(); y++) {
               if (x == sceneMin.getX() || x == sceneMax.getX() || y == sceneMin.getY() || y == sceneMax.getY()) {
                  approaches.addAll(findApproachTiles(client, new WorldPoint(worldView.getBaseX() + x, worldView.getBaseY() + y, location.getPlane()), target));
               }
            }
         }

         return new ArrayList<>(approaches);
      } else {
         return Collections.emptyList();
      }
   }

   static List<WorldPoint> findApproachTiles(Client client, WorldPoint targetPoint) {
      return findApproachTiles(client, targetPoint, null);
   }

   private static List<WorldPoint> findApproachTiles(Client client, WorldPoint targetPoint, TileObject targetObject) {
      WorldView worldView = client.getTopLevelWorldView();
      CollisionData[] collisionMaps = worldView.getCollisionMaps();
      byte[][][] tileSettings = worldView.getTileSettings();
      Tile[][][] sceneTiles = worldView.getScene() == null ? null : worldView.getScene().getTiles();
      int plane = targetPoint.getPlane();
      if (collisionMaps != null && tileSettings != null && sceneTiles != null && plane >= 0 && plane < collisionMaps.length && collisionMaps[plane] != null && plane < tileSettings.length && plane < sceneTiles.length) {
         int targetX = targetPoint.getX() - worldView.getBaseX();
         int targetY = targetPoint.getY() - worldView.getBaseY();
         int[][] collisionFlags = collisionMaps[plane].getFlags();
         if (isInsideGrid(collisionFlags, targetX, targetY) && targetX < tileSettings[plane].length && targetY < tileSettings[plane][targetX].length) {
            LinkedHashSet<Integer> wallEdges = new LinkedHashSet<>();
            boolean wallTarget = targetObject instanceof WallObject;
            if (wallTarget) {
               Tile targetTile = getTileOrNull(sceneTiles[plane], targetX, targetY);
               if (targetTile == null || targetTile.getWallObject() != targetObject) {
                  return Collections.emptyList();
               }

               WallObject wall = (WallObject)targetObject;
               LocalCollisionGrid.forEachDirectionalEdge(collisionFlags.length, collisionFlags[0].length, targetX, targetY, wall.getOrientationA(), wallEdges::add);
               LocalCollisionGrid.forEachDirectionalEdge(collisionFlags.length, collisionFlags[0].length, targetX, targetY, wall.getOrientationB(), wallEdges::add);
               if (wallEdges.isEmpty()) {
                  return Collections.emptyList();
               }
            }

            ArrayList<WorldPoint> approachTiles = new ArrayList<>();
            if (isTileWalkable(collisionFlags[targetX][targetY]) && (!wallTarget || !isApproachBlockedByOtherObject(client, getTileOrNull(sceneTiles[plane], targetX, targetY), targetObject))) {
               approachTiles.add(targetPoint);
            }

            for (int direction = 0; direction < DELTA_X.length; direction++) {
               int neighborX = targetX + DELTA_X[direction];
               int neighborY = targetY + DELTA_Y[direction];
               if (isInsideGrid(collisionFlags, neighborX, neighborY)
                  && neighborX < tileSettings[plane].length
                  && neighborY < tileSettings[plane][neighborX].length
                  && (
                     wallTarget
                        ? isTileWalkable(collisionFlags[neighborX][neighborY])
                           && (
                              wallEdges.contains(LocalCollisionGrid.cardinalEdgeIndex(collisionFlags.length, collisionFlags[0].length, targetX, targetY, neighborX, neighborY))
                                 || (collisionFlags[targetX][targetY] & DIRECTION_MASKS[direction]) == 0 && (collisionFlags[neighborX][neighborY] & DIRECTION_MASKS[direction ^ 1]) == 0
                           )
                        : canApproachAcrossEdge(collisionFlags[targetX][targetY], collisionFlags[neighborX][neighborY], tileSettings[plane][targetX][targetY], tileSettings[plane][neighborX][neighborY], direction)
                  )
                  && !isApproachBlockedByOtherObject(client, getTileOrNull(sceneTiles[plane], targetX, targetY), targetObject)
                  && !isApproachBlockedByOtherObject(client, getTileOrNull(sceneTiles[plane], neighborX, neighborY), targetObject)) {
                  approachTiles.add(targetPoint.dx(DELTA_X[direction]).dy(DELTA_Y[direction]));
               }
            }

            if (wallTarget) {
               return approachTiles;
            }

            for (int horizontalDirection = 0; horizontalDirection < 2; horizontalDirection++) {
               for (int verticalDirection = 2; verticalDirection < 4; verticalDirection++) {
                  int diagonalX = targetX + DELTA_X[horizontalDirection];
                  int diagonalY = targetY + DELTA_Y[verticalDirection];
                  int sourceDiagonalMask = horizontalDirection == 0 ? (verticalDirection == 2 ? 64 : 1) : (verticalDirection == 2 ? 16 : 4);
                  int destinationDiagonalMask = horizontalDirection == 0 ? (verticalDirection == 2 ? 4 : 16) : (verticalDirection == 2 ? 1 : 64);
                  if (approachTiles.contains(targetPoint.dx(DELTA_X[horizontalDirection]))
                     && approachTiles.contains(targetPoint.dy(DELTA_Y[verticalDirection]))
                     && isInsideGrid(collisionFlags, diagonalX, diagonalY)
                     && diagonalX < tileSettings[plane].length
                     && diagonalY < tileSettings[plane][diagonalX].length
                     && (collisionFlags[targetX][targetY] & sourceDiagonalMask) == 0
                     && (collisionFlags[diagonalX][diagonalY] & destinationDiagonalMask) == 0
                     && canApproachAcrossEdge(collisionFlags[diagonalX][targetY], collisionFlags[diagonalX][diagonalY], tileSettings[plane][diagonalX][targetY], tileSettings[plane][diagonalX][diagonalY], verticalDirection)
                     && canApproachAcrossEdge(collisionFlags[targetX][diagonalY], collisionFlags[diagonalX][diagonalY], tileSettings[plane][targetX][diagonalY], tileSettings[plane][diagonalX][diagonalY], horizontalDirection)
                     && !isApproachBlockedByOtherObject(client, getTileOrNull(sceneTiles[plane], diagonalX, diagonalY), targetObject)) {
                     approachTiles.add(targetPoint.dx(DELTA_X[horizontalDirection]).dy(DELTA_Y[verticalDirection]));
                  }
               }
            }

            return approachTiles;
         } else {
            return Collections.emptyList();
         }
      } else {
         return Collections.emptyList();
      }
   }

   static boolean canApproachAcrossEdge(int fromFlags, int toFlags, int fromSettings, int toSettings, int direction) {
      return isTileWalkable(toFlags)
         && (fromFlags & DIRECTION_MASKS[direction]) == 0
         && (toFlags & DIRECTION_MASKS[direction ^ 1]) == 0
         && (fromSettings & 4) == (toSettings & 4);
   }

   private static boolean isTileWalkable(int flags) {
      return (flags & 19136768) == 0;
   }

   private static boolean isInsideGrid(int[][] flags, int x, int y) {
      return flags != null && x >= 0 && y >= 0 && x < flags.length && y < flags[x].length;
   }

   private static Tile getTileOrNull(Tile[][] tiles, int x, int y) {
      // Negative coordinates short-circuit before reading the grid; null rows retain their original failure.
      return x >= 0 && y >= 0 && x < tiles.length && y < tiles[x].length ? tiles[x][y] : null;
   }

   private static boolean isApproachBlockedByOtherObject(Client client, Tile tile, TileObject target) {
      if (tile == null) {
         return true;
      }
      // Keep the two accessor calls: a changing wall snapshot is observed at the original points.
      if (tile.getWallObject() != null && tile.getWallObject() != target) {
         return true;
      }

      for (GameObject object : tile.getGameObjects()) {
         if (object == null || object == target) {
            continue;
         }
         ObjectComposition definition = client.getObjectDefinition(object.getId());
         if (definition != null && definition.getImpostorIds() != null) {
            definition = definition.getImpostor();
            if (definition == null) {
               continue;
            }
         }
         if (definition == null || definition.getName() == null) {
            return true;
         }

         String name = definition.getName().toLowerCase(Locale.ROOT);
         if (name.contains(resolveObstacleWord("door", "q5stOCEfM3h7EXgISXNDZVhvfDFuVCBuIUU=", 1303912850, -1664754786))
            || name.contains(resolveObstacleWord("gate", "rJkpMChNOXZ4H3g9SQxJHVkPfwprDCBUIEc=", 614003836, 422449653))) {
            return true;
         }
      }
      return false;
   }

   static {
      DELTA_X = new int[]{-1, 1, 0, 0};
      DELTA_Y = new int[]{0, 0, -1, 1};
      DIRECTION_MASKS = new int[]{128, 8, 32, 2};
   }

   private static String resolveObstacleWord(String literal, String encodedBytes, int outerSeed, int textSeed) {
      String decimalSeed = Integer.toString(outerSeed);
      if (Arrays.equals(decimalSeed.getBytes(), decimalSeed.getBytes(StandardCharsets.US_ASCII))) {
         return literal;
      }
      return decodeLegacyInner(decodeLegacyOuter(Base64.getDecoder().decode(encodedBytes), outerSeed), textSeed);
   }

   /** Historical in-place XOR layer, including default seed charset and two-write mutation order. */
   public static String decodeLegacyOuter(byte[] payload, int seed) {
      byte[] numericKey = Integer.toString(seed).getBytes();
      for (int index = 0; index < payload.length; index++) {
         payload[index] = (byte) (payload[index] ^ numericKey[index % numericKey.length]);
         payload[index] = (byte) (payload[index] ^ legacyXorKey[index % legacyXorKey.length]);
      }
      return new String(payload, StandardCharsets.UTF_16);
   }

   /** Original Base64/LCG layer, including signed-int overflow, odd-byte truncation and interning. */
   private static String decodeLegacyInner(String encoded, int seed) {
      byte[] payload = Base64.getDecoder().decode(encoded);
      char[] decoded = new char[payload.length >>> 1];
      for (int index = 0; index < decoded.length; index++) {
         seed = seed * 1664525 + 1013904223;
         decoded[index] = (char) ((((payload[index << 1] & 255) << 8)
            | (payload[(index << 1) + 1] & 255)) ^ (seed >>> 16));
      }
      return new String(decoded).intern();
   }
}
